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
| `runtime-protocol-iec101` | `runtime-core`, released `protocol-iec101`, tests. | Netty, serial adapters, storage, deployment frameworks. |
| `runtime-protocol-iec103` | `runtime-core`, released `protocol-iec103`, tests. | Netty, serial adapters, storage, deployment frameworks. |
| `runtime-protocol-modbus` | `runtime-core`, released `protocol-modbus`, tests. | Netty, UDP/TCP adapters, storage, deployment frameworks. |
| `runtime-ingress-tcp-netty` | `runtime-core`, Netty transport, tests. | Protocol SDK modules, Spring, Kafka, MQTT, HTTP, database, Redis. |
| `runtime-ingress-http` | `runtime-core`, JDK `HttpServer`, tests. | Protocol SDK modules, Spring, Netty, Kafka, MQTT, database, Redis, changes to `runtime-core` for HTTP-specific policy. |
| `runtime-ingress-kafka` | `runtime-core`, Kafka client libraries, tests. | Protocol SDK modules, HTTP/MQTT adapter dependencies, database, Redis, changes to `runtime-core` for offset policy. |
| `runtime-ingress-mqtt` | `runtime-core`, MQTT client libraries, tests. | Protocol SDK modules, HTTP/Kafka adapter dependencies, database, Redis, changes to `runtime-core` for topic/session policy. |
| `runtime-app` | Runtime modules, JDK logging/file APIs, JDK `HttpServer` management endpoints, tests. | New parser implementation, SDK changes, Spring framework, database, Redis, moving adapter dependencies into core or protocol bindings. |
| `runtime-smoke-tests` | Runtime modules and tests. | Application dependency use. Central publishing is skipped for future releases. |

## Planned Adapter Module Rules

These modules are not part of the current published surface yet. They define
where future dependencies may live once adapter implementation starts.

| Module | Allowed dependencies | Not allowed |
| --- | --- | --- |
| `runtime-sink-kafka` | `runtime-core`, Kafka client libraries, tests. | Ingress ownership, protocol SDK modules, HTTP/MQTT adapter dependencies, changes to parser bindings. |
| `runtime-adapter-testkit` | Test fixtures, fake sinks, fake runner wiring, and adapter boundary assertions. | Production runtime dependencies or application dependency use. |

The first HTTP ingress design contract and JDK `HttpServer` baseline are tracked in
[`runtime-ingress-http-design.md`](runtime-ingress-http-design.md). It records
HTTP request mapping, response policy, backpressure behavior, parse-failure
routing, request limits, and test strategy without adding third-party HTTP
dependencies.

The first Kafka ingress design contract is tracked in
[`runtime-ingress-kafka-design.md`](runtime-ingress-kafka-design.md). It records
consumer ownership, topic/partition/offset attributes, source mapping,
backpressure handling, commit policy, replay posture, and parse-failure routing.
The `0.7.0` line adds the first implementation baseline while keeping Kafka
dependencies isolated to `runtime-ingress-kafka`.

The first MQTT ingress design contract is tracked in
[`runtime-ingress-mqtt-design.md`](runtime-ingress-mqtt-design.md). It records
client/session ownership, topic/source mapping, QoS posture, retained and
duplicate message policy, reconnect behavior, backpressure handling, and
parse-failure routing before any MQTT client dependency is added.

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

## `0.4.0` Multi-Protocol Boundary

The `0.4.0` line adds runtime protocol binding modules for the published
IEC101, IEC103, and Modbus SDK parser artifacts. Those modules adapt parser
results into runtime records and failures; they do not own transport,
deployment, or downstream delivery.

Allowed:

- `runtime-protocol-iec101` depends on `runtime-core` and `protocol-iec101`
- `runtime-protocol-iec103` depends on `runtime-core` and `protocol-iec103`
- `runtime-protocol-modbus` depends on `runtime-core` and `protocol-modbus`
- `runtime-app` selects a protocol binding for each configured source/listener
- `runtime-smoke-tests` combines TCP ingress and protocol bindings for
  repository-only cross-module verification

Not allowed:

- adding SDK protocol modules to `runtime-core`
- adding Netty, serial-port, UDP, Spring, Kafka, MQTT, HTTP, database, Redis, or
  observability exporter dependencies to `runtime-core`
- adding Netty or app dependencies to `runtime-protocol-*`
- changing `protocol-sdk` to depend on `protocol-runtime`

## `0.5.0` Adapter Boundary

