# Protocol Runtime 0.10.0 Release Notes

Release notes for the `0.10.0` runtime release line.

## Planned Highlights

- Open the Maven reactor at `0.10.0-SNAPSHOT` after the published `0.9.0` sink
  and operations hardening release.
- Formalize runtime-app health and readiness state for standalone collector
  operation.
- Improve status output so operators can distinguish listener, source, parser,
  sink, failure, and backpressure posture.
- Add app-local `CollectorHealthSnapshot` derivation with `HEALTHY`,
  `DEGRADED`, `FAILED`, lifecycle-aligned non-running states, `READY` /
  `NOT_READY`, and explainable health reasons.
- Add examples and troubleshooting guidance for healthy, degraded, failed, and
  stopped collector states.
- Add English and Chinese status guides with a health/readiness matrix,
  trimmed status-line examples, reason catalog, and operator triage order.
- Add repository smoke coverage proving standalone TCP/IEC104 collector health
  status across healthy/ready and degraded/ready parser-failure states.
- Preserve `runtime-core` as a dependency-light contract module with no Spring,
  Netty, Kafka, MQTT, HTTP, database, Redis, object storage, or observability
  exporter dependencies.

## Scope

`0.10.0` focuses on health checks and runtime status productionization after the
published TCP, HTTP, Kafka, MQTT, and sink-hardening baselines.

## Dependency Policy

`runtime-core` must remain free of transport, broker, storage, database,
framework, health endpoint, management API, dashboard, and exporter
dependencies.

Dedicated app or adapter modules may own external dependencies only after their
boundary is explicit. `runtime-protocol-*` modules continue to depend only on
`runtime-core`, published `protocol-sdk` parser artifacts, and tests.

## Verification Target

Before release branch work, the readiness branch should pass:

- `git diff --check`
- `mvn -q verify`
- `mvn -q -Pcentral-release -Dgpg.skip=true -Dcentral.skipPublishing=true deploy`
- standalone smoke coverage for supported collector examples
- dependency boundary checks proving new dependencies do not enter
  `runtime-core`, `runtime-protocol-*`, or `protocol-sdk`

The detailed plan is tracked in [`roadmap-0.10.0.md`](roadmap-0.10.0.md).
