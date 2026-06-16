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
| `runtime-protocol-iec101` | `0.4.0` runtime binding for `protocol-iec101` parser output. | Serial-session policy and richer IEC101 record mapping after the parser binding baseline is stable. |
| `runtime-protocol-iec103` | `0.4.0` runtime binding for `protocol-iec103` parser output. | Protection-event mapping and serial-session policy after the parser binding baseline is stable. |
| `runtime-protocol-modbus` | `0.4.0` runtime binding for `protocol-modbus` parser output. | Modbus TCP/UDP runtime policy after the parser binding baseline is stable. |
| `runtime-ingress-tcp-netty` | Provide the first Netty TCP ingress baseline: server bootstrap, port binding, per-connection `RuntimePipelineRunner` creation, active session registry, connection lifecycle events, `ByteBuf` to `IngressEnvelope`, source id resolution, session attributes, backpressure handling, exception routing, and dispatch to sinks. | IEC104 sessions, Modbus TCP sessions, reconnects, heartbeat policy, TLS, and durable retry queues. |
| `runtime-ingress-http` | `0.5.0` JDK `HttpServer` baseline for HTTP POST payloads, source mapping, request limits, response policy, and runtime backpressure behavior. | `0.6.0` runtime-app HTTP collector assembly, lifecycle hardening, richer response policy, and smoke coverage. |
| `runtime-ingress-kafka` | `0.7.0` Kafka `ConsumerRecord` baseline for source mapping, payload mapping, envelope attributes, polling lifecycle, backpressure result mapping, and commit-mode decisions. | Broker-backed integration tests, retry/dead-letter posture, and downstream Kafka sink boundaries. |
| `runtime-ingress-mqtt` | `0.8.0` MQTT message baseline for source mapping, payload mapping, envelope attributes, Paho client lifecycle, and backpressure result mapping. | Broker-backed integration tests, reconnect hardening, and downstream MQTT sink boundaries. |
| `runtime-app` | Provide the standalone collector assembly with property-based configuration, source id selection, app-level protocol selection, TCP/HTTP/Kafka/MQTT assembly, backpressure mode, logging/file/in-memory sinks, and app-owned management endpoints. | Service wrappers, TLS, reconnect policy, richer management security, and downstream sink adapters. |
| `runtime-smoke-tests` | Prove the first IEC104 over TCP runtime path with EmbeddedChannel and real localhost socket tests through `TcpNettyServer`, `TcpNettyIngressHandler`, `RuntimePipelineRunner`, `Iec104RuntimeBinding`, sinks, active sessions, and disconnect lifecycle. | More cross-module runtime paths after new ingress and protocol bindings land. |

## Deferred Modules

| Module | Reason deferred |
| --- | --- |
| `runtime-sink-file` | Future candidate; file delivery hardening belongs outside ingress adapters and can prove sink lifecycle, error routing, and rotation policy before broker-backed sinks. |
| `runtime-sink-kafka` | Future candidate; downstream delivery belongs outside ingress adapters and must not pull Kafka dependencies into `runtime-core`. |
| `runtime-adapter-testkit` | Future candidate; reusable adapter tests should stay test support and avoid production dependency leakage. |
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

## `0.16.0` Production Runtime Operations Posture

The `0.16.0` runtime line starts from the published `0.15.0` distribution
package productionization release. The goal is production runtime operations
hardening without moving self-check, diagnostics, recovery, smoke, supervisor,
service-manager, filesystem-layout, package management, or external
observability concerns into core contracts:

| Module | 0.16.0 planning goal |
| --- | --- |
| `runtime-core` | Stay dependency-light; add no Spring, Netty, Kafka, MQTT, HTTP, database, Redis, runtime-supervisor, service-manager, filesystem-layout, deployment wrapper, installer, package manager, access-control, request-logging, or observability exporter dependencies. |
| `runtime-app` | Own production runtime self-check, config hot-check reporting without hot-reload, app-local diagnostics, richer status/log evidence, and operator-facing command surfaces if implementation starts. |
| `examples` and `docs` | Own failure recovery runbooks, operator runbook, production issue diagnostics flow, long-running smoke guidance, and release artifact regression smoke guidance. |
| CI/smoke | Own long-running smoke and release artifact regression smoke as verification only; do not turn smoke fixtures into supported application dependencies. |
| `runtime-ingress-*` | Preserve ingress behavior; expose only app-consumable lifecycle/status evidence needed by diagnostics. |
| `runtime-protocol-*` | Continue to parse payloads without transport, app, operations, supervisor, storage, status-export, or sink dependencies. |
| `runtime-smoke-tests` | Keep repository-only cross-module verification and avoid becoming an application dependency. |