The `0.5.0` line opens the runtime adapter productionization path. HTTP, Kafka,
and MQTT support should be designed as adapter modules first, not as
`runtime-core` features.

Allowed:

- the Maven reactor moves to `0.5.0-SNAPSHOT` after the published `0.4.0`
  release
- adapter boundary documents define source mapping, payload mapping,
  acknowledgement/response behavior, and failure routing before implementation
- adapter modules may own their respective client/server dependencies
- `runtime-app` may assemble adapter modules after their boundaries are stable
- tests may use fake brokers, embedded servers, or test-only fixtures inside
  adapter/test modules

Not allowed:

- adding Spring, Kafka, MQTT, HTTP, database, Redis, or observability exporter
  dependencies to `runtime-core`
- adding adapter dependencies to `protocol-sdk`
- adding adapter dependencies to `runtime-protocol-*`
- mixing downstream sink delivery into ingress adapters without an explicit
  sink boundary
- making broker acknowledgement, HTTP response status, MQTT reconnect policy,
  or topic/offset handling a `runtime-core` concern

## `0.6.0` HTTP App Assembly Boundary

The `0.6.0` line keeps the adapter model from `0.5.0` and focuses on making
HTTP ingress usable from the standalone runtime app.

Allowed:

- the Maven reactor moves to `0.6.0-SNAPSHOT` after the published `0.5.0`
  release
- `runtime-ingress-http` owns HTTP request handling, response policy, payload
  limits, source mapping, and adapter lifecycle
- `runtime-app` owns HTTP listener configuration and combines HTTP ingress,
  runtime protocol bindings, and app sinks
- HTTP app configuration supports named listeners with host, port, path,
  source reference, source id mode, source id header, payload limit, response
  mode, backlog, and worker threads
- HTTP listener status belongs to the app status snapshot and formatter, not
  to `runtime-core`
- tests may verify HTTP end-to-end behavior in app or smoke-test modules

Not allowed:

- adding HTTP, Kafka, MQTT, Spring, database, Redis, or observability exporter
  dependencies to `runtime-core`
- adding HTTP ingress or app dependencies to `runtime-protocol-*`
- introducing Kafka or MQTT client dependencies before their dedicated
  implementation modules are opened
- moving HTTP response policy, Kafka offset policy, or MQTT reconnect policy
  into `runtime-core`

## `0.7.0` Kafka Ingress Boundary

The `0.7.0` line opens the Kafka ingress implementation path while preserving
the adapter model from the previous releases.

Allowed:

- the Maven reactor moves to `0.7.0-SNAPSHOT` after the published `0.6.0`
  release
- `runtime-ingress-kafka` owns Kafka client dependencies, consumer
  configuration, source id resolution, record-to-envelope mapping, Kafka
  attributes, polling source lifecycle, backpressure result mapping, and
  commit-mode decisions
- `runtime-app` may depend on `runtime-ingress-kafka` for Kafka consumer
  configuration, source/protocol binding, status reporting, and standalone
  collector assembly
- Kafka attributes stay in `IngressEnvelope.attributes()` and do not become
  `runtime-core` fields
- tests may construct Kafka `ConsumerRecord` fixtures without requiring a live
  broker in normal `mvn verify`

Not allowed:

- adding Kafka, MQTT, Spring, database, Redis, or observability exporter
  dependencies to `runtime-core`
- adding Kafka ingress or app dependencies to `runtime-protocol-*`
- changing `protocol-sdk` to depend on `protocol-runtime`
- moving Kafka offset policy, broker retry policy, or downstream sink delivery
  into `runtime-core`

## `0.8.0` MQTT Ingress Boundary

The `0.8.0` line opens the MQTT ingress implementation path while preserving the
adapter model from the previous releases.

Allowed:

- the Maven reactor moves to `0.8.0-SNAPSHOT` after the published `0.7.0`
  release
- `runtime-ingress-mqtt` owns MQTT client dependencies, client configuration,
  source id resolution, message-to-envelope mapping, MQTT attributes, client
  lifecycle, backpressure result mapping, and acknowledgement posture
- `runtime-app` may depend on `runtime-ingress-mqtt` for MQTT client
  configuration, source/protocol binding, status reporting, and standalone
  collector assembly
- MQTT attributes stay in `IngressEnvelope.attributes()` and do not become
  `runtime-core` fields
- tests may construct MQTT message fixtures without requiring a live broker in
  normal `mvn verify`

