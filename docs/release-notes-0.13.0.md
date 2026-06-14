# Protocol Runtime 0.13.0 Release Notes

Release notes draft for the `0.13.0` runtime development line.

The Maven reactor is open at `0.13.0-SNAPSHOT` after the published `0.12.0`
management-plane productionization release. No `v0.13.0` tag has been created,
and no real Maven Central upload is part of the development line.

## Planned Highlights

- Define production deployment governance for the standalone collector.
- Document configuration profiles and profile-specific override behavior.
- Define runtime directory conventions for `conf`, `logs`, `data`, `run`, and
  temporary files.
- Document standalone log file policy and sensitive-value redaction posture.
- Add or document PID and stop-script behavior for local service management.
- Provide systemd and launchd examples as operator-owned deployment templates.
- Add a configuration validation CLI path and startup dry-run behavior before
  binding ports or connecting to external systems.
- Define app-owned runtime status export for operator scripts.
- Expand troubleshooting documentation for startup, shutdown, config, port,
  management, sink, parser, and backpressure failures.
- Preserve `runtime-core` as a dependency-light contract module with no Spring,
  Netty, Kafka, MQTT, HTTP, database, Redis, deployment-wrapper, service-manager,
  access-control, request-logging, or external observability dependencies.

## Scope

`0.13.0` focuses on making the standalone collector easier to deploy and operate
without turning `runtime-core`, protocol bindings, or parser SDK modules into an
application framework. Deployment governance remains owned by `runtime-app` or
future dedicated deployment/app adapter modules.

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

The detailed plan is tracked in [`roadmap-0.13.0.md`](roadmap-0.13.0.md).

## Publication

- Tag: not created
- Central deployment: not started
- Central state: not published
- GitHub Release: not created
