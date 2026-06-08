# Protocol Runtime 0.5.0 Release Notes

Draft release notes for the `0.5.0` runtime release-candidate line.

## Planned Highlights

- Open the Maven reactor at `0.5.0-SNAPSHOT` after the published `0.4.0`
  multi-protocol runtime release.
- Plan HTTP, Kafka, and MQTT ingestion adapter boundaries without moving those
  dependencies into `runtime-core` or `protocol-sdk`.
- Add the first HTTP ingress design note covering endpoint shape,
  configuration, envelope mapping, response policy, backpressure behavior,
  parse-failure routing, request limits, and tests.
- Add the first JDK `HttpServer` based HTTP ingress baseline for POST payloads,
  request limits, source mapping, and runtime backpressure responses.
- Add the first Kafka ingress design note covering consumer ownership,
  topic/partition/offset attributes, source mapping, commit timing, replay
  posture, backpressure behavior, parse-failure routing, and test strategy.
- Add the first MQTT ingress design note covering client/session ownership,
  topic/source mapping, QoS posture, retained and duplicate message policy,
  reconnect behavior, backpressure behavior, parse-failure routing, and test
  strategy.
- Separate ingress adapter responsibilities from downstream sink adapter
  responsibilities.
- Preserve the existing TCP/Netty standalone collector path and app-level
  protocol selection from `0.4.0`.
- Keep `runtime-protocol-*` modules parser-binding only.
- Define adapter configuration, source mapping, failure routing, and
  backpressure behavior before adding heavy adapter dependencies.

## Scope

`0.5.0` starts the adapter productionization line. It makes HTTP, Kafka, and
MQTT collection work reviewable by first locking the module boundaries,
dependency rules, configuration shape, and test strategy.

The release line now includes the first narrow HTTP ingress implementation in a
dedicated module. Kafka, MQTT, downstream broker sinks, and heavier adapter
dependencies remain deferred to dedicated modules.

## Dependency Policy

`runtime-core` must remain adapter-free. HTTP, Kafka, MQTT, database, Redis,
object storage, application framework, and observability exporter dependencies
belong in dedicated adapter modules, sink modules, or app assembly.

`protocol-sdk` remains parser-only and must not depend on `protocol-runtime`.

`runtime-protocol-*` modules continue to depend only on `runtime-core`, their
published SDK parser module, and tests.

## Verification Target

Before release readiness, the branch should pass:

- `git diff --check`
- `mvn -q verify`
- dependency boundary checks for `runtime-core` and `runtime-protocol-*`
- documentation checks proving adapter dependencies are assigned to adapter
  modules only
- existing runtime-app and smoke-test coverage for the TCP/Netty and
  multi-protocol paths

## Release Readiness Status

This remains a development draft. No tag or real Maven Central upload is part
of the current `0.5.0` development branch.

HTTP ingress design is tracked in
[`runtime-ingress-http-design.md`](runtime-ingress-http-design.md).

The first `runtime-ingress-http` baseline now uses JDK `HttpServer` to accept
POST payloads, map configured/header/path `SourceId` values, enforce payload
size limits, and translate runtime backpressure decisions to HTTP responses
without adding third-party HTTP dependencies.

Kafka ingress design is tracked in
[`runtime-ingress-kafka-design.md`](runtime-ingress-kafka-design.md). It keeps
Kafka consumer lifecycle, offset commit timing, replay posture, and
topic/partition/offset metadata inside a future adapter module rather than
`runtime-core`.

MQTT ingress design is tracked in
[`runtime-ingress-mqtt-design.md`](runtime-ingress-mqtt-design.md). It keeps
MQTT client/session lifecycle, QoS posture, retained and duplicate message
policy, reconnect behavior, and topic metadata inside a future adapter module
rather than `runtime-core`.
