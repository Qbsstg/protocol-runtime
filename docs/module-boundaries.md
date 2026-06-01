# Runtime Module Boundaries

This note records the dependency and publishing boundaries for the first
`protocol-runtime` release line.

## Runtime And SDK Boundary

`protocol-runtime` consumes released `protocol-sdk` artifacts. The dependency
direction is one-way:

```text
protocol-runtime -> protocol-sdk
```

Forbidden directions:

```text
protocol-sdk -> protocol-runtime
protocol-sdk -> Spring, Netty, Kafka, MQTT, HTTP, database, or Redis clients
runtime-core -> Netty
runtime-core -> protocol-specific runtime bindings
```

Protocol SDK modules remain parser-only. Runtime modules own ingestion,
session lifecycle, parser binding, backpressure, sink routing, and future
deployment concerns.

## Module Rules

| Module | Allowed dependencies | Not allowed |
| --- | --- | --- |
| `runtime-core` | JDK and test dependencies only. | Spring, Netty, Kafka, MQTT, HTTP, database, Redis, SDK protocol modules, deployable adapter dependencies. |
| `runtime-protocol-iec104` | `runtime-core`, released `protocol-iec104`, tests. | Netty, transport adapters, storage, deployment frameworks. |
| `runtime-ingress-tcp-netty` | `runtime-core`, Netty transport, tests. | Protocol SDK modules, Spring, Kafka, MQTT, HTTP, database, Redis. |
| `runtime-smoke-tests` | Runtime modules and tests. | Production publication or application dependency use. |

## `0.1.0` Published Surface

The `0.1.0` release is a baseline library release:

- `runtime-core` publishes the neutral pipeline contracts.
- `runtime-protocol-iec104` adapts `protocol-iec104:0.7.0` into runtime parse
  results.
- `runtime-ingress-tcp-netty` publishes TCP byte ingress, server bootstrap,
  active session registry, lifecycle events, backpressure handling, and failure
  routing.
- `runtime-smoke-tests` proves the cross-module IEC104 over TCP path but is
  skipped during Maven deploy.

## Deferred Runtime Concerns

The following concerns should stay out of `0.1.0` library modules unless a
separate design decision changes the release scope:

- deployable Spring Boot or other application assembly
- reconnect policy and connection scheduling
- IEC104 STARTDT/STOPDT/TESTFR policy and command routing
- TLS configuration and certificate management
- Kafka, MQTT, HTTP ingress
- database, Redis, object storage, or durable queues
- metrics exporters and operational dashboards

These features can be added as separate runtime modules after the published
baseline has stable contracts.
