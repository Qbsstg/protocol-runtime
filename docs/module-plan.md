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
| `runtime-ingress-tcp-netty` | Provide the first Netty TCP ingress baseline: `ByteBuf` to `IngressEnvelope`, source id resolution, session attributes, backpressure handling, and dispatch to `RuntimePipelineRunner`. | Server bootstrap, IEC104 sessions, Modbus TCP sessions, reconnects, heartbeat policy, and durable retry queues. |
| `runtime-smoke-tests` | Prove the first IEC104 over TCP runtime path with EmbeddedChannel, `TcpNettyIngressHandler`, `RuntimePipelineRunner`, `Iec104RuntimeBinding`, and sinks. | More cross-module runtime paths after new ingress and protocol bindings land. |

## Deferred Modules

| Module | Reason deferred |
| --- | --- |
| `runtime-ingress-mqtt` | Needs topic/source mapping and payload policy after core contracts settle. |
| `runtime-ingress-kafka` | Needs replay semantics, offsets, error routing, and record identity rules. |
| `runtime-ingress-http` | Needs request limits, response policy, and JSON/binary mapping decisions. |
| `runtime-pipeline` | Needs backpressure and batching decisions proven by first ingress adapters. |
| `runtime-sink-*` | Storage and downstream integrations should follow stable parsed-record contracts. |
| `runtime-app` | Deployment assembly should wait until core adapters and sinks exist. |

## Dependency Boundaries

`runtime-ingress-tcp-netty` is the only module that may depend on Netty for the
TCP baseline. `runtime-core` remains adapter-free, and `protocol-sdk` remains a
parser-only dependency consumed by `runtime-protocol-*` modules.

`runtime-smoke-tests` may combine runtime ingress modules and protocol binding
modules, but it is test-only. Cross-module combinations proven there should not
be moved into `runtime-core`.
