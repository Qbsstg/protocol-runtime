# Protocol Runtime 0.12.0 Release Notes

Release notes for the published `0.12.0` runtime release.

The release was tagged as `v0.12.0`, uploaded in Central deployment
`eec1ab98-8186-4332-bd66-4819bef9c1ad`, published, and verified from Maven
Central with a fresh local Maven repository, including the `runtime-app`
standalone classifier.

## Baseline Highlights

- Productionizes the standalone collector management plane introduced in
  `0.11.0`.
- Defines management security boundaries and safe default exposure rules.
- Adds configurable management access control under the app-owned management
  configuration boundary.
- Adds management request logging with status, duration, remote address, and
  rejection reason while avoiding secret and payload logging.
- Expands JSON metrics for management requests, runtime counters, listener and
  source health, sink status, failure counters, and backpressure decisions.
- Adds bounded health status history snapshots for recent lifecycle, degraded,
  failed, and recovered transitions.
- Standardizes management error responses for not found, method not allowed,
  malformed request, unauthorized, forbidden, and internal error paths.
- Adds configuration examples and smoke coverage for healthy, degraded,
  unauthorized/forbidden, malformed request, metrics, request logging, and
  shutdown paths.
- Preserves `runtime-core` as a dependency-light contract module with no Spring,
  Netty, Kafka, MQTT, HTTP, database, Redis, object storage, access-control,
  request-logging, or observability exporter dependencies.

The first baseline keeps implementation inside `runtime-app`: access modes are
`local`, `open`, and `token`; request logging uses JDK logging; metrics and
health history are in-memory and bounded; and `/status` never emits management
tokens.

## Scope

`0.12.0` focuses on making the `0.11.0` app-local management surface safer and
more operationally useful without turning `runtime-core` or parser bindings into
an application framework. Management concerns remain owned by `runtime-app` or
future dedicated management/observability adapter modules.

## Dependency Policy

`runtime-core` must remain free of transport, broker, storage, database,
framework, access-control, request-logging, health endpoint, management API,
dashboard, and exporter dependencies.

`runtime-ingress-http` remains the protocol payload ingestion adapter. It must
not become the management endpoint or inherit management security policy.

## Verification Targets

The release passed before publication:

- `git diff --check`
- `mvn -q verify`
- central-release dry run with publishing disabled
- standalone TCP collector smoke, including management HTTP checks
- standalone HTTP collector smoke, including management HTTP checks
- management HTTP smoke for success, unauthorized/forbidden, malformed request,
  request logging, metrics, and shutdown paths
- dependency boundary checks proving new dependencies do not enter
  `runtime-core`, `runtime-protocol-*`, or `protocol-sdk`
- GitHub CI on the release PR

The release-readiness audit is tracked in
[`release-readiness-0.12.0.md`](release-readiness-0.12.0.md).

The detailed plan is tracked in [`roadmap-0.12.0.md`](roadmap-0.12.0.md).

## Publication

- Tag: `v0.12.0`
- Central deployment: `eec1ab98-8186-4332-bd66-4819bef9c1ad`
- Central state: `PUBLISHED`
- Public Maven Central verification: passed, including
  `io.github.qbsstg:runtime-app:0.12.0:jar:standalone`
- GitHub Release:
  <https://github.com/Qbsstg/protocol-runtime/releases/tag/v0.12.0>
