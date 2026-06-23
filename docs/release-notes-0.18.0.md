# Protocol Runtime 0.18.0 Release Notes

Release notes draft for the `0.18.0` downstream sink adapter SPI baseline.

`0.18.0` follows the published `0.17.0` downstream sink productionization
baseline. The development line adds the first protocol-neutral SPI contracts,
app bridge, configuration model, status evidence, and productionization route
for future Kafka, HTTP, and MQTT downstream sink adapters.

## Baseline

- Add downstream sink SPI contracts without adding Kafka, HTTP, MQTT,
  database, Redis, or external queue dependencies to `runtime-core`.
- Stabilize the adapter-facing record envelope contract derived from
  `protocol-runtime.record.v1`, including sink adapter contract metadata and
  forward-compatible extension fields.
- Define delivery result outcomes for success, retryable failure, permanent
  failure, backpressure rejection, configuration rejection, serialization
  failure, transport failure, timeout, dead-letter routing, and operator
  diagnostics.
- Bridge current logging, file, and in-memory sinks through the SPI while
  keeping failed-record isolation and backpressure behavior in `runtime-app`.
- Document retry and dead-letter boundaries before implementing durable retry
  behavior or broker-specific dead-letter delivery.
- Plan dedicated `runtime-sink-kafka`, `runtime-sink-http`, and
  `runtime-sink-mqtt` module boundaries.
- Add adapter configuration model expectations for endpoints, topics,
  authentication references, timeouts, batching, retry posture, dead-letter
  output, and secret redaction.
- Add fake/no-network adapter test coverage before live dependency integration.
- Extend operator troubleshooting guidance for downstream adapter failures,
  failed-record correlation, status evidence, and dependency-specific checks.

## Scope

`0.18.0` is an SPI baseline and boundary-design line for downstream sink adapters.
It should not introduce Kafka producer, HTTP client, MQTT publisher, database,
Redis, object storage, or external queue dependencies during the planning
stage. Future implementation work must place those dependencies only in
dedicated `runtime-sink-*` modules or explicit app/adapter boundaries.

## Dependency Policy

`runtime-core` must remain free of Spring, Netty, Kafka, MQTT, HTTP client,
database, Redis, object storage, external queue, retry-store, dead-letter
store, sink-adapter, observability exporter, deployment wrapper, service
manager, filesystem layout, package-management, checksum/signing, installer,
runtime-supervisor, and operations-agent dependencies.

`runtime-ingress-http` remains protocol payload ingestion only. It must not
become a downstream HTTP sink, management API, deployment API, package API,
operations API, diagnostics API, or adapter delivery surface.

Kafka, HTTP, and MQTT downstream delivery dependencies belong only in future
dedicated `runtime-sink-*` modules or explicit app/adapter boundaries.

`protocol-sdk` remains parser-only. Parser behavior changes belong in
`protocol-sdk` releases, not in `protocol-runtime` sink adapter planning.

## Verification Targets

Planning and follow-up PRs must pass:

- `git diff --check`
- `mvn -q verify`
- central-release dry run with publishing disabled when release metadata
  changes
- standalone TCP collector smoke
- standalone HTTP collector smoke
- management HTTP smoke
- distribution package smoke
- release artifact smoke
- long-running smoke
- release artifact regression smoke
- downstream sink failure smoke
- dependency boundary checks proving sink adapter work does not enter
  `runtime-core`, `runtime-protocol-*`, `runtime-ingress-*`, or
  `protocol-sdk`
- GitHub CI on planning and release PRs

## Publication

- Development line: `0.18.0-SNAPSHOT` closed by the `0.18.0` release branch
- Release branch: `codex/release-0.18.0`
- Tag: pending
- Central deployment: pending
- Central state: not published; release branch validation passed
- GitHub Release: pending

The roadmap is tracked in [`roadmap-0.18.0.md`](roadmap-0.18.0.md).
Downstream sink adapter troubleshooting is tracked in
[`downstream-sink-adapters.md`](downstream-sink-adapters.md).
