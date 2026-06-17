# Protocol Runtime 0.16.0 Release Notes

Release notes for the `0.16.0` runtime release.

`0.16.0` follows the published `0.15.0` standalone collector distribution
package productionization release. `v0.16.0` points at the release commit, and
Maven Central deployment `82c4c0e3-211c-4993-877d-061ab50349bb` reached
`PUBLISHED`.

## Baseline Highlights

- Adds `bin/protocol-runtime self-check` for Java version, runtime version,
  package metadata, runtime directory writability, listener bind readiness,
  sink paths, management posture, and package verification evidence.
- Adds `bin/protocol-runtime hot-check` to detect configuration file changes,
  re-run validation, and report whether a restart is required without
  hot-reloading a live collector.
- Strengthens status and log evidence for startup, shutdown, PID handling,
  listener bind, active connections, sink health, parse failures,
  backpressure decisions, management requests, package verification, and
  version information.
- Adds English and Chinese operations runbooks for self-check, hot-check,
  failure recovery, long-running operation evidence, and production issue
  diagnostics.
- Adds long-running smoke coverage for packaged collector health/readiness,
  status snapshots, file sink evidence, log evidence, PID state, and graceful
  stop.
- Adds release artifact regression smoke for local or published standalone jar
  and distribution packages.
- Preserves `runtime-core` as a dependency-light contract module with no
  Spring, Netty, Kafka, MQTT, HTTP, database, Redis, operations-agent,
  runtime-supervisor, service-manager, filesystem-layout, package-management,
  checksum/signing, sink-adapter, or external observability dependencies.

## Scope

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

Implementation and release PRs must pass:

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
- GitHub CI on release PRs

## Release Branch Checks

The release branch checks are tracked in
[`release-readiness-0.16.0.md`](release-readiness-0.16.0.md). They must pass
before the release branch is merged to `main`.

## Publication

- Tag: `v0.16.0`
- Central deployment: `82c4c0e3-211c-4993-877d-061ab50349bb`
- Central state: `PUBLISHED`
- GitHub Release:
  <https://github.com/Qbsstg/protocol-runtime/releases/tag/v0.16.0>