The first `0.16.0` planning baseline is documentation and boundary design. It
must not introduce Spring, database, Redis, external observability exporters,
runtime supervisors, service managers, installers, package managers, or reverse
dependencies into `protocol-sdk`.

## `0.15.0` Productionization Posture

The `0.15.0` runtime line started from the published `0.14.0` package
distribution governance release and has since been published as the
distribution package productionization release. The goal was distribution
package productionization without moving package integrity, signing, installer,
service-manager, filesystem-layout, deployment-wrapper, or external
observability concerns into core contracts:

| Module | 0.15.0 baseline goal |
| --- | --- |
| `runtime-core` | Stay dependency-light; add no Spring, Netty, Kafka, MQTT, HTTP, database, Redis, distribution-packaging, checksum/signing, installer, deployment wrapper, shell-wrapper, service-manager, filesystem-layout, access-control, request-logging, or observability exporter dependencies. |
| `runtime-app` | Own package-facing runtime behavior, package layout verification, archive checksum verification, embedded version metadata, and operator diagnostics. |
| Build configuration | Own package metadata generation and local `.sha256`/`.sha512` sidecar generation for standalone jar, distribution zip, and distribution tar.gz outputs. |
| `examples` and `docs` | Own release artifact smoke, configuration migration notes, offline deployment guidance, upgrade rollback strategy, cross-platform script notes, and operator troubleshooting updates. |
| `runtime-ingress-*` | Preserve ingress behavior; do not own package integrity, package upgrade, install, rollback, or deployment API behavior. |
| `runtime-protocol-*` | Continue to parse payloads without transport, app, packaging, checksum/signing, service-manager, filesystem-layout, status-export, or sink dependencies. |
| `runtime-smoke-tests` | Keep repository-only cross-module smoke coverage; release artifact smoke remains verification, not a supported dependency surface. |

The first `0.15.0` baseline keeps implementation in `runtime-app`, build
configuration, examples, and docs. It must not introduce Spring, database,
Redis, external observability exporters, checksum/signing dependencies in
`runtime-core`, or reverse dependencies into `protocol-sdk`.

## `0.14.0` Published Posture

The `0.14.0` runtime line starts from the published `0.13.0` production
deployment governance release and has since been published as the first runtime
package distribution governance release.

The goal is runtime package distribution governance for the standalone
collector without moving package layout, install, script, service-manager,
filesystem-layout, or observability dependencies into core contracts:

| Module | 0.14.0 goal |
| --- | --- |
| `runtime-core` | Stay dependency-light; add no Spring, Netty, Kafka, MQTT, HTTP, database, Redis, distribution-packaging, deployment wrapper, shell-wrapper, service-manager, filesystem-layout, access-control, request-logging, or observability exporter dependencies. |
| `runtime-app` | Own the executable standalone jar, package-facing config templates, script expectations, runtime directory assumptions, validate/dry-run/status-export behavior, JDK 21 checks, and operator install guidance. |
| Build configuration | May assemble zip/tar distribution artifacts from app outputs and examples; packaging work must not leak into runtime-core or protocol bindings. |
| `examples` and `docs` | Own package directory templates, default config templates, startup/stop script examples, upgrade notes, default Java troubleshooting, package smoke instructions, and operator install guide. |
| `runtime-ingress-*` | Preserve published ingress behavior and avoid owning package layout, installation, service management, or deployment API behavior. |
| `runtime-protocol-*` | Continue to parse payloads without transport, app, packaging, service-manager, filesystem-layout, status-export, or sink dependencies. |
| `runtime-smoke-tests` | Keep repository-only cross-module smoke coverage; add package smoke only as verification, not as a supported dependency surface. |

