# Protocol Runtime 0.11.0 Roadmap

`0.11.0` starts after the published `0.10.0` health and status release. The
release branch fixes the Maven reactor version at `0.11.0`.

The release target is a minimal runtime management plane for the standalone
collector. The management plane is app-owned and exposes local HTTP health,
readiness, and status snapshots without introducing Spring, external metrics
exporters, database, Redis, or broker dependencies.

## Goals

- Keep `runtime-core` free of Spring, Netty, Kafka, MQTT, HTTP, database,
  Redis, object storage, and observability exporter dependencies.
- Keep `protocol-sdk` parser-only and preserve the dependency direction from
  `protocol-runtime` to published SDK artifacts.
- Add an explicit management boundary in `runtime-app` that is separate from
  `runtime-ingress-http` data ingestion.
- Add management configuration for `enabled`, `host`, `port`, `healthPath`,
  `readinessPath`, and `statusPath`.
- Expose JDK-only HTTP management endpoints:
  - `/health`
  - `/readiness`
  - `/status`
- Return JSON snapshots with lifecycle, health, readiness, sources, listeners,
  sink, backpressure, runtime metrics, and failure counters.
- Cover real localhost port `0` binding, startup/shutdown behavior, port
  conflicts, failed startup rollback, and degraded-but-ready status.

## Target Module Work

| Module | `0.11.0` target |
| --- | --- |
| `runtime-core` | Preserve the dependency-light contract surface; add no framework, transport, broker, storage, database, Redis, HTTP management, or exporter dependencies. |
| `runtime-app` | Own the JDK `HttpServer` management endpoint, configuration, JSON status formatting, startup rollback, examples, and smoke coverage. |
| `runtime-ingress-http` | Remain a data-ingestion adapter for protocol payload POSTs; do not become the management endpoint. |
| `runtime-ingress-*` | Preserve published ingress behavior and expose app-consumable status only through existing app snapshots. |
| `runtime-protocol-*` | Continue to parse protocol payloads without transport, app, management endpoint, metrics exporter, or downstream sink dependencies. |
| `runtime-smoke-tests` | Preserve repository-only cross-module smoke coverage; management smoke can live in app tests and standalone scripts. |

## Progress

- Added `ManagementServerConfig` with default-disabled management settings and
  property parsing under `collector.management.*`.
- Added a JDK `HttpServer` based app-local management endpoint in
  `runtime-app`.
- Added JSON formatting for health, readiness, and full status snapshots
  without adding a JSON dependency.
- Added management lifecycle integration so bind failures mark the collector
  `FAILED` and roll back already-started collector listeners.
- Added focused tests for port `0`, health/readiness/status JSON, degraded
  parser-failure state, validation errors, and management port conflicts.
- Updated standalone example smoke scripts to verify management HTTP endpoints
  alongside record ingestion.

## Non-Goals

- Spring Boot or application framework adoption.
- Moving HTTP management concerns into `runtime-core`.
- Reusing `runtime-ingress-http` as the management endpoint.
- Prometheus/OpenTelemetry exporters, dashboards, durable health history,
  database, Redis, object storage, or broker publishing.
- Authentication, authorization, TLS, or remote multi-node control APIs.
- New parser behavior inside `protocol-sdk`.

## Readiness Checklist

- [x] README and Chinese README describe the `0.11.0` management-plane line.
- [x] `docs/module-plan.md` and `docs/module-boundaries.md` describe the
  management boundary.
- [x] management endpoints are covered by focused runtime-app tests.
- [x] standalone smoke scripts cover management HTTP endpoints.
- [x] `git diff --check` passes.
- [x] `mvn -q verify` passes.
- [x] standalone TCP and HTTP smoke scripts pass with management HTTP checks.
- [x] central-release dry run passes with publishing disabled.
- [x] dependency boundary checks prove new dependencies stay out of
  `runtime-core`, `runtime-protocol-*`, and `protocol-sdk`.
- [x] `docs/release-readiness-0.11.0.md` records the release branch gates and
  final pre-publication checks.
- [ ] GitHub CI passes before merge.
