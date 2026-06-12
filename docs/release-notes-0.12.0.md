# Protocol Runtime 0.12.0 Release Notes

Release notes draft for the `0.12.0` runtime development line.

The Maven reactor is open at `0.12.0-SNAPSHOT` after the published `0.11.0`
management-plane baseline. No `v0.12.0` tag has been created, and no real Maven
Central upload is part of this baseline PR.

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

The release must pass before publication:

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

The detailed plan is tracked in [`roadmap-0.12.0.md`](roadmap-0.12.0.md).

## Publication

- Tag: not created
- Central deployment: not started
- Central state: not published
- GitHub Release: not created