Any future non-JDK packaging, installer, service-wrapper, deployment, or
observability dependency must stay in `runtime-app`, build configuration, or a
dedicated app/distribution adapter module until its boundary is explicit.

The first `0.14.0` baseline implements package distribution through
`runtime-app` build configuration, package-owned `bin` scripts, config
templates, docs, and smoke coverage. It does not introduce Spring, database,
Redis, external observability exporters, service managers, distribution
dependencies in `runtime-core`, or reverse dependencies into `protocol-sdk`.

## `0.13.0` Published Posture

The `0.13.0` runtime line started from the published `0.12.0` management-plane
productionization release and has since been published as the production
deployment governance release.

The goal is production deployment governance for the standalone collector
without moving deployment, service-manager, filesystem-layout, or observability
dependencies into core contracts:

| Module | 0.13.0 goal |
| --- | --- |
| `runtime-core` | Stay dependency-light; add no Spring, Netty, Kafka, MQTT, HTTP, database, Redis, deployment wrapper, shell-wrapper, service-manager, filesystem-layout, access-control, request-logging, or observability exporter dependencies. |
| `runtime-app` | Own implemented configuration profiles, runtime directory conventions, log file policy, PID/stop behavior, systemd/launchd examples, config validation CLI, startup dry-run, status export, troubleshooting docs, and deployment smoke coverage. |
| `runtime-ingress-*` | Preserve published ingress behavior and expose only app-consumable lifecycle/status evidence needed by deployment governance. |
| `runtime-protocol-*` | Continue to parse payloads without transport, app, deployment, service-manager, filesystem-layout, status-export, or sink dependencies. |
| `runtime-smoke-tests` | Keep repository-only cross-module smoke coverage; add deployment-governance smoke only as verification, not as a supported dependency surface. |

Any future non-JDK deployment, observability, or service-wrapper dependency
must stay in `runtime-app` or a dedicated deployment/observability adapter
module until its boundary is explicit.

The first `0.13.0` baseline is documentation plus small app-owned operator
surfaces in `runtime-app`: profile-aware config loading, runtime directory
configuration, validate/dry-run CLI, PID stop handling, status export, examples,
and smoke coverage. It does not introduce Spring, database, Redis, external
observability exporters, or reverse dependencies into `protocol-sdk`.

## `0.12.0` Published Posture

The `0.12.0` runtime line starts from the published `0.11.0` management-plane
baseline and has since been published as the management-plane productionization
release.

The goal is management-plane productionization without moving management,
observability, storage, or framework dependencies into core contracts:

| Module | 0.12.0 goal |
| --- | --- |
| `runtime-core` | Stay dependency-light; add no Spring, Netty, Kafka, MQTT, HTTP, database, Redis, object storage, management endpoint, access-control, request-logging, or observability exporter dependencies. |
| `runtime-app` | Own management security defaults, configurable access control, request logging, JSON metrics expansion, health history snapshots, error response formatting, examples, and smoke coverage. |
| `runtime-ingress-http` | Remain the protocol payload HTTP ingestion adapter; do not own management security, status, or metrics endpoints. |
| `runtime-ingress-*` | Preserve published ingress behavior and expose only app-consumable status evidence needed by runtime-app. |
| `runtime-protocol-*` | Continue to parse payloads without transport, app, management, exporter, access-control, logging, or sink dependencies. |
| `runtime-smoke-tests` | Keep repository-only cross-module smoke coverage; add management HTTP smoke only as integration verification, not as a supported dependency surface. |

Any future non-JDK management or observability dependency must stay in
`runtime-app` or a dedicated management/observability adapter module until its
boundary is explicit.

The first `0.12.0` baseline keeps this work in `runtime-app` only. It uses the
existing JDK `HttpServer` management endpoint, JDK logging, app-local request
counters, bounded in-memory health history, and stable JSON error formatting.
No access-control or request-logging dependency is introduced into
`runtime-core` or protocol binding modules.

## `0.11.0` Published Posture

