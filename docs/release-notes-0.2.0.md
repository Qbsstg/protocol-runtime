# Protocol Runtime 0.2.0 Release Notes

Draft notes for the next runtime release line.

## Highlights

- Adds `runtime-app`, the first standalone collector assembly.
- Provides a JDK 21 executable shaded jar through `runtime-app`.
- Supports IEC104 over TCP runtime wiring:
  `TcpNettyServer -> RuntimePipelineRunner -> Iec104RuntimeBinding -> sinks`.
- Supports property-based configuration for TCP host/port, source id,
  backpressure mode, sink type, file sink path, and IEC104 strict ASDU parsing.
- Adds logging, file, and in-memory app-level sinks.
- Adds integration tests for startup, graceful shutdown, port conflicts,
  malformed frame routing, client disconnects, and backpressure.
- Keeps runtime app dependencies out of `runtime-core` and keeps
  `protocol-sdk` parser-only.

## Example

```bash
mvn -q -pl runtime-app -am package

java -jar runtime-app/target/runtime-app-0.2.0-SNAPSHOT-standalone.jar \
  --collector.tcp.host=0.0.0.0 \
  --collector.tcp.port=2404 \
  --collector.source.id=iec104:station-1 \
  --collector.backpressure=ACCEPT \
  --collector.sink.type=file \
  --collector.sink.file=target/runtime-records.ndjson
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
- dependency boundary checks proving `runtime-core` remains adapter-free
- executable jar smoke verification for `runtime-app`
