# Protocol Runtime 0.11.0 Release Notes

Draft release notes for the `0.11.0` runtime release.

`0.11.0` is currently under development on the `0.11.0-SNAPSHOT` line. It has
not been tagged or published yet.

## Highlights

- Opens the standalone collector management-plane baseline after the published
  `0.10.0` health and status release.
- Adds app-owned management HTTP configuration under `collector.management.*`.
- Adds JDK-only `/health`, `/readiness`, and `/status` management endpoints in
  `runtime-app`.
- Returns JSON status snapshots containing lifecycle, health, readiness,
  sources, listeners, sink, backpressure, metrics, and failure counters.
- Keeps the management endpoint separate from `runtime-ingress-http`, which
  remains the protocol-payload HTTP ingestion adapter.
- Adds startup rollback behavior for management port bind failures.
- Adds tests and standalone smoke coverage for real localhost management
  endpoints and degraded status snapshots.
- Preserves `runtime-core` as a dependency-light contract module with no
  Spring, Netty, Kafka, MQTT, HTTP, database, Redis, object storage, or
  observability exporter dependencies.

## Scope

`0.11.0` focuses on the first app-local management surface for standalone
collector operation. It builds on the `0.10.0` health/readiness snapshot model
and exposes that evidence through a separate management port.

## Dependency Policy

`runtime-core` must remain free of transport, broker, storage, database,
framework, health endpoint, management API, dashboard, and exporter
dependencies.

The management endpoint is currently implemented with JDK `HttpServer` inside
`runtime-app`. Future management adapters may move to a dedicated app/adapter
module, but those dependencies must still stay out of `runtime-core`,
`runtime-protocol-*`, and `protocol-sdk`.

## Verification Targets

The release must pass before publication:

- `git diff --check`
- `mvn -q verify`
- `mvn -q -Pcentral-release -Dgpg.skip=true -Dcentral.skipPublishing=true deploy`
- standalone TCP collector smoke, including management HTTP checks
- standalone HTTP collector smoke, including management HTTP checks
- dependency boundary checks proving new dependencies do not enter
  `runtime-core`, `runtime-protocol-*`, or `protocol-sdk`
- GitHub CI on the release PR

The detailed plan is tracked in [`roadmap-0.11.0.md`](roadmap-0.11.0.md).

## Publication

- Tag: not created
- Central deployment: not started
- Central state: not published
- GitHub Release: not created
