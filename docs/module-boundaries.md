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
| `runtime-app` | Runtime modules, JDK logging/file APIs, tests. | New parser implementation, SDK changes, Spring, Kafka, MQTT, HTTP, database, Redis. |
| `runtime-smoke-tests` | Runtime modules and tests. | Application dependency use. Central publishing is skipped for future releases. |

## `0.1.0` Published Surface

The `0.1.0` release is a baseline library release:

- `runtime-core` publishes the neutral pipeline contracts.
- `runtime-protocol-iec104` adapts `protocol-iec104:0.7.0` into runtime parse
  results.
- `runtime-ingress-tcp-netty` publishes TCP byte ingress, server bootstrap,
  active session registry, lifecycle events, backpressure handling, and failure
  routing.
- `runtime-smoke-tests` proves the cross-module IEC104 over TCP path and is not
  a supported application dependency. It sets both `maven.deploy.skip=true` and
  `central.skipPublishing=true` for future releases.

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

## `0.2.0` App Boundary

`runtime-app` is allowed to assemble `runtime-ingress-tcp-netty`,
`runtime-protocol-iec104`, and app-level sinks into a runnable JDK 21 process.
That assembly boundary is intentionally outside `runtime-core`.

The first app baseline may provide:

- IEC104 TCP listening configuration.
- Fixed configured `SourceId` selection.
- `ACCEPT`, `RETRY_LATER`, or `DROP` backpressure configuration.
- Logging, file, and in-memory sinks.
- A shaded executable jar for local operation and integration tests.

The app boundary must not make `protocol-sdk` depend on runtime code, and must
not move Spring, Kafka, MQTT, HTTP, database, or Redis dependencies into
`runtime-core`.

## `0.3.0` Production-Hardening Boundary

The `0.3.0` line may harden `runtime-app` with validation, multi-source
configuration, lifecycle/status state, app-level counters, file sink rotation,
parse failure isolation, and stronger backpressure policy.

Those changes should stay at the app or adapter boundary unless a smaller
protocol-neutral contract is proven necessary. In particular:

- configuration file shape belongs to `runtime-app`
- Netty listener behavior belongs to `runtime-ingress-tcp-netty`
- future HTTP health endpoints belong to an app or HTTP adapter module
- metrics exporters belong to dedicated observability modules
- Kafka, MQTT, HTTP, database, Redis, and object storage dependencies belong to
  dedicated adapter or sink modules
- `protocol-sdk` remains parser-only
