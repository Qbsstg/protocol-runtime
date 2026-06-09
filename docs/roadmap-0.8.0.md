# Protocol Runtime 0.8.0 Roadmap

`0.8.0` starts after the published `0.7.0` Kafka runtime-app release. The
development line opens with Maven reactor version `0.8.0-SNAPSHOT`.

The release target is the first MQTT ingress baseline. MQTT client dependencies
are allowed only in `runtime-ingress-mqtt`, runtime-app assembly, and test
scopes. They must not enter `runtime-core`, `runtime-protocol-*`, or
`protocol-sdk`.

## Goals

- Keep `runtime-core` dependency-light and free of Spring, Netty, Kafka, MQTT,
  HTTP, database, Redis, and observability exporter dependencies.
- Add `runtime-ingress-mqtt` as a dedicated adapter module with the MQTT client
  dependency isolated to that module.
- Convert MQTT binary payloads into `IngressEnvelope` instances without
  protocol parsing inside the adapter.
- Preserve topic, QoS, retained flag, duplicate flag, packet id, selected
  protocol, source id mode, and client identity as envelope attributes.
- Support source id resolution from configured source or topic in the first
  Paho MQTT v3 baseline.
- Map runtime backpressure decisions to MQTT adapter handling results without
  moving acknowledgement, reconnect, or session policy into `runtime-core`.
- Add runtime-app MQTT client configuration and standalone collector assembly
  after the adapter message boundary is stable.

## Target Module Work

| Module | `0.8.0` target |
| --- | --- |
| `runtime-core` | No MQTT, broker, topic, acknowledgement, reconnect, or session APIs. Existing envelope and pipeline contracts should be enough for the first baseline. |
| `runtime-ingress-mqtt` | Add MQTT client dependency, client config model, source id resolution, message-to-envelope mapping, client lifecycle, backpressure result mapping, and unit tests using fake MQTT message fixtures. |
| `runtime-app` | Add MQTT client properties, source binding, protocol runner assembly, collector status, and app tests with fake MQTT message sources after the adapter baseline is stable. MQTT APIs are allowed here only through the dedicated adapter module. |
| `runtime-protocol-*` | Reuse existing parser bindings for MQTT payloads without transport-specific code. |
| `runtime-smoke-tests` | Live-broker MQTT smoke coverage remains follow-up; normal verification uses app-level fake source tests first. |

## Candidate Work Items

1. Open the `0.8.0-SNAPSHOT` Maven line and add `runtime-ingress-mqtt` to the
   reactor.
2. Select a minimal MQTT client dependency and keep it isolated to the adapter.
3. Add `MqttIngressClientConfig`, source id modes, QoS handling posture, and
   adapter validation.
4. Add message-to-envelope mapping for payload and MQTT attributes.
5. Add `MqttMessageHandler` for `RuntimePipelineRunner` dispatch and
   backpressure result mapping.
6. Add unit tests for configured/topic source id resolution,
   attributes, invalid source handling, retained/duplicate flags, and module
   factories.
7. Add runtime-app MQTT collector configuration, status snapshot, standalone
   assembly, and fake-source app tests.
8. Add release-readiness notes before the `0.8.0` release branch.

## Non-Goals

- MQTT downstream publishing or sink behavior.
- Durable queue, database, Redis, object storage, or retry store integration.
- Broker-backed integration tests that require a live MQTT broker in normal
  `mvn verify`.
- MQTT over WebSocket, TLS certificate management, authentication, or secrets
  management.
- HTTP management APIs, metrics exporters, dashboards, and health endpoints.
- New parser behavior inside `protocol-sdk`.

## Progress

- `runtime-ingress-mqtt` adapter module opened with
  `org.eclipse.paho:org.eclipse.paho.client.mqttv3` isolated to that module.
- `MqttIngressClientConfig`, `MqttMessageEnvelopeMapper`,
  `MqttMessageHandler`, `MqttPahoMessageSource`, and module factories establish
  the first MQTT message-to-runtime boundary.
- Unit tests cover configured/topic source id resolution, MQTT envelope
  attributes, invalid source handling, backpressure result mapping, and module
  factory exposure without requiring a live broker.

Remaining `0.8.0` work includes runtime-app MQTT collector assembly,
configuration examples, status output, and release-readiness verification.

## Dependency Boundaries

- `runtime-core` must not depend on MQTT, Kafka, HTTP, Netty, Spring, database,
  Redis, object storage, or observability exporter artifacts.
- `runtime-ingress-mqtt` may depend on an MQTT client library and
  `runtime-core`.
- `runtime-app` may depend on `runtime-ingress-mqtt` for standalone collector
  assembly, but must not move MQTT APIs into `runtime-core` or
  `runtime-protocol-*`.
- `runtime-ingress-mqtt` must not depend on protocol SDK modules,
  `runtime-protocol-*`, `runtime-ingress-http`, `runtime-ingress-kafka`,
  database, Redis, or downstream sink modules.
- `runtime-protocol-*` modules must not depend on ingress adapters or app code.
- `protocol-sdk` remains parser-only and does not depend on
  `protocol-runtime`.

## Readiness Criteria

Before `0.8.0` release readiness:

- README and Chinese README describe the `0.8.0-SNAPSHOT` development line.
- `docs/module-plan.md` and `docs/module-boundaries.md` describe the MQTT
  ingress adapter boundary.
- MQTT message mapping, source id resolution, invalid source handling,
  backpressure results, and lifecycle decisions are covered by unit tests.
- runtime-app MQTT client parsing, status output, fake-source dispatch,
  malformed payload routing, and backpressure behavior are covered by tests.
- an example MQTT collector configuration documents the minimal standalone
  MQTT collector shape.
- `git diff --check` passes.
- `mvn -q verify` passes.
- dependency boundary checks prove MQTT remains isolated to
  `runtime-ingress-mqtt` and does not enter `runtime-core` or
  `runtime-protocol-*`.