Not allowed:

- adding MQTT, Kafka, HTTP, Spring, database, Redis, or observability exporter
  dependencies to `runtime-core`
- adding MQTT ingress or app dependencies to `runtime-protocol-*`
- changing `protocol-sdk` to depend on `protocol-runtime`
- moving MQTT acknowledgement policy, reconnect/session policy, or downstream
  sink delivery into `runtime-core`

## `0.9.0` Sink And Operations Boundary

The `0.9.0` line starts after the published `0.8.0` MQTT runtime-app release.
Its boundary is downstream delivery and operational hardening after TCP, HTTP,
Kafka, and MQTT ingress baselines are available.

`0.9.0` has since been published as the sink and operations hardening release.

Allowed:

- the Maven reactor moves to `0.9.0-SNAPSHOT` after the published `0.8.0`
  release
- `runtime-app` owns app-level sink configuration, status output, lifecycle
  reporting, and operator-facing examples
- app-local file sink status may report output path, open state, active byte
  count, retained history count, in-process rotation count, and rotation limits
- app-local sink-failure backpressure may reject later ingress payloads before
  parsing after a configured sink failure threshold is reached
- dedicated `runtime-sink-*` modules may own downstream delivery dependencies
  after their contracts are explicit
- ingress adapters route accepted records through runtime sinks rather than
  directly depending on downstream delivery systems
- sink failures and parse failures can be isolated and reported without moving
  sink-specific policy into parser bindings

Not allowed:

- adding Spring, Netty, Kafka, MQTT, HTTP, database, Redis, object storage, or
  observability exporter dependencies to `runtime-core`
- moving Kafka producer, MQTT publisher, HTTP management, database, Redis,
  retry-store, or metrics-exporter dependencies into ingress adapters unless
  that adapter is explicitly responsible for the matching external boundary
- changing `protocol-sdk` to depend on `protocol-runtime`
- moving sink delivery, broker publishing, or storage retry policy into
  `runtime-protocol-*`

## `0.11.0` Development Boundary

The Maven reactor is now open at `0.11.0-SNAPSHOT` after the published
`0.10.0` health and status release. The current boundary is the first
standalone collector management plane.

Allowed:

- `runtime-app` may expose app-local JDK `HttpServer` management endpoints for
  `/health`, `/readiness`, and `/status`
- management configuration may live under `collector.management.*` and is owned
  by `runtime-app`
- management JSON may serialize existing `CollectorStatusSnapshot`,
  `CollectorHealthSnapshot`, runtime metrics, listener status, sink status, and
  backpressure configuration
- management port binding failures may fail collector startup and roll back
  already-started app listeners
- tests and standalone smoke scripts may query management endpoints on real
  localhost ports
- future non-JDK management dependencies must live in `runtime-app` or a
  dedicated management adapter module

Not allowed:

- adding Spring, Netty, Kafka, MQTT, HTTP, database, Redis, object storage, or
  observability exporter dependencies to `runtime-core`
- changing `protocol-sdk` to depend on `protocol-runtime`
- using `runtime-ingress-http` as the management API; it remains the protocol
  payload ingestion adapter
- moving management endpoint, metrics exporter, dashboard, durable health
  history, database, Redis, or broker-publishing policy into
  `runtime-protocol-*`

## `0.10.0` Health And Status Boundary

The `0.10.0` line started after the published `0.9.0` sink and operations
hardening release. It has since been published as the health checks and runtime
status productionization release for the standalone collector.

Allowed:

- the Maven reactor moved to `0.10.0-SNAPSHOT` after the published `0.9.0`
  release, then the release branch fixed it at `0.10.0`
- `runtime-app` owns app-level health/readiness calculation, degraded-state
  mapping, status formatting, failure summaries, and operator-facing examples
- ingress adapters may expose app-consumable status evidence needed by
  runtime-app health calculations without moving management concerns into the
  adapters
- tests may prove healthy, degraded, failed, and stopped runtime states without
  requiring external observability systems

Not allowed:

- adding Spring, Netty, Kafka, MQTT, HTTP, database, Redis, object storage, or
  observability exporter dependencies to `runtime-core`
- moving HTTP management endpoints, metrics exporters, dashboards, durable
  health history, database, Redis, or broker-publishing dependencies into
  `runtime-core`
- changing `protocol-sdk` to depend on `protocol-runtime`
- moving health endpoint, management API, or exporter dependencies into
  `runtime-protocol-*`
