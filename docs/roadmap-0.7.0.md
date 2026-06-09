# Protocol Runtime 0.7.0 Roadmap

`0.7.0` starts after the published `0.6.0` HTTP runtime-app release. The
development line opens with Maven reactor version `0.7.0-SNAPSHOT`.

The release target is the first Kafka ingress baseline. Kafka client
dependencies are allowed only in `runtime-ingress-kafka` and test modules. They
must not enter `runtime-core`, `runtime-protocol-*`, `runtime-app` by accident,
or `protocol-sdk`.

## Goals

- Keep `runtime-core` dependency-light and free of Spring, Netty, Kafka, MQTT,
  HTTP, database, Redis, and observability exporter dependencies.
- Add `runtime-ingress-kafka` as a dedicated adapter module with the Kafka
  client dependency isolated to that module.
- Convert Kafka `ConsumerRecord<byte[], byte[]>` payloads into
  `IngressEnvelope` instances without protocol parsing inside the adapter.
- Preserve topic, partition, offset, timestamp, key, headers, selected protocol,
  source id mode, and consumer identity as envelope attributes.
- Support source id resolution from configured source, record header, topic, or
  key.
- Map runtime backpressure decisions to Kafka adapter handling results without
  moving offset or polling policy into `runtime-core`.
- Keep runtime-app Kafka collector assembly as follow-up work after the adapter
  record boundary is stable.

## Target Module Work

| Module | `0.7.0` target |
| --- | --- |
| `runtime-core` | No Kafka, broker, offset, or polling APIs. Existing envelope and pipeline contracts should be enough for the first baseline. |
| `runtime-ingress-kafka` | Add Kafka client dependency, consumer config model, source id resolution, record-to-envelope mapping, backpressure result mapping, commit mode decisions, and unit tests using fake `ConsumerRecord` instances. |
| `runtime-app` | Stay free of Kafka dependencies until app-level Kafka collector assembly is explicitly added. |
| `runtime-protocol-*` | Reuse existing parser bindings for Kafka payloads without transport-specific code. |
| `runtime-smoke-tests` | Add Kafka/app smoke coverage only after runtime-app Kafka assembly lands. |
| `runtime-ingress-mqtt` | Remain design-only until the `0.8.0` implementation line opens. |

## Candidate Work Items

1. Open the `0.7.0-SNAPSHOT` Maven line and add `runtime-ingress-kafka` to the
   reactor.
2. Add `KafkaIngressConsumerConfig`, source id modes, commit modes, and adapter
   validation.
3. Add `KafkaRecordEnvelopeMapper` for payload and attribute mapping.
4. Add `KafkaRecordHandler` for `RuntimePipelineRunner` dispatch and
   backpressure result mapping.
5. Add unit tests for configured/header/topic/key source id resolution,
   attributes, commit decisions, invalid source handling, and module factories.
6. Add runtime-app Kafka collector configuration and assembly in a later PR
   after the adapter record boundary is stable.
7. Add release-readiness notes before the `0.7.0` release branch.

## Non-Goals

- Kafka producer or downstream Kafka sink behavior.
- Dead-letter topics, retry topics, durable queues, database, Redis, object
  storage, or schema registry integration.
- Kafka Streams, transactions, management UI, metrics exporter, or operational
  dashboard.
- Runtime-app Kafka listener/consumer assembly until the adapter boundary is
  proven by unit tests.
- MQTT implementation before the `0.8.0` line.
- New parser behavior inside `protocol-sdk`.

## Dependency Boundaries

- `runtime-core` must not depend on Kafka, HTTP, MQTT, Netty, Spring, database,
  Redis, object storage, or observability exporter artifacts.
- `runtime-ingress-kafka` may depend on `org.apache.kafka:kafka-clients` and
  `runtime-core`.
- `runtime-ingress-kafka` must not depend on protocol SDK modules,
  `runtime-protocol-*`, `runtime-ingress-http`, `runtime-ingress-mqtt`,
  database, Redis, or downstream sink modules.
- `runtime-protocol-*` modules must not depend on ingress adapters or app code.
- `protocol-sdk` remains parser-only and does not depend on
  `protocol-runtime`.

## Readiness Criteria

Before `0.7.0` release readiness:

- README and Chinese README describe the `0.7.0-SNAPSHOT` development line.
- `docs/module-plan.md` and `docs/module-boundaries.md` describe the Kafka
  ingress adapter boundary.
- Kafka record mapping, source id resolution, invalid source handling,
  backpressure results, and commit decisions are covered by unit tests.
- `git diff --check` passes.
- `mvn -q verify` passes.
- dependency boundary checks prove Kafka remains isolated to
  `runtime-ingress-kafka` and does not enter `runtime-core` or
  `runtime-protocol-*`.
