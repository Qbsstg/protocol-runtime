# Runtime Module Plan

This note records the first open-source module shape for `protocol-runtime`.

## Principles

- Runtime code targets JDK 21.
- Runtime modules consume released `protocol-sdk` artifacts from Maven Central.
- Dependency direction is one-way from runtime modules to SDK modules.
- Transport, queue, database, HTTP, MQTT, Kafka, Netty, scheduling, retry, and
  deployment concerns stay out of `protocol-sdk`.
- Runtime contracts should be small and typed before adding heavy adapters.
- `runtime-core` must stay free of Spring, Netty, Kafka, MQTT, HTTP, database,
  Redis, and deployment dependencies.

## Bootstrap Modules

| Module | First responsibility | Later expansion |
| --- | --- | --- |
| `runtime-core` | Protocol-neutral contracts for source identity, ingress payloads, parser bindings, parse results, record/failure sinks, backpressure, pipeline runner, and lifecycle boundary. | Batching, metrics tags, queue decisions, and richer delivery policies. |
| `runtime-protocol-iec104` | Bind IEC104 SDK stream decoding to runtime envelopes and records. | Session-aware command routing, strict/permissive policy configuration, and richer record mapping. |
| `runtime-ingress-tcp-netty` | Provide the first Netty TCP ingress baseline: server bootstrap, port binding, per-connection `RuntimePipelineRunner` creation, active session registry, connection lifecycle events, `ByteBuf` to `IngressEnvelope`, source id resolution, session attributes, backpressure handling, exception routing, and dispatch to sinks. | IEC104 sessions, Modbus TCP sessions, reconnects, heartbeat policy, TLS, and durable retry queues. |
| `runtime-app` | Provide the first standalone IEC104 TCP collector assembly with property-based configuration, source id selection, backpressure mode, and logging/file/in-memory sinks. | Richer app config, service wrappers, metrics, TLS, reconnect policy, and downstream sink adapters. |
| `runtime-smoke-tests` | Prove the first IEC104 over TCP runtime path with EmbeddedChannel and real localhost socket tests through `TcpNettyServer`, `TcpNettyIngressHandler`, `RuntimePipelineRunner`, `Iec104RuntimeBinding`, sinks, active sessions, and disconnect lifecycle. | More cross-module runtime paths after new ingress and protocol bindings land. |

## Deferred Modules

| Module | Reason deferred |
| --- | --- |
| `runtime-ingress-mqtt` | Needs topic/source mapping and payload policy after core contracts settle. |
| `runtime-ingress-kafka` | Needs replay semantics, offsets, error routing, and record identity rules. |
| `runtime-ingress-http` | Needs request limits, response policy, and JSON/binary mapping decisions. |
| `runtime-pipeline` | Needs backpressure and batching decisions proven by first ingress adapters. |
| `runtime-sink-*` | Storage and downstream integrations should follow stable parsed-record contracts. |

## Dependency Boundaries

`runtime-ingress-tcp-netty` is the only module that may depend on Netty for the
TCP baseline. `runtime-core` remains adapter-free, and `protocol-sdk` remains a
parser-only dependency consumed by `runtime-protocol-*` modules.

`runtime-smoke-tests` may combine runtime ingress modules and protocol binding
modules, but it is test-only. Cross-module combinations proven there should not
be moved into `runtime-core`.

`runtime-app` may combine TCP ingress, protocol bindings, and app-level sinks
because it is the deployable assembly boundary. It still must not move those
dependencies into `runtime-core` or `protocol-sdk`.

## `0.2.0` Development Posture

The `0.2.0` runtime line starts from the published `0.1.0` contracts and adds a
minimal runnable collector:

| Module | 0.2.0 goal |
| --- | --- |
| `runtime-app` | Build a JDK 21 standalone IEC104 TCP collector that can be launched from a shaded jar. |
| `runtime-ingress-tcp-netty` | Keep TCP server lifecycle and port-conflict behavior suitable for app usage. |
| `runtime-core` | Stay dependency-light; no app or adapter dependencies enter the core contract. |
| `runtime-protocol-iec104` | Continue to consume released `protocol-iec104` SDK artifacts only. |

`0.2.0` is still not a full collector platform. Kafka, MQTT, HTTP ingestion,
database sinks, Redis, TLS, reconnect scheduling, and operational dashboards
remain later milestones.

## `0.1.0` Release Posture

The `0.1.0` runtime line should publish the bootstrap runtime library modules:

| Module | Maven Central posture |
| --- | --- |
| `protocol-runtime` | Parent POM for repository builds and release metadata. |
| `runtime-core` | Published baseline runtime contracts. |
| `runtime-protocol-iec104` | Published IEC104 runtime binding against `protocol-iec104:0.7.0`. |
| `runtime-ingress-tcp-netty` | Published first TCP/Netty ingress adapter. |
| `runtime-smoke-tests` | Not published; repository-only integration verification. |

The release should not claim a deployable collector application, reconnect
policy, TLS, durable queues, storage sinks, Kafka/MQTT/HTTP ingestion, or formal
IEC104 session-state coverage. Those belong in later runtime milestones after
the baseline contracts are externally consumable.
