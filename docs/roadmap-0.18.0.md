# Protocol Runtime 0.18.0 Roadmap

`0.18.0` starts after the published `0.17.0` downstream sink
productionization baseline. The line plans the first dedicated downstream sink
adapter boundary for Kafka, HTTP, and MQTT delivery without implementing
producer/client adapters in the planning step.

The goal is to define a stable SPI and operator model before adding external
delivery dependencies. Adapter implementation can follow only after the module
boundaries, record envelope contract, delivery result contract, retry and
dead-letter posture, smoke expectations, and dependency policy are explicit.

## Goals

- Define a downstream sink SPI that can be implemented by app-local file sinks
  and future Kafka, HTTP, and MQTT delivery adapters.
- Keep `runtime-core` dependency-light while deciding which neutral contracts
  are worth promoting from app-owned implementation details.
- Stabilize the record envelope contract used between runtime parsing and
  downstream delivery adapters.
- Stabilize the delivery result contract for success, retryable failure,
  permanent failure, backpressure rejection, dead-letter routing, and operator
  diagnostics.
- Define retry and dead-letter boundaries without adding a durable retry store,
  external queue, database, or Redis dependency to the planning baseline.
- Plan dedicated `runtime-sink-kafka`, `runtime-sink-http`, and
  `runtime-sink-mqtt` modules with clear dependency ownership.
- Define adapter configuration model boundaries for endpoint identity,
  authentication references, timeout policy, batching posture, retry posture,
  dead-letter output, and secret redaction.
- Define adapter smoke expectations that do not require live external systems
  during normal `mvn verify`.
- Extend operator troubleshooting guidance for delivery failure diagnosis,
  failed-record correlation, adapter status, and dependency-specific evidence.

## Module Boundary

| Module or area | `0.18.0` planning responsibility |
| --- | --- |
| `runtime-core` | Stay dependency-light. Consider only minimal protocol-neutral sink SPI contracts after proving they are not app-specific. Add no Spring, Netty, Kafka, MQTT, HTTP client, database, Redis, external queue, retry-store, dead-letter store, sink-adapter, or exporter dependencies. |
| `runtime-app` | Continue to own current logging/file/in-memory sink assembly, app-local sink configuration, status, self-check, hot-check, failed-record isolation, and operator examples. |
| Future `runtime-sink-kafka` | Planned Kafka producer delivery adapter. Kafka producer dependencies belong only here or in an explicit app/adapter module, never in `runtime-core` or `runtime-ingress-kafka`. |
| Future `runtime-sink-http` | Planned downstream HTTP delivery adapter. HTTP client dependencies belong only here or in an explicit app/adapter module; `runtime-ingress-http` remains ingestion-only. |
| Future `runtime-sink-mqtt` | Planned MQTT publisher delivery adapter. MQTT publisher dependencies belong only here or in an explicit app/adapter module, never in `runtime-core` or `runtime-ingress-mqtt`. |
| Examples and docs | Own adapter configuration examples, record envelope examples, retry/dead-letter policy notes, dependency boundary rules, and operator troubleshooting. |
| CI/smoke | Own fake adapter, no-network adapter, and packaged app smoke expectations as repository verification only. |

## SPI Topics

### Sink SPI Shape

The planning line should decide whether the first SPI belongs in
`runtime-core`, `runtime-app`, or a dedicated sink API module. The SPI should
describe:

- sink identity and lifecycle
- delivery request input
- delivery result output
- health/readiness contribution
- backpressure contribution
- failure evidence and counters
- shutdown and flush behavior
- secret-safe diagnostic output

No SPI decision should require Kafka, HTTP, MQTT, database, Redis, or external
queue classes in `runtime-core`.

### Record Envelope Contract

The adapter-facing record envelope should build on
`protocol-runtime.record.v1` and remain stable enough for downstream tooling:

- `schemaVersion`
- `sourceId`
- `protocol`
- `receivedAt`
- `parsedAt`
- `recordType`
- `quality`
- decoded payload
- raw payload metadata
- parser diagnostics
- sink metadata
- extension fields for adapter-specific attributes

Adapter-specific serialization can wrap the envelope, but it should not mutate
parser output or hide source/listener evidence needed by operators.

### Delivery Result Contract

The planning line should define delivery outcomes before adapter
implementation:

- accepted or delivered
- retryable failure
- permanent failure
- backpressure rejection
- configuration rejection
- authentication/authorization failure
- serialization failure
- transport failure
- timeout
- dead-letter routed
- unknown failure with bounded diagnostics

Delivery result evidence should feed status JSON, management status,
self-check, hot-check, logs, counters, and failed-record samples without
requiring an external store.

## Adapter Planning

### Kafka Sink Adapter

The future Kafka sink adapter should own Kafka producer dependencies, topic
selection, key mapping, partition posture, send timeout, acknowledgment policy,
batching posture, retry posture, and dead-letter topic boundaries. It must not
reuse `runtime-ingress-kafka` as a producer surface.

### HTTP Sink Adapter

The future HTTP sink adapter should own HTTP client dependencies, endpoint
configuration, method/path/header policy, authentication references, request
timeout, response classification, retry posture, and dead-letter fallback. It
must not reuse `runtime-ingress-http`, which remains protocol payload
ingestion only.

### MQTT Sink Adapter

The future MQTT sink adapter should own MQTT publisher dependencies, broker
connection configuration, topic mapping, QoS policy, retained-message posture,
publish timeout, retry posture, and dead-letter fallback. It must not reuse
`runtime-ingress-mqtt` as a publisher surface.

## Out of Scope

- Implementing Kafka producers, HTTP clients, MQTT publishers, database
  writers, Redis queues, object storage sinks, or external queue dependencies.
- Adding sink adapter dependencies to `runtime-core`, `runtime-protocol-*`,
  `runtime-ingress-*`, or `protocol-sdk`.
- Reusing `runtime-ingress-http` as a downstream HTTP sink, management API,
  deployment API, package API, operations API, or diagnostics API.
- Moving retry, dead-letter, broker delivery, or storage policy into parser
  bindings.
- Changing `protocol-sdk` parser behavior or making `protocol-sdk` depend on
  `protocol-runtime`.

## Smoke Expectations

The planning line should define smoke coverage before adapter implementation:

- fake sink adapter contract tests for delivery result mapping
- record envelope compatibility checks
- retry/dead-letter classification checks without an external broker
- app configuration validation for future sink adapter blocks
- status/management/self-check evidence for configured sink adapters
- operator troubleshooting examples for failed delivery
- dependency boundary checks proving adapter dependencies do not leak into
  `runtime-core`, protocol bindings, ingress modules, or `protocol-sdk`

## Readiness Checklist

- [x] `0.17.0` release artifacts are published and verified from Maven Central.
- [x] GitHub Release `v0.17.0` is published.
- [x] Maven reactor is opened at `0.18.0-SNAPSHOT`.
- [ ] Downstream sink SPI boundary is designed.
- [ ] Record envelope contract is reviewed for adapter-facing use.
- [ ] Delivery result contract is designed.
- [ ] Retry and dead-letter boundaries are documented.
- [ ] Kafka sink adapter module boundary is documented.
- [ ] HTTP sink adapter module boundary is documented.
- [ ] MQTT sink adapter module boundary is documented.
- [ ] Adapter configuration model is documented.
- [ ] Adapter smoke expectations are documented.
- [ ] Operator sink adapter troubleshooting guidance is drafted.
