# Protocol Runtime 0.17.0 Release Notes

Release notes for the published `0.17.0` runtime release.

`0.17.0` follows the published `0.16.0` production runtime operations
baseline. The development line adds the first downstream sink productionization
baseline for standalone collectors.

## Baseline

- Stabilize the file sink output schema before downstream tools depend on it
  through `protocol-runtime.record.v1`.
- Define a record envelope output contract for parsed records, source metadata,
  protocol metadata, timestamps, quality/status evidence, delivery metadata,
  and failures.
- Classify delivery failures into operator-actionable categories including
  configuration, filesystem, serialization, write, flush, backpressure,
  retryable, permanent, and unknown failures.
- Isolate failed records from normal output as bounded
  `protocol-runtime.failed-record.v1` samples for troubleshooting.
- Expose sink schema, failed-record isolation, delivery counters, failure type
  counters, and last sink failure evidence in status/self-check output.
- Revisit sink backpressure policy around sink saturation, parse success, sink
  failure, failed-record isolation failures, and future broker delivery.
- Design retry and dead-letter boundaries without adding a durable retry store
  in this baseline.
- Define Kafka, HTTP, and MQTT downstream sink adapter boundaries so future
  producer/client dependencies land only in dedicated `runtime-sink-*` or
  app/adapter modules.
- Add operator sink troubleshooting and smoke expectations for delivery,
  failed-record isolation, backpressure, and sample export.

## Scope

`0.17.0` is a downstream sink productionization baseline. Implementation
remains focused on existing file/logging/in-memory sinks. Dedicated Kafka,
HTTP, MQTT, database, Redis, or external queue delivery is still deferred until
a future goal explicitly opens a dedicated sink adapter module.

## Dependency Policy

`runtime-core` must remain free of Spring, Netty, Kafka, MQTT, HTTP client,
database, Redis, object storage, external queue, retry-store, sink-adapter,
observability exporter, deployment wrapper, service manager, filesystem
layout, package-management, checksum/signing, installer, runtime-supervisor,
and operations-agent dependencies.

`runtime-ingress-http` remains protocol payload ingestion only. It must not
become a downstream HTTP sink, management API, deployment API, package API,
operations API, or diagnostics API.

Kafka, HTTP, and MQTT downstream delivery dependencies belong only in future
dedicated `runtime-sink-*` modules or explicit app/adapter boundaries.

`protocol-sdk` remains parser-only. Parser behavior changes belong in
`protocol-sdk` releases, not in `protocol-runtime` downstream sink
productionization.

## Verification Targets

Implementation and release PRs must pass:

- `git diff --check`
- `mvn -q verify`
- central-release dry run with publishing disabled
- standalone TCP collector smoke
- standalone HTTP collector smoke
- management HTTP smoke
- distribution package smoke
- release artifact smoke
- long-running smoke
- release artifact regression smoke
- downstream sink failure smoke
- dependency boundary checks proving downstream sink work does not enter
  `runtime-core`, `runtime-protocol-*`, `runtime-ingress-*`, or `protocol-sdk`
- GitHub CI on release PRs

## Publication

- Release branch: `codex/release-0.17.0`
- Release PR: <https://github.com/Qbsstg/protocol-runtime/pull/88>
- Tag: `v0.17.0`
- Release commit: `3e2dfca236e1883610353f8704f5ce28d0b0e4ea`
- Central deployment: `cb3c502d-59e6-4829-9d02-941374042d84`
- Central state: `PUBLISHED`
- Public Maven Central verification: passed for runtime modules,
  `runtime-app:jar:standalone`, `runtime-app:zip:distribution`,
  `runtime-app:tar.gz:distribution`, signatures, and checksum sidecars.
- GitHub Release:
  <https://github.com/Qbsstg/protocol-runtime/releases/tag/v0.17.0>

The release-readiness audit is tracked in
[`release-readiness-0.17.0.md`](release-readiness-0.17.0.md).
