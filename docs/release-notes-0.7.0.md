# Protocol Runtime 0.7.0 Release Notes

Draft release notes for the `0.7.0` runtime release line.

## Planned Highlights

- Open the Maven reactor at `0.7.0-SNAPSHOT` after the published `0.6.0` HTTP
  runtime-app release.
- Add the first `runtime-ingress-kafka` module with Kafka client dependencies
  isolated to that adapter module.
- Add Kafka consumer configuration and validation for topic/topic pattern,
  source id mode, commit mode, poll limits, and protocol selection.
- Map Kafka `ConsumerRecord<byte[], byte[]>` instances into runtime
  `IngressEnvelope` objects while preserving topic, partition, offset,
  timestamp, key, headers, protocol, and source attributes.
- Dispatch Kafka records through `RuntimePipelineRunner` without adding Kafka
  APIs to `runtime-core`.
- Keep runtime-app Kafka collector assembly as follow-up work after the adapter
  record boundary is stable.

## Scope

`0.7.0` focuses on the Kafka ingress adapter boundary. The first baseline is
record-oriented and does not require a live Kafka broker in normal `mvn verify`.
It should prove the data mapping, source selection, and backpressure semantics
that app-level Kafka consumer assembly can build on.

## Dependency Policy

`runtime-core` must remain free of Kafka, HTTP, MQTT, Spring, database, Redis,
object storage, and observability exporter dependencies.

`runtime-ingress-kafka` may depend on `org.apache.kafka:kafka-clients`, but it
must not depend on protocol SDK modules, runtime protocol bindings, HTTP/MQTT
adapters, or downstream sink modules.

## Verification Target

Before release branch work, the readiness branch should pass:

- `git diff --check`
- `mvn -q verify`
- dependency boundary checks proving Kafka is isolated to
  `runtime-ingress-kafka`
- Kafka ingress unit tests for source resolution, envelope attributes,
  invalid source handling, backpressure result mapping, and commit decisions
