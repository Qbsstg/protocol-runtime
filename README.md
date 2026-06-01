# Protocol Runtime

Protocol Runtime is the JDK 21 collector runtime for the Java 8 compatible
[`protocol-sdk`](https://github.com/Qbsstg/protocol-sdk) parser modules.

The runtime owns ingestion, session lifecycle, parser binding, backpressure,
batching, and downstream delivery concerns. The SDK remains parser-only and
must not depend on this repository.

## Status

This repository is in bootstrap. The first target is a small runtime-core
contract surface and an IEC104 binding that consumes the published
`protocol-sdk` `0.7.0` artifacts from Maven Central.

## Module Plan

| Module | Status | Responsibility |
| --- | --- | --- |
| `runtime-core` | Bootstrap | Runtime-neutral contracts: source identity, ingress envelope, parser binding, parse results, record/failure sinks, backpressure, pipeline runner, and lifecycle boundary. |
| `runtime-protocol-iec104` | Bootstrap | First runtime protocol binding around `io.github.qbsstg:protocol-iec104:0.7.0`. |
| `runtime-ingress-tcp-netty` | Baseline | Minimal Netty TCP ingress handler and server bootstrap that bind a TCP port, create one `RuntimePipelineRunner` per accepted connection, convert `ByteBuf` payloads to `IngressEnvelope`, apply backpressure decisions, and dispatch to sinks. |
| `runtime-smoke-tests` | Test-only | Cross-module smoke tests that prove ingress, runtime-core, and protocol bindings work together without turning those combinations into production dependencies. |

Future modules may include MQTT, Kafka, HTTP ingress, pipelines, sinks, and a
deployable runtime application. Those dependencies belong here, not in
`protocol-sdk`.

## Runtime Core Contract

`runtime-core` is intentionally dependency-light. It defines the contracts that
transport adapters and protocol bindings share:

- `SourceId` identifies a data source across transports.
- `IngressEnvelope` carries source, transport, payload, timestamp, and
  attributes into the parser boundary.
- `RuntimeParserBinding` adapts protocol SDK parsers to runtime parse results.
- `ParsedRecord` and `ParseFailure` describe successful and failed parse output.
- `RecordSink` and `FailureSink` receive routed parse output.
- `BackpressureStrategy` returns an `ACCEPT`, `RETRY_LATER`, or `DROP`
  decision before parsing.
- `RuntimePipelineRunner` wires parser binding, sinks, backpressure, and
  lifecycle together.

`runtime-core` must not depend on Spring, Netty, Kafka, MQTT, HTTP clients or
servers, database drivers, Redis clients, or any deployable runtime adapter.
Those dependencies belong in adapter modules outside the core contract. The
current Netty dependency is isolated to `runtime-ingress-tcp-netty`.

## TCP Netty Ingress

`runtime-ingress-tcp-netty` currently provides the first TCP ingress baseline:

- `TcpNettyServer` binds a configured host/port and shuts down Netty event loop
  groups gracefully.
- `TcpNettyServerConfig` supports loopback or any-address binding, including
  port `0` for tests.
- `TcpNettyChannelInitializer` creates one `RuntimePipelineRunner` per accepted
  connection through `TcpNettyPipelineRunnerFactory`.
- `TcpNettyIngressHandler` copies inbound `ByteBuf` data into an immutable
  `IngressEnvelope` payload.
- `TcpSourceIdResolver` resolves a runtime `SourceId` from the remote address or
  channel id.
- `TcpConnectionAttributes` attaches `tcp.channel.id`, `tcp.session.id`, local
  address, and remote address attributes when available.
- `RuntimePipelineRunner` receives each envelope and owns parser binding,
  backpressure, record sink, failure sink, and lifecycle routing.
- `RETRY_LATER` backpressure pauses Netty `autoRead`; `DROP` emits a
  `TcpNettyBackpressureEvent` without pausing the channel.

The module is still a baseline. It does not yet manage reconnects, expose
protocol-specific server builders, provide TLS, or provide durable retry queues.

## Smoke Tests

`runtime-smoke-tests` holds cross-module verification only. The first smoke test
feeds IEC104 TCP bytes through:

```text
EmbeddedChannel or real localhost Socket
  -> TcpNettyServer / TcpNettyIngressHandler
  -> RuntimePipelineRunner
  -> Iec104RuntimeBinding
  -> RecordSink / FailureSink
```

It covers complete IEC104 frames, split TCP reads, backpressure that prevents
parsing, malformed IEC104 frames routed to the failure sink, and a real TCP
socket path through the server bootstrap.

## Dependency Direction

Allowed:

```text
protocol-runtime -> protocol-sdk
```

Forbidden:

```text
protocol-sdk -> protocol-runtime
protocol-sdk -> Spring or Netty
protocol-sdk -> MQTT or Kafka clients
protocol-sdk -> HTTP server/client frameworks
protocol-sdk -> database or Redis clients
runtime-core -> Netty
runtime-core -> protocol-specific runtime bindings
```

## Build

JDK 21 is required.

```bash
mvn -q verify
```

## SDK Version

The bootstrap runtime consumes published SDK `0.7.0` artifacts:

- `io.github.qbsstg:protocol-core:0.7.0`
- `io.github.qbsstg:protocol-iec104:0.7.0`

The runtime can move to newer SDK versions after they are published and
verified.
