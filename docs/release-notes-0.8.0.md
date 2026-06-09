# Protocol Runtime 0.8.0 Release Notes

Release notes for the published `0.8.0` runtime release.

## Highlights

- Opened the Maven reactor at `0.8.0-SNAPSHOT` after the published `0.7.0`
  Kafka runtime-app release, then fixed the release branch at `0.8.0`.
- Added the first `runtime-ingress-mqtt` module with MQTT client dependencies
  isolated to that adapter module.
- Added MQTT client configuration and validation for broker URI, client id,
  topic subscriptions, configured/topic source id mode, QoS posture, and
  protocol selection.
- Mapped MQTT payloads into runtime `IngressEnvelope` objects while preserving
  topic, QoS, retained flag, duplicate flag, packet id, protocol, source, and
  client attributes.
- Dispatched MQTT messages through `RuntimePipelineRunner` without adding MQTT
  APIs to `runtime-core`.
- Added runtime-app MQTT client configuration, standalone collector assembly,
  status output, and fake-source tests for end-to-end parser dispatch without a
  live broker.
- Added [`examples/collector-mqtt.properties`](../examples/collector-mqtt.properties)
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

## Release Branch Status

The `0.8.0` release branch fixed the Maven reactor at `0.8.0` and merged to
`main`.

## Publication

`0.8.0` has been tagged as `v0.8.0`, uploaded to Maven Central, manually
published, and verified.

- Release commit: `aa3d8da2761ad4d833a501629d9a3b1b8b30a1a2`
- Central deployment: `f2b54d7a-924f-44f2-bbd9-6199fa1514a3`
- Central state: `PUBLISHED`
- GitHub Release:
  <https://github.com/Qbsstg/protocol-runtime/releases/tag/v0.8.0>
- Maven Central verification:
  - `runtime-core:0.8.0` POM returned HTTP 200
  - `runtime-ingress-mqtt:0.8.0` POM returned HTTP 200
  - `runtime-app:0.8.0` POM returned HTTP 200
  - isolated local Maven repositories resolved `runtime-core:0.8.0`,
    `runtime-ingress-mqtt:0.8.0`, `runtime-app:0.8.0`, and
    `runtime-app:0.8.0:standalone`
