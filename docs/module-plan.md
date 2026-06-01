# Runtime Module Plan

This note records the first open-source module shape for `protocol-runtime`.

## Principles

- Runtime code targets JDK 21.
- Runtime modules consume released `protocol-sdk` artifacts from Maven Central.
- Dependency direction is one-way from runtime modules to SDK modules.
- Transport, queue, database, HTTP, MQTT, Kafka, Netty, scheduling, retry, and
  deployment concerns stay out of `protocol-sdk`.
- Runtime contracts should be small and typed before adding heavy adapters.

## Bootstrap Modules

| Module | First responsibility | Later expansion |
| --- | --- | --- |
| `runtime-core` | Protocol-neutral contracts for source identity, ingress payloads, parsed records, parse failures, parser bindings, and backpressure decisions. | Batching, sink contracts, metrics tags, and queue decisions. |
| `runtime-protocol-iec104` | Bind IEC104 SDK stream decoding to runtime envelopes and records. | Session-aware command routing, strict/permissive policy configuration, and richer record mapping. |
| `runtime-ingress-tcp-netty` | Reserve the TCP/Netty adapter boundary without adding Netty yet. | IEC104 sessions, Modbus TCP sessions, reconnects, heartbeat policy, and backpressure propagation. |

## Deferred Modules

| Module | Reason deferred |
| --- | --- |
| `runtime-ingress-mqtt` | Needs topic/source mapping and payload policy after core contracts settle. |
| `runtime-ingress-kafka` | Needs replay semantics, offsets, error routing, and record identity rules. |
| `runtime-ingress-http` | Needs request limits, response policy, and JSON/binary mapping decisions. |
| `runtime-pipeline` | Needs backpressure and batching decisions proven by first ingress adapters. |
| `runtime-sink-*` | Storage and downstream integrations should follow stable parsed-record contracts. |
| `runtime-app` | Deployment assembly should wait until core adapters and sinks exist. |
