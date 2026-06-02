# Protocol Runtime 0.2.0 Roadmap

`0.2.0` turns the published `0.1.0` library baseline into the first runnable
collector baseline.

## Primary Goal

Provide a minimal JDK 21 standalone IEC104 TCP collector that can be launched
from `runtime-app` without changing `protocol-sdk` or adding adapter
dependencies to `runtime-core`.

## Included Scope

- `runtime-app` Maven module.
- Shaded executable jar with `StandaloneCollectorMain`.
- Properties-based configuration for:
  - TCP host and port.
  - configured `SourceId`.
  - `ACCEPT`, `RETRY_LATER`, or `DROP` backpressure.
  - sink type.
  - optional IEC104 strict ASDU parsing.
- IEC104 over TCP pipeline assembly:
  - `TcpNettyServer`
  - `RuntimePipelineRunner`
  - `Iec104RuntimeBinding`
  - `RecordSink` and `FailureSink`
- App-level sinks:
  - JDK logging
  - newline-delimited file output
  - in-memory sink for integration tests
- Operator-facing examples:
  - `examples/collector.properties`
  - single-file IEC104 test-frame sender
  - standalone jar smoke script
- Integration coverage for:
  - startup and record routing
  - graceful shutdown
  - port conflict failure
  - malformed IEC104 parse failures
  - client disconnect lifecycle
  - backpressure preventing parsing

## Explicitly Deferred

- Spring Boot or any application framework.
- Kafka, MQTT, or HTTP ingress.
- Database, Redis, durable queue, or object storage sinks.
- TLS and certificate management.
- Reconnect scheduling.
- IEC104 command/session state policy.
- Metrics exporters and operational dashboards.

## Dependency Rules

- `runtime-core` remains free of Netty, Spring, Kafka, MQTT, HTTP, database,
  Redis, and protocol-specific bindings.
- `protocol-sdk` remains parser-only and does not depend on `protocol-runtime`.
- Netty remains isolated to `runtime-ingress-tcp-netty` and modules that
  intentionally assemble it, such as `runtime-app` and tests.

## Usage Experience Gate

Before `0.2.0` release readiness, a new user should be able to:

1. Build `runtime-app`.
2. Start the standalone collector with `examples/collector.properties`.
3. Send a known IEC104 test frame with `examples/Iec104SendSinglePoint.java`.
4. Inspect the file sink output in `target/runtime-records.ndjson`.
5. Run `sh examples/smoke-standalone.sh` as a local smoke check.

## Release Readiness Gate

Before cutting the `0.2.0` release branch, the readiness audit should confirm:

- README and Chinese README document the standalone collector run path.
- `docs/release-notes-0.2.0.md` reflects the selected release scope.
- `docs/release-readiness-0.2.0.md` records module gates, publishing policy,
  required checks, and dependency boundary evidence.
- GitHub Actions uses Node 24 compatible action versions.
- Local verification passes `git diff --check`, `mvn -q verify`, Central
  profile smoke, standalone jar smoke, and dependency boundary checks.
