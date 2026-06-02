# Protocol Runtime 0.2.0 Release Notes

Release branch notes for the `0.2.0` runtime release line.

## Highlights

- Adds `runtime-app`, the first standalone collector assembly.
- Provides a JDK 21 executable shaded jar through `runtime-app`.
- Supports IEC104 over TCP runtime wiring:
  `TcpNettyServer -> RuntimePipelineRunner -> Iec104RuntimeBinding -> sinks`.
- Supports property-based configuration for TCP host/port, source id,
  backpressure mode, sink type, file sink path, and IEC104 strict ASDU parsing.
- Adds logging, file, and in-memory app-level sinks.
- Adds `examples/collector.properties`, a single-file IEC104 test-frame sender,
  and a standalone jar smoke script.
- Adds integration tests for startup, graceful shutdown, port conflicts,
  malformed frame routing, client disconnects, and backpressure.
- Keeps runtime app dependencies out of `runtime-core` and keeps
  `protocol-sdk` parser-only.

## Example

```bash
mvn -q -pl runtime-app -am package

java -jar runtime-app/target/runtime-app-0.2.0-standalone.jar \
  --config examples/collector.properties

java examples/Iec104SendSinglePoint.java 127.0.0.1 2404

tail -f target/runtime-records.ndjson
```

Run the local standalone smoke:

```bash
sh examples/smoke-standalone.sh
```

## Scope

`0.2.0` is a runnable baseline, not a full production collector platform.

Out of scope:

- Spring Boot or deployment framework integration.
- Kafka, MQTT, and HTTP ingestion.
- Database, Redis, durable queue, or object storage sinks.
- TLS, reconnect scheduling, and IEC104 command/session state policy.
- Metrics exporters and operational dashboards.

## Verification Target

Before release, the branch should pass:

- `git diff --check`
- `mvn -q verify`
- `mvn -q -Pcentral-release -Dgpg.skip=true -Dcentral.skipPublishing=true deploy`
- dependency boundary checks proving `runtime-core` remains adapter-free
- executable jar smoke verification for `runtime-app`
- `sh examples/smoke-standalone.sh`

## Release Readiness Status

The `0.2.0` readiness audit is maintained in
[`release-readiness-0.2.0.md`](release-readiness-0.2.0.md).

The release is not tagged yet. This release branch sets the Maven reactor
version to `0.2.0`, reruns the verification target above, and requires GitHub
Actions to pass before tag creation or Maven Central upload.
