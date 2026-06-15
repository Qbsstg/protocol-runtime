# Protocol Runtime 0.13.0 Release Notes

Release notes for the published `0.13.0` runtime release.

`0.13.0` was published after the `0.12.0` management-plane productionization
release. The release branch fixed the Maven reactor at `0.13.0`, tag
`v0.13.0` points at the release commit, and Maven Central deployment
`6bd50b51-e4af-4774-b1fa-6a120e7f41f6` reached `PUBLISHED`.

## Highlights

- Adds production deployment governance for the standalone collector.
- Adds configuration profiles and profile-specific override behavior.
- Defines runtime directory conventions for `conf`, `logs`, `data`, `run`, and
  temporary files.
- Documents standalone log file policy and sensitive-value redaction posture.
- Adds PID and stop behavior for local service management.
- Provides systemd and launchd examples as operator-owned deployment templates.
- Adds a configuration validation CLI path and startup dry-run behavior before
  binding ports or connecting to external systems.
- Adds app-owned runtime status export for operator scripts.
- Expands troubleshooting documentation for startup, shutdown, config, port,
  management, sink, parser, and backpressure failures.
- Expands standalone smoke scripts to cover validate, dry-run, PID file
  creation, status export, management checks, and graceful stop.
- Preserve `runtime-core` as a dependency-light contract module with no Spring,
  Netty, Kafka, MQTT, HTTP, database, Redis, deployment-wrapper, service-manager,
  access-control, request-logging, or external observability dependencies.

## Scope

`0.13.0` focuses on making the standalone collector easier to deploy and operate
without turning `runtime-core`, protocol bindings, or parser SDK modules into an
application framework. Deployment governance remains owned by `runtime-app` or
future dedicated deployment/app adapter modules.

## Runtime-App Deployment Commands

```sh
java -jar runtime-app-0.13.0-standalone.jar --validate --config conf/collector.properties
java -jar runtime-app-0.13.0-standalone.jar --dry-run --config conf/collector.properties --status-export run/status.json
java -jar runtime-app-0.13.0-standalone.jar --stop --pid-file run/protocol-runtime.pid
```

Profile-specific config loading is deterministic: defaults, explicit config
files, optional sibling profile files such as `collector-production.properties`,
then command-line `--key=value` overrides.

## Dependency Policy

`runtime-core` must remain free of transport, broker, storage, database,
framework, service-manager, shell-wrapper, deployment-wrapper, access-control,
request-logging, health endpoint, management API, dashboard, and exporter
dependencies.

`runtime-ingress-http` remains the protocol payload ingestion adapter. It must
not become the management endpoint or own deployment governance.

## Verification Targets

The release must pass before publication:

- `git diff --check`
- `mvn -q verify`
- central-release dry run with publishing disabled
- standalone TCP collector smoke
- standalone HTTP collector smoke
- management HTTP smoke
- deployment governance smoke for validation, dry-run, startup failure,
  graceful shutdown, and status export
- dependency boundary checks proving new dependencies do not enter
  `runtime-core`, `runtime-protocol-*`, or `protocol-sdk`
- GitHub CI on the release PR
- Release readiness audit in [`release-readiness-0.13.0.md`](release-readiness-0.13.0.md)

The detailed plan is tracked in [`roadmap-0.13.0.md`](roadmap-0.13.0.md), and
operator-facing deployment details are tracked in
[`deployment-governance.md`](deployment-governance.md) and
[`deployment-governance.zh-CN.md`](deployment-governance.zh-CN.md).

## Publication

- Tag: `v0.13.0`
- Central deployment: `6bd50b51-e4af-4774-b1fa-6a120e7f41f6`
- Central state: published and verified from an isolated local Maven repository
- GitHub Release: https://github.com/Qbsstg/protocol-runtime/releases/tag/v0.13.0
