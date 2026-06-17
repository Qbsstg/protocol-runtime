# Protocol Runtime 0.17.0 Release Notes

Release notes draft for the `0.17.0` runtime release.

`0.17.0` follows the published `0.16.0` production runtime operations
baseline. The development line opens with downstream sink productionization
planning for standalone collectors.

## Planned Baseline

- Stabilize the file sink output schema before downstream tools depend on it.
- Define a record envelope output contract for parsed records, source metadata,
  protocol metadata, timestamps, quality/status evidence, delivery metadata,
  and failures.
- Classify delivery failures into operator-actionable categories.
- Isolate failed records from normal output and define bounded sample export
  behavior for troubleshooting.
- Revisit sink backpressure policy around sink saturation, parse success,
  sink failure, and future broker delivery.
- Design retry and dead-letter boundaries without adding a durable retry store
  in the planning step.
- Define Kafka, HTTP, and MQTT downstream sink adapter boundaries so future
  producer/client dependencies land only in dedicated `runtime-sink-*` or
  app/adapter modules.
- Add operator sink troubleshooting and smoke expectations for delivery,
  failed-record isolation, backpressure, and sample export.

## Scope

`0.17.0` is a downstream sink productionization planning line. The first step
is documentation and boundary design. Implementation should remain focused on
existing file/logging/in-memory sinks unless a later goal explicitly opens a
dedicated sink adapter module.

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
`protocol-sdk` releases, not in `protocol-runtime` downstream sink planning.

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
- downstream sink smoke once implemented
- dependency boundary checks proving downstream sink work does not enter
  `runtime-core`, `runtime-protocol-*`, `runtime-ingress-*`, or `protocol-sdk`
- GitHub CI on release PRs

## Publication

- Tag: not created
- Central deployment: not started
- Central state: not published
- GitHub Release: not created