The `0.11.0` runtime line started after the published `0.10.0` health and
status release and has since been published as the first app-owned management
plane for standalone collectors.

The published scope is a minimal management plane for the standalone collector:

| Module | 0.11.0 goal |
| --- | --- |
| `runtime-core` | Stay dependency-light; add no Spring, Netty, Kafka, MQTT, HTTP, database, Redis, object storage, management endpoint, or observability exporter dependencies. |
| `runtime-app` | Add JDK `HttpServer` based management configuration and endpoints for `/health`, `/readiness`, and `/status`; expose JSON snapshots from existing app-local health/status models. |
| `runtime-ingress-http` | Remain the protocol payload HTTP ingestion adapter; do not own management endpoints. |
| `runtime-ingress-*` | Preserve published ingress behavior and keep adapter-specific dependencies isolated. |
| `runtime-protocol-*` | Continue to parse payloads without transport, app, management, exporter, or sink dependencies. |
| `runtime-smoke-tests` | Keep repository-only cross-module smoke coverage; management checks can remain app-level and standalone-script based. |

Any future non-JDK management dependency must stay in `runtime-app` or a
dedicated management adapter module until its boundary is explicit.

## `0.10.0` Published Posture

The `0.10.0` runtime line started from the published `0.9.0` sink and
operations hardening release and has since been published as the health and
status productionization release.

The published scope is health checks and runtime status productionization after
the TCP, HTTP, Kafka, MQTT, and sink-hardening baselines are all published:

| Module | 0.10.0 goal |
| --- | --- |
| `runtime-core` | Stay dependency-light; add no Spring, Netty, Kafka, MQTT, HTTP, database, Redis, object storage, or observability exporter dependencies unless a protocol-neutral contract is proven necessary. |
| `runtime-app` | Formalize app-owned health/readiness state, degraded-state calculation, status formatting, failure summaries, and operator examples. |
| `runtime-ingress-*` | Preserve published ingress behavior and expose only app-consumable status evidence needed by runtime-app health calculations. |
| `runtime-protocol-*` | Continue to parse protocol payloads without transport, app, health endpoint, metrics exporter, or downstream sink dependencies. |
| `runtime-smoke-tests` | Add repository-only smoke coverage for stable health/status paths that should not become supported application dependencies. |

`0.10.0` should not introduce Spring, database, Redis, object storage,
observability exporter, Kafka producer, MQTT publisher, HTTP management, or
dashboard dependencies into `runtime-core`. Those dependencies belong only in
dedicated adapter/app modules after their boundaries are explicit.

## `0.9.0` Development Posture

The `0.9.0` runtime line starts from the published `0.8.0` MQTT runtime-app
release and opens the Maven reactor at `0.9.0-SNAPSHOT`.

`0.9.0` has since been published as the sink and operations hardening release.

The goal is downstream sink and operations hardening after the TCP, HTTP,
Kafka, and MQTT ingress baselines are all published:

| Module | 0.9.0 goal |
| --- | --- |
| `runtime-core` | Stay dependency-light; add no Spring, Netty, Kafka, MQTT, HTTP, database, Redis, or observability exporter dependencies unless a protocol-neutral contract is proven necessary. |
| `runtime-app` | Harden app-owned sink configuration, sink lifecycle, file sink status output, parse-failure isolation, and sink-failure backpressure/failure routing across TCP, HTTP, Kafka, and MQTT collectors. |
| `runtime-sink-*` | Introduce downstream delivery modules only if the sink boundary is stable enough to avoid leaking broker, storage, retry, or operational dependencies into `runtime-core`. |
| `runtime-ingress-*` | Preserve published ingress behavior and route delivery concerns through sink contracts instead of coupling ingress adapters to downstream systems. |
| `runtime-protocol-*` | Continue to parse protocol payloads without transport, app, or downstream sink dependencies. |
| `runtime-smoke-tests` | Expand cross-module smoke coverage only for stable runtime paths that should remain repository-only verification. |

`0.9.0` should not introduce Spring, database, Redis, object storage,
observability exporter, Kafka producer, MQTT publisher, or HTTP management
dependencies into `runtime-core`. Those dependencies belong only in dedicated
adapter/app modules after their boundaries are explicit.

