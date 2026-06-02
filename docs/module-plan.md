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
| `runtime-protocol-iec101` | Planned `0.4.0` runtime binding for `protocol-iec101` parser output. | Serial-session policy and richer IEC101 record mapping after the parser binding baseline is stable. |
| `runtime-protocol-iec103` | Planned `0.4.0` runtime binding for `protocol-iec103` parser output. | Protection-event mapping and serial-session policy after the parser binding baseline is stable. |
| `runtime-protocol-modbus` | Planned `0.4.0` runtime binding for `protocol-modbus` parser output. | Modbus TCP/UDP runtime policy after the parser binding baseline is stable. |
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
modules, but it is test-only and is not a supported application dependency.
Cross-module combinations proven there should not be moved into `runtime-core`.

`runtime-app` may combine TCP ingress, protocol bindings, and app-level sinks
because it is the deployable assembly boundary. It still must not move those
dependencies into `runtime-core` or `protocol-sdk`.

## `0.4.0` Development Posture

The `0.4.0` runtime line starts from the published `0.3.0` standalone collector
hardening release and opens the Maven reactor at `0.4.0-SNAPSHOT`.

The goal is multi-protocol runtime expansion around published
`protocol-sdk:0.7.0` parser artifacts:

| Module | 0.4.0 goal |
| --- | --- |
| `runtime-core` | Stay dependency-light; add only protocol-neutral contracts if multi-protocol binding proves a shared need. |
| `runtime-protocol-iec104` | Preserve the existing IEC104 binding and compatibility path. |
| `runtime-protocol-iec101` | Add or prepare a runtime binding for `protocol-iec101:0.7.0` without transport or app dependencies. |
| `runtime-protocol-iec103` | Add or prepare a runtime binding for `protocol-iec103:0.7.0` without transport or app dependencies. |
| `runtime-protocol-modbus` | Add or prepare a runtime binding for `protocol-modbus:0.7.0` without transport or app dependencies. |
| `runtime-ingress-tcp-netty` | Continue to own TCP byte ingress only; do not consume protocol SDK modules. |
| `runtime-app` | Add protocol selection at the app/source/listener boundary while preserving the IEC104 default configuration path. |
| `runtime-smoke-tests` | Add repository-only cross-module checks after individual protocol bindings are stable. |

`0.4.0` should not introduce Spring, Kafka, MQTT, HTTP, database, Redis,
observability exporter, serial-port, or UDP dependencies into `runtime-core`.
If those integrations are needed, they should land in dedicated adapter modules
or app-owned assembly code.

## `0.3.0` Development Posture

The `0.3.0` runtime line starts from the published `0.2.0` standalone
collector and focuses on production hardening:

| Module | 0.3.0 goal |
| --- | --- |
| `runtime-app` | Add stronger configuration validation, multi-source and multi-listener config shape, collector lifecycle state, health/status output, file sink rotation, parse failure isolation, and richer backpressure policy. |
| `runtime-ingress-tcp-netty` | Preserve TCP session lifecycle and backpressure boundaries while supporting multiple app-owned listeners. |
| `runtime-core` | Stay dependency-light; expose only small contracts if production hardening proves a reusable need. |
| `runtime-protocol-iec104` | Continue to consume released `protocol-iec104` SDK artifacts and keep protocol parsing separate from app policy. |
| `runtime-smoke-tests` | Add cross-module checks for multi-listener startup, status visibility, failure isolation, and backpressure behavior. |

`0.3.0` should not introduce Kafka, MQTT, HTTP, database, Redis, metrics
exporter, or application framework dependencies into `runtime-core`. If those
integrations are needed, they should land in dedicated adapter modules or
app-owned assembly code.

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
| `runtime-smoke-tests` | Repository-only integration verification; Central publishing is skipped for future releases. |

The release should not claim a deployable collector application, reconnect
policy, TLS, durable queues, storage sinks, Kafka/MQTT/HTTP ingestion, or formal
IEC104 session-state coverage. Those belong in later runtime milestones after
the baseline contracts are externally consumable.
