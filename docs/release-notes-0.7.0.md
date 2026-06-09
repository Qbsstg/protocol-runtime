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
- Add runtime-app Kafka consumer configuration, standalone collector assembly,
  status output, and fake-source tests for end-to-end parser dispatch without a
  live broker.

## Scope

`0.7.0` focuses on the Kafka ingress adapter and standalone app assembly
boundary. The first baseline is record-oriented and does not require a live
Kafka broker in normal `mvn verify`; runtime-app uses fake source tests to prove
that Kafka records can flow through `RuntimePipelineRunner`,
`runtime-protocol-*`, and the configured sink. Broker-backed operational smoke
tests remain follow-up work.

## Dependency Policy

`runtime-core` must remain free of Kafka, HTTP, MQTT, Spring, database, Redis,
object storage, and observability exporter dependencies.

`runtime-ingress-kafka` may depend on `org.apache.kafka:kafka-clients`, but it
must not depend on protocol SDK modules, runtime protocol bindings, HTTP/MQTT
adapters, or downstream sink modules.

`runtime-app` may depend on `runtime-ingress-kafka` because it is the deployable
assembly boundary. Kafka dependencies still must not enter `runtime-core` or
`runtime-protocol-*`.

## Verification Target

Before release branch work, the readiness branch should pass:

- `git diff --check`
- `mvn -q verify`
- dependency boundary checks proving Kafka is isolated to
  `runtime-ingress-kafka` and app assembly, not `runtime-core` or
  `runtime-protocol-*`
- Kafka ingress unit tests for source resolution, envelope attributes,
  invalid source handling, backpressure result mapping, and commit decisions
- runtime-app Kafka tests for Kafka-only config parsing, status formatting,
  fake-source dispatch, parse failure routing, and backpressure behavior

## Release Branch Status

The `0.7.0` release branch fixes the Maven reactor at `0.7.0`. The release PR
does not create a tag and does not perform a real Maven Central upload; those
remain final publication steps after the release PR merges to `main`.
