# Protocol Runtime 0.8.0 Release Notes

Draft release notes for the `0.8.0` runtime release line.

## Planned Highlights

- Open the Maven reactor at `0.8.0-SNAPSHOT` after the published `0.7.0` Kafka
  runtime-app release.
- Add the first `runtime-ingress-mqtt` module with MQTT client dependencies
  isolated to that adapter module.
- Add MQTT client configuration and validation for broker URI, client id,
  topic subscriptions, configured/topic source id mode, QoS posture, and
  protocol selection.
- Map MQTT payloads into runtime `IngressEnvelope` objects while preserving
  topic, QoS, retained flag, duplicate flag, packet id, protocol, source, and
  client attributes.
- Dispatch MQTT messages through `RuntimePipelineRunner` without adding MQTT
  APIs to `runtime-core`.
- Add runtime-app MQTT client configuration, standalone collector assembly,
  status output, and fake-source tests for end-to-end parser dispatch without a
  live broker.
- Add [`examples/collector-mqtt.properties`](../examples/collector-mqtt.properties)
  as the minimal IEC104-over-MQTT standalone collector configuration.

## Scope

`0.8.0` focuses on the MQTT ingress adapter and standalone app assembly
boundary. The first baseline is message-oriented and should not require a live
MQTT broker in normal `mvn verify`; runtime-app should use fake source tests to
prove that MQTT payloads can flow through `RuntimePipelineRunner`,
`runtime-protocol-*`, and the configured sink. Broker-backed operational smoke
tests remain follow-up work.

## Dependency Policy

`runtime-core` must remain free of MQTT, Kafka, HTTP, Spring, database, Redis,
object storage, and observability exporter dependencies.

`runtime-ingress-mqtt` may depend on a selected MQTT client library, but it must
not depend on protocol SDK modules, runtime protocol bindings, HTTP/Kafka
adapters, or downstream sink modules.

`runtime-app` may depend on `runtime-ingress-mqtt` because it is the deployable
assembly boundary. MQTT dependencies still must not enter `runtime-core` or
`runtime-protocol-*`.

## Verification Target

Before release branch work, the readiness branch passed:

- `git diff --check`
- `mvn -q verify`
- `mvn -q -Pcentral-release -Dgpg.skip=true -Dcentral.skipPublishing=true deploy`
- standalone TCP and HTTP smoke scripts with a JDK 21+ `JAVA_BIN`
- dependency boundary checks proving MQTT is isolated to
  `runtime-ingress-mqtt` and app assembly, not `runtime-core` or
  `runtime-protocol-*`
- MQTT ingress unit tests for source resolution, envelope attributes,
  invalid source handling, backpressure result mapping, and lifecycle decisions
- runtime-app MQTT tests for MQTT-only config parsing, status formatting,
  fake-source dispatch, parse failure routing, and backpressure behavior
- an example MQTT collector configuration for manual broker-backed runs outside
  normal `mvn verify`

The readiness audit is tracked in
[`release-readiness-0.8.0.md`](release-readiness-0.8.0.md).
