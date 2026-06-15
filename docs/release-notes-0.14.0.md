# Protocol Runtime 0.14.0 Release Notes

Release notes draft for the future `0.14.0` runtime release.

The `0.14.0-SNAPSHOT` line opens after the published `0.13.0` production
deployment governance release. No `v0.14.0` tag has been created, and no real
Maven Central upload is part of this planning line.

## Planned Highlights

- Plan runtime package distribution governance for the standalone collector.
- Define zip/tar package boundaries for a deployable runtime-app distribution.
- Define package directory templates for `bin`, `conf`, `logs`, `data`, `run`,
  and `tmp`.
- Provide default configuration templates for operator-owned deployments.
- Enhance startup and stop script expectations around JDK 21 checks, PID files,
  runtime directories, stale PID handling, and repeated stop behavior.
- Add upgrade guidance for preserving config, logs, data, run, and temporary
  directories while replacing the runtime jar or package.
- Add package smoke coverage for unpack, validate, dry-run, startup,
  management endpoints, status export, and graceful stop.
- Add default Java version troubleshooting so operators can distinguish system
  Java from the required JDK 21+ runtime.
- Preserve `runtime-core` as a dependency-light contract module with no Spring,
  Netty, Kafka, MQTT, HTTP, database, Redis, packaging, service-manager,
  filesystem-layout, access-control, request-logging, or external
  observability dependencies.

## Planned Scope

`0.14.0` focuses on making the standalone collector easier to distribute and
install without turning `runtime-core`, protocol bindings, or parser SDK
modules into an application framework. Runtime package distribution governance
belongs in `runtime-app`, build configuration, examples, docs, or a future
dedicated app/distribution module.

## Dependency Policy

`runtime-core` must remain free of transport, broker, storage, database,
framework, service-manager, shell-wrapper, deployment-wrapper,
distribution-packaging, filesystem-layout, access-control, request-logging,
health endpoint, management API, dashboard, and exporter dependencies.

`runtime-ingress-http` remains the protocol payload ingestion adapter. It must
not become the management endpoint, package distribution endpoint, or deployment
API.

## Verification Targets

The future release should pass before publication:

- `git diff --check`
- `mvn -q verify`
- central-release dry run with publishing disabled
- standalone TCP collector smoke
- standalone HTTP collector smoke
- management HTTP smoke
- package distribution smoke
- dependency boundary checks proving packaging work does not enter
  `runtime-core`, `runtime-protocol-*`, or `protocol-sdk`
- GitHub CI on the release PR
- release readiness audit for `0.14.0`

The detailed plan is tracked in [`roadmap-0.14.0.md`](roadmap-0.14.0.md).

## Publication

- Tag: not created
- Central deployment: not started
- Central state: not published
- GitHub Release: not created
