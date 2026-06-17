# Protocol Runtime 0.17.0 Roadmap

`0.17.0` starts after the published `0.16.0` production runtime operations
baseline. The line implements the first downstream sink productionization
baseline for standalone collectors without adding broker, HTTP client,
database, Redis, or external queue dependencies to the current baseline.

The goal is to make parsed-record delivery safer and easier to operate before
introducing dedicated Kafka, HTTP, MQTT, or other downstream sink adapters.

## Goals

- Stabilize the file sink output schema so operators can consume collector
  output consistently across releases.
- Define a record envelope output contract that separates source identity,
  protocol metadata, payload parse result, delivery metadata, timestamps,
  quality/status fields, and error evidence.
- Classify downstream delivery failures into operator-actionable categories,
  such as configuration, filesystem, serialization, write, flush,
  backpressure, retryable transient failure, permanent rejection, and unknown
  failure.
- Isolate failed records from normal output so bad records or sink failures do
  not silently corrupt the main delivery stream.
- Export bounded failed-record samples for troubleshooting without requiring a
  database, Redis, external queue, or broker.
- Revisit sink backpressure policy around parse success, sink failure, file
  sink saturation, and future broker sink saturation.
- Design retry and dead-letter boundaries without implementing a persistent
  retry store in this release line.
- Document Kafka, HTTP, and MQTT downstream sink adapter boundaries so future
  producer/client dependencies land only in dedicated `runtime-sink-*` or
  app/adapter modules.
- Add operator sink troubleshooting guidance and smoke expectations for normal
  delivery, failed-record isolation, backpressure, and sample export.

## Module Boundary

| Module or area | `0.17.0` baseline responsibility |
| --- | --- |
| `runtime-core` | Keep the existing runtime contracts dependency-light. Do not add Spring, Netty, Kafka, MQTT, HTTP client, database, Redis, queue, retry-store, sink-adapter, or exporter dependencies. |
| `runtime-app` | Own current file/in-memory/logging sink assembly, stable JSONL envelope schema, operator-facing file sink behavior, failed-record evidence, and configuration examples. |
| `examples` and `docs` | Own file sink schema notes, record envelope examples, sink troubleshooting, retry/dead-letter boundary design, and adapter boundary documents. |
| CI/smoke | Own future verification for file sink schema stability, failed-record isolation, sink backpressure behavior, and release artifact regression paths. |
| Future `runtime-sink-file` | Candidate module for file delivery hardening if implementation outgrows `runtime-app`; no module is required in this baseline. |
| Future `runtime-sink-kafka` | Candidate adapter boundary for Kafka producer delivery; Kafka producer dependencies must not enter `runtime-core`, `runtime-ingress-kafka`, or `protocol-sdk`. |
| Future `runtime-sink-http` | Candidate adapter boundary for downstream HTTP delivery; it must not reuse `runtime-ingress-http`, which remains ingestion-only. |
| Future `runtime-sink-mqtt` | Candidate adapter boundary for MQTT publishing; MQTT publisher dependencies must not enter `runtime-core`, `runtime-ingress-mqtt`, or `protocol-sdk`. |

## Out of Scope

- Implementing Kafka producers, HTTP clients, MQTT publishers, database
  writers, Redis queues, object storage sinks, or external queue dependencies.
- Adding sink adapter dependencies to `runtime-core`.
- Reusing `runtime-ingress-http` as a downstream HTTP sink, management API,
  deployment API, package API, operations API, or diagnostics API.
- Moving delivery retry, dead-letter, or storage policy into
  `runtime-protocol-*` parser bindings.
- Changing `protocol-sdk` parser behavior or making `protocol-sdk` depend on
  `protocol-runtime`.
- Introducing durable retry stores, databases, Redis, or external brokers for
  this baseline.

## Design Topics

### File Sink Schema Stability

The published runtime already provides file sink output for standalone
collector deployments. `0.17.0` makes the schema explicit before users build
downstream tooling around it:

