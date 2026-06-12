# Protocol Runtime 0.12.0 Roadmap

`0.12.0` starts after the published `0.11.0` management-plane baseline. The
release branch fixes the Maven reactor at `0.12.0` for management-plane
productionization publication.

The release target is to harden the standalone collector management surface
while preserving the existing dependency boundaries. Management and observability
policy belongs in `runtime-app` or a dedicated app/adapter module; it must not
move into `runtime-core`, `runtime-protocol-*`, or `protocol-sdk`.

## Goals

- Keep `runtime-core` free of Spring, Netty, Kafka, MQTT, HTTP, database,
  Redis, object storage, access-control, request-logging, and observability
  exporter dependencies.
- Keep `protocol-sdk` parser-only and preserve the dependency direction from
  `protocol-runtime` to published SDK artifacts.
- Keep the management boundary separate from `runtime-ingress-http`; the latter
  remains a protocol-payload ingestion adapter.
- Define management security defaults that are safe for local standalone use
  and explicit when operators expose management endpoints beyond localhost.
- Add configurable access control for management HTTP endpoints, including
  disabled/open local mode and token-style restricted mode.
- Add management request logging that records method, path, status, duration,
  remote address, and rejection reason without logging secrets or payload bytes.
- Expand JSON metrics beyond the `0.11.0` baseline with request counts,
  response status counts, failure counters, listener/source health counts, and
  backpressure decision counts.
- Add bounded health status history snapshots so operators can inspect recent
  lifecycle, degraded, failed, and recovered transitions without adding a
  database or Redis dependency.
- Standardize management error responses for not found, method not allowed,
  malformed request, unauthorized, forbidden, and internal error paths.
- Add configuration examples and smoke coverage for healthy, degraded,
  unauthorized/forbidden, malformed request, request logging, metrics, and
  shutdown paths.

## Target Module Work

| Module | `0.12.0` target |
| --- | --- |
| `runtime-core` | Preserve the dependency-light contract surface; add no framework, transport, broker, storage, database, Redis, HTTP management, access-control, request-logging, or exporter dependencies. |
| `runtime-app` | Own management security, access control, request logging, metrics JSON, health history snapshots, error response formatting, examples, and standalone smoke coverage. |
| `runtime-ingress-http` | Remain the data-ingestion adapter for protocol payload POSTs; do not become the management endpoint or access-control owner. |
| `runtime-ingress-*` | Preserve published ingress behavior and expose only app-consumable status evidence needed by management snapshots. |
| `runtime-protocol-*` | Continue to parse protocol payloads without transport, app, management endpoint, access-control, metrics exporter, request logging, or downstream sink dependencies. |
| `runtime-smoke-tests` | Preserve repository-only cross-module smoke coverage; add management smoke only as integration verification. |

## Baseline Work

- Management security boundary documentation and default-localhost posture.
- Config model additions for management access control and request logging.
- Minimal token-style management access control in `runtime-app`.
- Management request log records with redaction rules for secrets.
- JSON metrics extension for management requests, runtime counters, and
  backpressure evidence.
- Bounded in-memory health history snapshot model inside `runtime-app`.
- Management error response formatter with stable JSON fields.
- Example properties files covering secured management endpoints.
- Standalone smoke coverage for secured and malformed management requests.

The implemented baseline uses only JDK `HttpServer`, JDK logging, and
app-local in-memory counters/history. It adds `local`, `open`, and `token`
management access modes, stable JSON errors, management request/status metrics,
and bounded health-history entries under `/status`.

## Non-Goals

- Spring Boot or application framework adoption.
- Moving HTTP management concerns into `runtime-core`.
- Reusing `runtime-ingress-http` as the management endpoint.
- Prometheus/OpenTelemetry exporters, dashboards, persistent health history,
  database, Redis, object storage, or broker publishing.
- TLS termination, multi-node control APIs, or role-based administration.
- New parser behavior inside `protocol-sdk`.

## Readiness Checklist

- [x] README and Chinese README describe the `0.12.0` management
  productionization line.
- [x] `docs/module-plan.md` and `docs/module-boundaries.md` describe the
  management security and observability boundaries.
- [x] management access-control configuration is covered by focused tests.
- [x] request logging and JSON metrics are covered without logging secrets.
- [x] health history snapshots are bounded and app-owned.
- [x] management error responses have stable JSON fields and tests.
- [x] standalone smoke scripts cover management HTTP success and failure paths.
- [x] `git diff --check` passes.
- [x] `mvn -q verify` passes.
- [x] dependency boundary checks prove new dependencies stay out of
  `runtime-core`, `runtime-protocol-*`, and `protocol-sdk`.
- [ ] GitHub CI passes before merge.

The release-readiness audit is tracked in
[`release-readiness-0.12.0.md`](release-readiness-0.12.0.md).