## `0.8.0` Development Posture

The `0.8.0` runtime line starts from the published `0.7.0` Kafka runtime-app
release and opens the Maven reactor at `0.8.0-SNAPSHOT`.

`0.8.0` has since been published as the MQTT ingress and runtime-app MQTT
collector assembly release.

The goal is the first MQTT ingress implementation baseline:

| Module | 0.8.0 goal |
| --- | --- |
| `runtime-core` | Stay dependency-light; add no MQTT, Kafka, HTTP, Spring, database, Redis, or observability exporter dependencies. |
| `runtime-ingress-mqtt` | Add MQTT client dependency in this module only; map MQTT payloads to `IngressEnvelope`; provide client lifecycle; cover configured/topic source resolution, attributes, retained/duplicate flags, backpressure results, and lifecycle decisions. |
| `runtime-app` | Add MQTT client properties, app-owned source/protocol binding, status snapshot, and standalone collector assembly through `runtime-ingress-mqtt` without moving MQTT APIs into `runtime-core`. |
| `runtime-protocol-*` | Reuse parser bindings for MQTT payloads without transport or app dependencies. |
| `runtime-ingress-kafka` | Preserve the published Kafka path and avoid coupling it to MQTT work. |
| `runtime-ingress-http` | Preserve the published HTTP path and avoid coupling it to MQTT work. |
| `runtime-smoke-tests` | Keep live-broker MQTT smoke coverage as follow-up; app-level fake-source tests cover normal verification. |

`0.8.0` should not introduce Spring, database, Redis, observability exporter,
serial-port, or UDP dependencies into `runtime-core`. MQTT dependencies are
allowed only in `runtime-ingress-mqtt`, runtime-app assembly, and test scopes.

## `0.7.0` Development Posture

The `0.7.0` runtime line starts from the published `0.6.0` HTTP runtime-app
release and opens the Maven reactor at `0.7.0-SNAPSHOT`.

The goal is the first Kafka ingress implementation baseline before MQTT client
dependencies are introduced:

| Module | 0.7.0 goal |
| --- | --- |
| `runtime-core` | Stay dependency-light; add no Kafka, MQTT, HTTP, Spring, database, Redis, or observability exporter dependencies. |
| `runtime-ingress-kafka` | Add Kafka client dependency in this module only; map `ConsumerRecord<byte[], byte[]>` to `IngressEnvelope`; provide polling source lifecycle; cover configured/header/topic/key source resolution, attributes, backpressure results, and commit-mode decisions. |
| `runtime-app` | Add Kafka consumer properties, app-owned source/protocol binding, status snapshot, and standalone collector assembly through `runtime-ingress-kafka` without moving Kafka APIs into `runtime-core`. |
| `runtime-protocol-*` | Reuse parser bindings for Kafka payloads without transport or app dependencies. |
| `runtime-ingress-http` | Preserve the published HTTP path and avoid coupling it to Kafka work. |
| `runtime-ingress-mqtt` | Remain design-only until the `0.8.0` implementation line opens. |
| `runtime-smoke-tests` | Keep live-broker Kafka smoke coverage as follow-up; app-level fake-source tests cover normal verification. |

`0.7.0` should not introduce Spring, MQTT, database, Redis, observability
exporter, serial-port, or UDP dependencies into `runtime-core`. Kafka
dependencies are allowed only in `runtime-ingress-kafka`, runtime-app assembly,
and test scopes.

## `0.6.0` Development Posture

The `0.6.0` runtime line starts from the published `0.5.0` adapter-boundary
release and opens the Maven reactor at `0.6.0-SNAPSHOT`.

The goal is HTTP ingress productionization and runtime-app HTTP collector
assembly before Kafka and MQTT client dependencies are introduced:

| Module | 0.6.0 goal |
| --- | --- |
| `runtime-core` | Stay dependency-light; add no HTTP, Kafka, MQTT, Spring, database, Redis, or observability exporter dependencies. |
| `runtime-ingress-http` | Harden the JDK `HttpServer` adapter baseline for lifecycle, source mapping, request limits, response policy, parse failure routing, and backpressure behavior. |
| `runtime-app` | Add HTTP listener configuration and app-owned assembly while preserving TCP collector defaults; HTTP-only configs do not open the legacy TCP listener. |
| `runtime-protocol-*` | Reuse parser bindings for HTTP payloads without transport or app dependencies. |
| `runtime-ingress-tcp-netty` | Preserve the TCP path and avoid coupling it to HTTP work. |
| `runtime-ingress-kafka` | Remain design-only until a dedicated implementation release opens. |
| `runtime-ingress-mqtt` | Remain design-only until a dedicated implementation release opens. |
| `runtime-smoke-tests` | Add repository-only HTTP end-to-end smoke coverage after app assembly lands. |

`0.6.0` should not introduce Spring, Kafka, MQTT, database, Redis,
observability exporter, serial-port, or UDP dependencies into `runtime-core`.
HTTP dependencies should remain JDK-only in `runtime-ingress-http` and
app-owned assembly code for this line. The app-level HTTP configuration covers
host, port, path, source reference, source id mode/header, payload limit,
response mode, backlog, and worker threads.

## `0.5.0` Development Posture

The `0.5.0` runtime line starts from the published `0.4.0` multi-protocol
runtime release and opens the Maven reactor at `0.5.0-SNAPSHOT`.

The goal is adapter boundary design for HTTP, Kafka, and MQTT ingestion before
heavy adapter dependencies are introduced:

| Module | 0.5.0 goal |
| --- | --- |
| `runtime-core` | Stay dependency-light; add no HTTP, Kafka, MQTT, Spring, database, Redis, or observability exporter dependencies. |
| `runtime-protocol-*` | Preserve parser-binding-only responsibilities and continue consuming published `protocol-sdk:0.7.0` parser artifacts. |
| `runtime-ingress-tcp-netty` | Preserve the existing TCP byte ingress baseline and avoid protocol SDK dependencies. |
| `runtime-app` | Remain the deployable assembly boundary; keep the `0.4.0` protocol selection and TCP configuration compatible. |
| `runtime-ingress-http` | Provide the first JDK `HttpServer` baseline for request limits, source mapping, payload mapping, response policy, and backpressure behavior without pulling third-party HTTP dependencies into core. |
| `runtime-ingress-kafka` | Documented topic/partition/offset attributes, commit timing, replay posture, parse failure routing, and backpressure behavior before implementation. |
| `runtime-ingress-mqtt` | Documented topic/source mapping, QoS posture, retained-message handling, reconnect/session ownership, and backpressure behavior before implementation. |
| `runtime-sink-*` | Keep downstream delivery separate from ingress and core parsing; introduce sink dependencies only in dedicated modules. |
| `runtime-smoke-tests` | Continue repository-only cross-module checks and add adapter smoke paths only after module boundaries are stable. |

`0.5.0` should not introduce Spring, Kafka, MQTT, HTTP, database, Redis,
observability exporter, serial-port, or UDP dependencies into `runtime-core`.
If HTTP, Kafka, or MQTT implementations start in this release line, they should
land in dedicated adapter modules and include dependency boundary tests.

## `0.4.0` Development Posture

The `0.4.0` runtime line starts from the published `0.3.0` standalone collector
hardening release and opens the Maven reactor at `0.4.0-SNAPSHOT`.

The goal is multi-protocol runtime expansion around published
`protocol-sdk:0.7.0` parser artifacts:

| Module | 0.4.0 goal |
| --- | --- |
| `runtime-core` | Stay dependency-light; add only protocol-neutral contracts if multi-protocol binding proves a shared need. |
| `runtime-protocol-iec104` | Preserve the existing IEC104 binding and compatibility path. |
| `runtime-protocol-iec101` | Add a runtime binding for `protocol-iec101:0.7.0` without transport or app dependencies. |
| `runtime-protocol-iec103` | Add a runtime binding for `protocol-iec103:0.7.0` without transport or app dependencies. |
| `runtime-protocol-modbus` | Add a runtime binding for `protocol-modbus:0.7.0` without transport or app dependencies. |
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