- stable field names and types through `protocol-runtime.record.v1`
- source and listener identity
- protocol and ASDU/frame metadata
- parse success/failure metadata
- payload encoding policy for raw bytes and decoded records
- event timestamp and collector receive timestamp
- delivery attempt metadata
- forward-compatible extension fields

### Failure Classification

Delivery failures are explainable without reading stack traces first. The
initial taxonomy distinguishes:

- configuration errors
- filesystem/path/permission failures
- serialization failures
- write and flush failures
- sink saturation and backpressure decisions
- retryable transient failures
- permanent downstream rejection
- operator-cancelled delivery
- unknown failures with bounded diagnostic evidence

### Failed-Record Isolation

The baseline defines where failed record samples live, how many samples are
retained, how sensitive payload bytes are represented, and how operators
correlate a sample with status, logs, and source/listener metadata.

### Retry and Dead-Letter Boundaries

`0.17.0` should document retry and dead-letter design before implementation:

- retry policy belongs to a sink adapter or app-owned delivery component, not
  parser bindings
- durable retry stores are deferred
- dead-letter output must have an explicit schema and operator retention policy
- future broker-specific retry behavior stays in dedicated `runtime-sink-*`
  adapters

### Adapter Boundaries

Kafka, HTTP, and MQTT downstream sinks are delivery adapters, not ingress
adapters. Their dependencies must remain in dedicated sink modules or
app/adapter boundaries when they are implemented later.

### Operator Sink Troubleshooting

Troubleshooting guidance should teach operators to collect evidence in this
order:

- collector version and package metadata
- active configuration and selected sink type
- file sink path, permissions, disk capacity, rotation/retention posture, and
  last successful write evidence
- failed-record sample path and bounded sample count
- status export, management status, and sink failure counters
- backpressure decision counters and rejection reasons
- logs around serialization, filesystem, and delivery failures
- record envelope examples that reproduce the delivery issue without exposing
  sensitive payload bytes unnecessarily

### Smoke Expectations

Downstream sink smoke should eventually cover:

- normal parsed-record delivery to file sink output
- stable record envelope fields and forward-compatible extension behavior
- malformed or unsupported payload routing without corrupting normal output
- filesystem or serialization failure isolation
- failed sample export with bounded retention
- sink-failure-triggered backpressure behavior
- package and release artifact regression paths that keep sink behavior
  reproducible for operators

## Readiness Checklist

- [x] `0.16.0` release artifacts are published and verified from Maven Central.
- [x] GitHub Release `v0.16.0` is published.
- [x] Maven reactor is opened at `0.17.0-SNAPSHOT`.
- [x] `codex/release-0.17.0` fixes the Maven reactor at `0.17.0` for the
  release branch.
- [x] README and Chinese README describe the `0.17.0` downstream sink baseline
  line.
- [x] `docs/module-plan.md` and `docs/module-boundaries.md` describe the
  downstream sink productionization boundary.
- [x] `docs/release-notes-0.17.0.md` records the baseline scope.
- [x] File sink schema stability is documented.
- [x] Failure classification and failed-record isolation boundaries are
  documented.
- [x] Retry/dead-letter and Kafka/HTTP/MQTT downstream adapter boundaries are
  documented.
- [x] Runtime app emits `protocol-runtime.record.v1` and
  `protocol-runtime.parse-failure.v1` JSONL envelopes for file/logging sinks.
- [x] Runtime app classifies sink delivery failures and exposes classification
  counters in status JSON.
- [x] Runtime app writes bounded `protocol-runtime.failed-record.v1` samples to
  app-local failed-record isolation storage.
- [x] Standalone and distribution smoke cover sink schema/status evidence.
- [x] Sink failure smoke covers failed-record isolation and readiness evidence.
- [x] Operator sink troubleshooting and smoke expectations are drafted.
- [x] `docs/release-readiness-0.17.0.md` records the release branch gates,
  validation checklist, publication policy, and final publication follow-up.
