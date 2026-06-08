# Protocol Runtime 0.5.0 Roadmap

`0.5.0` starts after the published `0.4.0` multi-protocol runtime release. The
development line opens with Maven reactor version `0.5.0-SNAPSHOT`.

The focus is adapter boundary design for HTTP, Kafka, and MQTT ingestion. The
goal is to make those integrations easy to add without moving framework,
broker, network, or storage dependencies into `runtime-core` or
`protocol-sdk`.

## Primary Goal

Define the first production adapter boundaries for runtime ingestion and
delivery while keeping the existing runtime contracts small:

- `runtime-core` stays protocol-neutral and adapter-free.
- `runtime-protocol-*` modules keep adapting SDK parser output only.
- adapter dependencies live in dedicated adapter modules or app assembly.
- `runtime-app` remains the deployable composition point.

## Included Scope

- Maven development line:
  - move the reactor from released `0.4.0` to `0.5.0-SNAPSHOT`
  - keep `protocol-sdk.version` at the published `0.7.0` line until a newer SDK
    release is intentionally selected
- Adapter boundary documents:
  - define HTTP ingress ownership, request limits, response policy, payload
    mapping, and source id mapping
  - define Kafka ingress ownership, topic/partition/offset attributes, replay
    posture, commit timing, and parse-failure routing
  - define MQTT ingress ownership, topic/source mapping, QoS posture, retained
    message handling, and reconnect/session ownership
  - define sink adapter boundaries separately from ingress adapters
- Configuration model planning:
  - keep existing TCP listener configuration compatible
  - model future adapter config as named adapter instances
  - keep adapter-specific settings out of `runtime-core`
  - keep protocol selection at the source/listener/adapter edge
- Failure and backpressure policy:
  - document how adapter modules should react to `ACCEPT`, `RETRY_LATER`, and
    `DROP`
  - preserve parse failure isolation from `0.3.0`
  - avoid broker acknowledgement or HTTP response policy inside
    `runtime-core`
- Verification:
  - add dependency boundary checks before adding adapter dependencies
  - define adapter test fixtures without requiring live brokers by default
  - use integration tests for real broker/server behavior only inside adapter
    modules or test-only modules

## Deferred Scope

- Making Spring Boot the default application framework.
- Adding Kafka, MQTT, or HTTP dependencies to `runtime-core`.
- Adding broker/client dependencies to `protocol-sdk`.
- Adding Kafka, MQTT, or HTTP dependencies to `runtime-protocol-*`.
- Durable queues, object storage, database sinks, Redis, and distributed
  scheduling.
- TLS certificate management and production secrets management.
- Full HTTP management API, dashboard, or metrics exporter.
- Changing SDK parser behavior.

## Candidate Adapter Modules

These modules are candidates for `0.5.x` work. They should be introduced only
when the module contract is clear and tests can prove the dependency boundary.

| Module | Candidate responsibility | Dependency boundary |
| --- | --- | --- |
| `runtime-ingress-http` | Accept HTTP requests, map request bodies to `IngressEnvelope`, choose response policy from runtime outcome. | May depend on HTTP server/client libraries; must not depend on protocol SDK modules. |
| `runtime-ingress-kafka` | Consume Kafka records, map topic/partition/offset and headers to envelope attributes, route parse failures without committing unsafe offsets. | May depend on Kafka client libraries; must not depend on protocol SDK modules or HTTP/MQTT adapters. |
| `runtime-ingress-mqtt` | Subscribe to MQTT topics, map topic/QoS/retain/session attributes to envelopes, own reconnect behavior at the adapter edge. | May depend on MQTT client libraries; must not depend on protocol SDK modules or Kafka/HTTP adapters. |
| `runtime-sink-kafka` | Publish parsed records or failures to Kafka as an optional downstream adapter. | May depend on Kafka client libraries; must not feed dependencies back into `runtime-core`. |
| `runtime-adapter-testkit` | Provide reusable fake sinks, fake runner wiring, fixture payloads, and boundary assertions for adapter tests. | Test support only; no production broker or framework dependency leakage. |

## Development Sequence

1. Open the `0.5.0-SNAPSHOT` Maven line and document the adapter boundary plan.
2. Add dependency boundary guardrails for planned adapter modules before adding
   broker or HTTP dependencies.
3. Design adapter configuration shapes for named HTTP, Kafka, and MQTT
   instances without changing the old TCP `collector.properties` path.
4. Choose one narrow adapter baseline for implementation after the boundary is
   reviewed. Prefer the adapter with the smallest production risk and clearest
   tests.
5. Add repository-only smoke tests for any implemented adapter path.
6. Update readiness notes before deciding whether `0.5.0` publishes only design
   contracts or includes the first adapter implementation.

## Acceptance Criteria

Before `0.5.0` release readiness:

- README and Chinese README describe the `0.5.0-SNAPSHOT` development line.
- `docs/module-plan.md` and `docs/module-boundaries.md` document adapter module
  dependency rules.
- HTTP, Kafka, and MQTT adapter boundaries are documented before implementation
  work starts.
- `runtime-core` still has no Netty, SDK protocol, Spring, Kafka, MQTT, HTTP,
  database, Redis, or observability exporter dependencies.
- `runtime-protocol-*` modules still do not depend on transport, app, or
  adapter modules.
- Any adapter implementation lives in a dedicated module or app/test assembly.
- Existing TCP/Netty and multi-protocol runtime smoke tests continue to pass.
