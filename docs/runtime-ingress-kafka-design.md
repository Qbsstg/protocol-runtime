# Runtime Kafka Ingress Design

This note defines the first `runtime-ingress-kafka` boundary. It started as a
`0.5.0` design contract and becomes an implementation baseline in the `0.7.0`
development line.

## Goals

- Consume Kafka records as runtime ingress payloads.
- Convert each accepted Kafka record into an `IngressEnvelope`.
- Preserve Kafka topic, partition, offset, timestamp, key, and headers as
  envelope attributes.
- Dispatch the envelope through `RuntimePipelineRunner` and the configured
  `RuntimeParserBinding`.
- Keep consumer lifecycle, offset policy, replay posture, and commit timing out
  of `runtime-core`.
- Keep protocol SDK modules out of `runtime-ingress-kafka`.

## Non-Goals

- No Kafka dependency in `runtime-core`, `protocol-sdk`, or
  `runtime-protocol-*`; Kafka dependencies belong only in
  `runtime-ingress-kafka` or test modules.
- No protocol parsing inside the Kafka adapter.
- No Kafka producer or downstream sink behavior in the ingress adapter.
- No durable retry queue, database sink, Redis cache, or object storage.
- No schema registry, Kafka Streams, transactions, consumer group management
  UI, metrics exporter, or operational dashboard in the first boundary.
- No replacement for the existing TCP/Netty or HTTP ingress paths.

## Proposed Module

| Module | Responsibility |
| --- | --- |
| `runtime-ingress-kafka` | Own Kafka consumer lifecycle, subscription assignment, record polling, source mapping, payload mapping, offset attributes, backpressure handling, parse-failure routing, and commit policy. |

Allowed dependencies:

- `runtime-core`
- Kafka client libraries selected by this module
- test dependencies

Not allowed:

- protocol SDK parser modules
- `runtime-protocol-*`
- HTTP, MQTT, database, Redis, object storage, or observability exporters
- downstream Kafka producer or sink ownership
- app framework dependencies in `runtime-core`

## Consumer Shape

The first consumer should be intentionally narrow:

| Field | Baseline |
| --- | --- |
| Subscription | explicit topic list or pattern, owned by adapter config |
| Consumer group | configured per named adapter instance |
| Payload | record value bytes forwarded as the envelope payload |
| Key | optional envelope attribute, never required by `runtime-core` |
| Headers | copied to envelope attributes with a stable prefix |
| Protocol selection | adapter/source configuration, not topic parsing by default |
| Offset policy | adapter-owned, never a `runtime-core` concern |

The adapter may support topic or header based source id mapping. If the source
id cannot be resolved, the record must not be parsed and the adapter must use a
documented failure or skip policy.

## Configuration Shape

The Kafka adapter should follow the named-adapter pattern planned for `0.5.0`:

```properties
collector.sources=station-1
collector.source.station-1.id=iec104:station-1
collector.source.station-1.protocol=iec104

collector.kafka.consumers=kafka-main
collector.kafka.consumer.kafka-main.bootstrapServers=localhost:9092
collector.kafka.consumer.kafka-main.groupId=protocol-runtime
collector.kafka.consumer.kafka-main.topics=iec104-raw
collector.kafka.consumer.kafka-main.source=station-1
collector.kafka.consumer.kafka-main.sourceIdMode=CONFIGURED
collector.kafka.consumer.kafka-main.autoOffsetReset=latest
collector.kafka.consumer.kafka-main.commitMode=AFTER_ACCEPT
collector.kafka.consumer.kafka-main.maxPollRecords=100
collector.kafka.consumer.kafka-main.pollTimeoutMillis=1000

collector.backpressure=ACCEPT
```

Baseline validation rules:

- `bootstrapServers` must be non-empty
- `groupId` must be non-empty unless explicit assignment mode is added later
- either `topics` or `topicPattern` must be configured, but not both
- runtime-app configuration must bind each consumer to a known `source`
- `sourceIdMode` must be `HEADER`, `TOPIC`, `KEY`, or `CONFIGURED`
- `sourceIdHeader` is required when `sourceIdMode=HEADER`
- the referenced source id is used when `sourceIdMode=CONFIGURED`
- the referenced source protocol must map to an existing runtime parser binding
- `maxPollRecords` must be positive
- `pollTimeoutMillis` must be positive
- commit mode must be one of the supported adapter policies

## Envelope Mapping

The adapter converts a Kafka record into an `IngressEnvelope`:

| Envelope field | Kafka source |
| --- | --- |
| `sourceId` | configured source id, header, topic mapping, or key mapping |
| `transport` | `kafka` |
| `payload` | record value bytes |
| `receivedAt` | adapter clock when record is accepted, or record timestamp if explicitly configured |
| `attributes` | consumer name, topic, partition, offset, timestamp, timestamp type, key, headers, selected protocol, and consumer group |

Suggested attribute names:

