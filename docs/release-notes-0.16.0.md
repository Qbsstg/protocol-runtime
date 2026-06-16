# Protocol Runtime 0.16.0 Release Notes

Release notes draft for the future `0.16.0` runtime release.

`0.16.0` follows the published `0.15.0` standalone collector distribution
package productionization release. The Maven reactor is open at
`0.16.0-SNAPSHOT` for the first production runtime operations baseline.

## Baseline Scope

`0.16.0` is a production runtime operations hardening release for standalone
collector deployments. The goal is to improve long-running
operability, diagnostics, and recovery evidence while keeping operational
concerns in `runtime-app`, examples, docs, CI/smoke, or a future dedicated
app/operations boundary.

Baseline areas:

- Runtime self-check output for Java version, package layout, writable runtime
  directories, sink paths, listener bind readiness, management endpoint
  posture, and package integrity evidence.
- Configuration hot-check behavior that detects changed config files and
  reports validation results without hot-reloading a live collector.
- Stronger logging and status evidence for startup, shutdown, listener bind,
  active connections, sink health, parse failures, backpressure decisions,
  management access, and package verification state.
- Failure recovery runbooks for stale PID files, port conflicts, sink path
  failures, malformed config, management token errors, parse failures,
  backpressure saturation, package verification failures, interrupted upgrades,
  and rollback validation.
- Long-running smoke and release artifact regression smoke coverage.
- Operator runbook and production issue diagnostics flow.

The detailed plan is tracked in [`roadmap-0.16.0.md`](roadmap-0.16.0.md).
Operational procedures are tracked in
[`operations-runbook.md`](operations-runbook.md) and
[`operations-runbook.zh-CN.md`](operations-runbook.zh-CN.md).

## Dependency Policy

`runtime-core` must remain free of transport, broker, storage, database,
framework, service-manager, shell-wrapper, deployment-wrapper,
distribution-packaging, filesystem-layout, checksum/signing, installer,
runtime-supervisor, access-control, request-logging, health endpoint,
management API, operations API, dashboard, and exporter dependencies.

`runtime-ingress-http` remains the protocol payload ingestion adapter. It must
not become the management endpoint, deployment endpoint, package endpoint,
upgrade endpoint, operations endpoint, or diagnostics endpoint.

`protocol-sdk` remains parser-only. Parser behavior changes belong in
`protocol-sdk` releases, not in `protocol-runtime` planning work.

## Verification Targets

Implementation and future release PRs should pass:

- `git diff --check`
- `mvn -q verify`
- central-release dry run with publishing disabled
- standalone TCP collector smoke
- standalone HTTP collector smoke
- management HTTP smoke
- distribution package smoke
- release artifact smoke
- runtime self-check and config hot-check coverage inside distribution and
  release artifact smoke
- long-running smoke
- release artifact regression smoke
- dependency boundary checks proving production operations work does not enter
  `runtime-core`, `runtime-protocol-*`, or `protocol-sdk`
- GitHub CI on implementation and release PRs

## Publication

- Tag: not created
- Central deployment: not started
- Central state: not published
- GitHub Release: not created
