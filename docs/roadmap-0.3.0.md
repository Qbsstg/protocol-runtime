# Protocol Runtime 0.3.0 Roadmap

`0.3.0` starts after the published `0.2.0` standalone collector baseline. The
focus is production hardening of `runtime-app` and its operating model, not
adding heavy downstream adapters to `runtime-core` or `protocol-sdk`.

## Primary Goal

Turn the single-source IEC104 TCP collector baseline into a clearer
production-oriented runtime app boundary with stronger configuration,
lifecycle, health, observability, sink, failure, and backpressure behavior.

## Included Scope

- Configuration validation:
  - reject missing required fields before opening network listeners
  - validate source id format, TCP ports, thread counts, sink selection, and
    file sink paths
  - report startup errors with operator-readable messages
  - expose a validation result model so callers can inspect errors before
    constructing the collector
- Multi-source and multi-listener planning:
  - model more than one configured source
  - allow multiple TCP listeners in the app config
  - keep source-specific attributes stable in `IngressEnvelope`
  - avoid changing `runtime-core` just to support app-level configuration shape
  - keep the existing single-source `collector.properties` shape compatible
- Collector lifecycle state:
  - expose app states such as configured, starting, running, stopping, stopped,
    and failed
  - make startup and shutdown outcomes inspectable in tests
  - preserve graceful shutdown behavior for active TCP sessions
- Health and runtime status:
  - provide a minimal status snapshot for listener state, active connections,
    parsed records, parse failures, and last failure
  - keep HTTP health endpoints deferred unless a dedicated adapter or app
    module owns the HTTP dependency
- Metrics and logging baseline:
  - define small app-level counters and gauges before choosing an exporter
  - use JDK logging in the baseline
  - keep Micrometer, Prometheus, OpenTelemetry, and similar dependencies
    deferred to separate observability modules
- File sink production behavior:
  - document and implement a rotation policy suitable for local operation
  - avoid unbounded single-file growth
  - keep durable storage and object storage sinks deferred
- Parse failure isolation:
  - ensure malformed frames are routed without stopping the collector
  - record enough source/session/payload context for diagnosis
  - keep failure handling policy explicit for drop, continue, and future
    quarantine behavior
- Backpressure policy enhancement:
  - move beyond the static `ACCEPT`, `RETRY_LATER`, and `DROP` smoke modes
  - add app-level thresholds for queue depth or sink pressure when the runtime
    has an internal buffer
  - keep transport-specific behavior inside the transport module
- Adapter boundary planning:
  - Kafka, MQTT, HTTP, database, Redis, and object storage dependencies remain
    outside `runtime-core`
  - runtime adapters should land as separate modules or app-owned integrations
  - `protocol-sdk` remains parser-only

## Explicitly Deferred

- Spring Boot or another application framework as the default runtime app.
- Kafka, MQTT, and HTTP production adapters.
- Database, Redis, durable queue, object storage, or distributed checkpointing.
- TLS certificate management.
- IEC104 command/session state machine policy.
- Formal metrics exporters and dashboards.
- Cluster scheduling, leader election, and high availability.

## Dependency Rules

- `runtime-core` remains free of Spring, Netty, Kafka, MQTT, HTTP, database,
  Redis, observability exporters, and protocol-specific bindings.
- Netty remains isolated to `runtime-ingress-tcp-netty` and intentional
  assembly/test modules.
- Future Kafka, MQTT, HTTP, database, Redis, and observability dependencies
  must be introduced only in dedicated adapter or app modules.
- `protocol-sdk` remains parser-only and must not depend on
  `protocol-runtime`.

## Usage Experience Gate

Before `0.3.0` release readiness, an operator should be able to:

1. Start a configured collector with validation errors reported before bind.
2. Configure more than one logical source or listener.
3. Inspect a local runtime status snapshot without attaching a debugger.
4. Confirm file sink output does not grow without a documented rotation policy.
5. Confirm parse failures are isolated from successful traffic.
6. Confirm backpressure behavior is visible and test-covered.

## Development Progress

- Configuration validation result model: started.
- Startup validation before network bind: started.
- Multi-source and multi-listener app configuration model: started.
- Legacy single-source `collector.properties` compatibility: preserved.
- Collector lifecycle/status snapshot: started.
- File rotation and richer backpressure policy: still planned.

## Release Readiness Gate

Before cutting the `0.3.0` release branch, the readiness audit should confirm:

- README and Chinese README describe the `0.3.0` production-hardening scope.
- `docs/release-notes-0.3.0.md` reflects the selected release scope.
- Module boundary docs still prove adapter dependencies stayed out of
  `runtime-core` and `protocol-sdk`.
- Local verification passes `git diff --check`, `mvn -q verify`, standalone
  smoke checks, and dependency boundary checks.