| Attribute | Meaning |
| --- | --- |
| `kafka.consumer` | named adapter instance |
| `kafka.group` | consumer group id |
| `kafka.topic` | record topic |
| `kafka.partition` | record partition |
| `kafka.offset` | record offset |
| `kafka.timestamp` | record timestamp, if present |
| `kafka.timestampType` | Kafka timestamp type |
| `kafka.key` | decoded key when safe to expose |
| `kafka.header.<name>` | decoded header values, with collisions handled deterministically |
| `runtime.protocol` | selected runtime parser binding |

Kafka-specific values remain attributes. They must not become new
`runtime-core` fields unless more than one adapter proves the need for a
protocol-neutral contract.

## Runtime Wiring

The adapter should not parse protocol frames directly. It owns only Kafka record
to envelope conversion and runner invocation:

```text
Kafka ConsumerRecord
  -> runtime-ingress-kafka
  -> IngressEnvelope
  -> RuntimePipelineRunner
  -> selected RuntimeParserBinding
  -> RecordSink / FailureSink
  -> adapter commit policy
```

Runner creation can follow one of two models:

- per-consumer runner, when the consumer has one configured source/protocol
- per-record runner lookup, when source id or protocol can vary by topic,
  header, or key

The lookup model belongs to the adapter or app assembly. It must not require
`runtime-core` to know Kafka topic, partition, offset, or commit rules.

## Backpressure Policy

Kafka backpressure handling is adapter policy:

| Runtime decision | Baseline Kafka behavior |
| --- | --- |
| `ACCEPT` | parse and route the record through the runner |
| `DROP` | skip parsing, record a dropped counter/failure event, and apply configured commit policy |
| `RETRY_LATER` | pause partitions or delay polling without committing the rejected offset |

The adapter should avoid busy loops when `RETRY_LATER` is returned. A future
implementation can use partition pause/resume or bounded sleep at the adapter
edge. `runtime-core` must not manage Kafka polling or partition state.

## Commit Policy

Commit timing is the highest-risk Kafka-specific behavior. It must stay in the
adapter:

| Commit mode | Meaning |
| --- | --- |
| `MANUAL` | adapter exposes no automatic commit; app code or a later coordinator owns commits |
| `AFTER_ACCEPT` | commit after the runtime runner accepts the record, including routed parse failures |
| `AFTER_PARSE_SUCCESS` | commit only when at least one parsed record is routed successfully |
| `NEVER` | do not commit offsets, useful for replay tests |

The first implementation should prefer `MANUAL` or `AFTER_ACCEPT` because
`RuntimePipelineRunner` already treats parser exceptions as routed failures
after the envelope is accepted. `AFTER_PARSE_SUCCESS` requires richer parse
outcome observability before it can be safe.

Offset commit failures must route to a failure boundary owned by the adapter or
app assembly. They must not alter SDK parser behavior.

## Parse Failure Policy

Malformed payloads should route to `FailureSink` through
`RuntimePipelineRunner`. The adapter then applies commit policy:

| Policy | Parse failure behavior |
| --- | --- |
| `AFTER_ACCEPT` | routed failure is considered accepted; offset may be committed |
| `AFTER_PARSE_SUCCESS` | offset is not committed because parsing did not produce a success |
| `MANUAL` | no adapter commit; caller decides |
| `NEVER` | no commit |

Poison-message quarantine, dead-letter topics, and retry topics are downstream
delivery concerns. They belong in a future sink or app module, not in the first
Kafka ingress boundary.

## Replay Posture

Replay must be explicit:

- `autoOffsetReset` controls only the starting point when no committed offset
  exists.
- `NEVER` commit mode is suitable for repeatable local replay tests.
- A replay run should use a distinct group id unless it intentionally resumes
  an existing group.
- Topic rewinds, seek operations, and timestamp based replay belong to adapter
  or app configuration, not `runtime-core`.

## Test Strategy

Unit tests:

- configuration validation
- source id resolution from header/topic/key/configuration
- envelope attribute mapping for topic, partition, offset, timestamp, key, and
  headers
- backpressure mapping for accept, drop, and retry later
- commit policy decisions for parse success and parse failure

Integration tests:

- fake consumer records without live Kafka for baseline behavior
- optional embedded Kafka or Testcontainers tests only inside
  `runtime-ingress-kafka` or test-only modules after the dependency boundary is
  approved
- dependency boundary checks proving no Kafka dependency appears in
  `runtime-core`, `protocol-sdk`, or `runtime-protocol-*`

The first implementation avoids requiring a live broker in normal `mvn verify`.
Broker-backed tests can be added later under a dedicated profile after the
record mapping boundary is stable.

## Open Decisions

- Resolved for `0.7.0`: the first implementation depends directly on
  `org.apache.kafka:kafka-clients` in `runtime-ingress-kafka`.
- Whether source id mapping from topic names should use a simple template or a
  pluggable resolver.
- Whether commit mode should be per consumer, per source, or per protocol.
- Whether dead-letter publishing belongs in a future `runtime-sink-kafka`
  module or a runtime app assembly.
- Whether broker-backed tests should use embedded Kafka, Testcontainers, or a
  separate opt-in integration profile.
