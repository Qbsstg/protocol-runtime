# Protocol Runtime 0.14.0 Release Notes

Release notes for the published `0.14.0` runtime release.

`0.14.0` follows the published `0.13.0` production deployment governance
release. The release branch fixed the Maven reactor at `0.14.0`, tag
`v0.14.0` points at the release commit, and Maven Central deployment
`fc95f451-5a0d-4d3c-8743-6a78374fa6d9` reached `PUBLISHED`.

## Highlights

- Adds runtime package distribution governance for the standalone collector.
- Adds zip and tar.gz package assembly for a deployable runtime-app
  distribution.
- Adds package directory templates for `bin`, `conf`, `logs`, `data`, `run`,
  and `tmp`.
- Adds default local and production-style configuration templates for
  operator-owned deployments.
- Adds package `bin/protocol-runtime` and `bin/protocol-runtime-stop` scripts
  with JDK 21 checks, PID handling, runtime directory setup, duplicate-start
  diagnostics, validation, dry-run, status, and stop commands.
- Adds upgrade guidance for preserving config, logs, data, run, and temporary
  directories while replacing the runtime jar or package.
- Adds package smoke coverage for unpack, Java discovery, validate, dry-run,
  startup, management endpoints, status export, duplicate start, TCP port
  conflict, file sink output, and graceful stop.
- Adds default Java version troubleshooting so operators can distinguish system
  Java from the required JDK 21+ runtime.
- Preserves `runtime-core` as a dependency-light contract module with no Spring,
  Netty, Kafka, MQTT, HTTP, database, Redis, packaging, service-manager,
  filesystem-layout, access-control, request-logging, or external
  observability dependencies.

## Scope

`0.14.0` focuses on making the standalone collector easier to distribute and
install without turning `runtime-core`, protocol bindings, or parser SDK
modules into an application framework. Runtime package distribution governance
belongs in `runtime-app`, build configuration, examples, docs, or a future
dedicated app/distribution module.

The current baseline attaches:

- `runtime-app-0.14.0-distribution.zip`
- `runtime-app-0.14.0-distribution.tar.gz`

The package install and upgrade guide is maintained in
[`distribution-package.md`](distribution-package.md).

## Dependency Policy

`runtime-core` must remain free of transport, broker, storage, database,
framework, service-manager, shell-wrapper, deployment-wrapper,
distribution-packaging, filesystem-layout, access-control, request-logging,
health endpoint, management API, dashboard, and exporter dependencies.

`runtime-ingress-http` remains the protocol payload ingestion adapter. It must
not become the management endpoint, package distribution endpoint, or deployment
API.

## Verification Targets

The release must pass before publication:

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
- Release readiness audit in [`release-readiness-0.14.0.md`](release-readiness-0.14.0.md)

Current baseline local verification has passed:

- `git diff --check`
- `mvn -q verify`
- `JAVA_BIN=/opt/homebrew/opt/openjdk/bin/java sh examples/smoke-standalone.sh`
- `JAVA_BIN=/opt/homebrew/opt/openjdk/bin/java sh examples/smoke-standalone-http.sh`
- `JAVA_BIN=/opt/homebrew/opt/openjdk/bin/java sh examples/smoke-distribution-package.sh`
- dependency boundary checks for `runtime-core`, `runtime-protocol-*`, and
  `runtime-app`

The detailed plan is tracked in [`roadmap-0.14.0.md`](roadmap-0.14.0.md).

## Publication

- Tag: `v0.14.0`
- Central deployment: `fc95f451-5a0d-4d3c-8743-6a78374fa6d9`
- Central state: published and verified from isolated local Maven repositories
- GitHub Release: https://github.com/Qbsstg/protocol-runtime/releases/tag/v0.14.0
