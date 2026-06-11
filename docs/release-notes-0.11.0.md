# Protocol Runtime 0.11.0 Release Notes

`0.11.0` is the standalone collector management-plane baseline release.

The `0.11.0` release was tagged as `v0.11.0`, uploaded in Central deployment
`ad3dcf19-2aa1-4b02-9a3e-2215043274f1`, published to Maven Central, verified
from an isolated local Maven repository, and published as a GitHub Release.

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
- Adds a `0.11.0` release-readiness audit covering management boundary,
  module policy, verification commands, and release branch evidence.
- Preserves `runtime-core` as a dependency-light contract module with no
  Spring, Netty, Kafka, MQTT, HTTP, database, Redis, object storage, or
  observability exporter dependencies.

## Scope

`0.11.0` focuses on the first app-local management surface for standalone
collector operation. It builds on the `0.10.0` health/readiness snapshot model
and exposes that evidence through a separate management port.

## Dependency Policy

`runtime-core` remains free of transport, broker, storage, database, framework,
health endpoint, management API, dashboard, and exporter dependencies.

The management endpoint is implemented with JDK `HttpServer` inside
`runtime-app`. Future management adapters may move to a dedicated app/adapter
module, but those dependencies must still stay out of `runtime-core`,
`runtime-protocol-*`, and `protocol-sdk`.

## Verification

The release passed:

- `git diff --check`
- `mvn -q verify`
- standalone TCP collector smoke, including management HTTP checks
- standalone HTTP collector smoke, including management HTTP checks
- dependency boundary checks proving new dependencies do not enter
  `runtime-core`, `runtime-protocol-*`, or `protocol-sdk`
- signed Central dry run with publishing disabled
- real Maven Central upload and public Central resolution checks, including
  `io.github.qbsstg:runtime-app:0.11.0:jar:standalone`

The detailed plan is tracked in [`roadmap-0.11.0.md`](roadmap-0.11.0.md).
The release-readiness audit is tracked in
[`release-readiness-0.11.0.md`](release-readiness-0.11.0.md).

## Publication

- Tag: `v0.11.0`
- Central deployment: `ad3dcf19-2aa1-4b02-9a3e-2215043274f1`
- Central state: `PUBLISHED`
- GitHub Release: <https://github.com/Qbsstg/protocol-runtime/releases/tag/v0.11.0>
