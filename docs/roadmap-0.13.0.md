# Protocol Runtime 0.13.0 Roadmap

`0.13.0` starts after the published `0.12.0` management-plane
productionization baseline. The Maven reactor is open at `0.13.0-SNAPSHOT`.

The release target is production deployment governance for the standalone
collector. The work should make a released `runtime-app` jar easier to run,
stop, validate, inspect, and troubleshoot in operator-owned environments while
preserving the existing module boundaries.

## Goals

- Keep `runtime-core` free of Spring, Netty, Kafka, MQTT, HTTP, database,
  Redis, observability exporter, access-control, request-logging, deployment
  wrapper, shell-wrapper, service-manager, and filesystem-layout dependencies.
- Keep `protocol-sdk` parser-only and preserve the dependency direction from
  `protocol-runtime` to published SDK artifacts.
- Treat deployment governance as an app/adapter concern owned by `runtime-app`
  or future dedicated deployment modules.
- Define configuration profile conventions for local, test, staging, and
  production-style runtime profiles without introducing a framework.
- Define runtime directory conventions for `conf`, `logs`, `data`, `run`, and
  temporary files.
- Define log file policy for standalone collector operation, including file
  locations, rotation expectations, and sensitive-value redaction posture.
- Add or document PID and stop-script behavior for local service management.
- Provide systemd and launchd examples as documentation/templates, not as
  `runtime-core` dependencies.
- Add a configuration validation CLI path and startup dry-run mode so operators
  can check config before binding ports or connecting to external systems.
- Define runtime status export boundaries for operator scripts without adding
  external observability exporters.
- Expand troubleshooting documentation and smoke coverage for startup,
  shutdown, dry-run, status export, and common deployment failures.

## Target Module Work

| Module | `0.13.0` target |
| --- | --- |
| `runtime-core` | Preserve the dependency-light contract surface; add no deployment, filesystem layout, shell-wrapper, service-manager, framework, database, Redis, or exporter dependencies. |
| `runtime-app` | Own configuration profiles, runtime directory conventions, PID/stop-script expectations, startup dry-run, validation CLI behavior, status export, deployment examples, and troubleshooting docs. |
| `runtime-ingress-*` | Preserve published ingress behavior; expose only app-consumable lifecycle/status evidence needed by deployment governance. |
| `runtime-protocol-*` | Continue to parse protocol payloads without transport, app, deployment, service-manager, status-export, or filesystem-layout dependencies. |
| `runtime-smoke-tests` | Add repository-only smoke coverage for deployable app behavior; do not make smoke fixtures a supported application dependency. |

## Baseline Work

- `collector.profile` supports profile-specific configuration files and a
  documented override order.
- Runtime directory conventions cover config, log, data, run, and temporary
  paths under `collector.runtime.dir`.
- Logging file policy examples keep secret-bearing config values out of logs.
- PID file and stop-script behavior are owned by `runtime-app`.
- systemd unit and launchd plist examples are provided as operator-owned
  templates.
- `runtime-app` CLI supports config validation and startup dry-run.
- Status export writes lifecycle, health, readiness, listener, sink,
  backpressure, management, and failure summaries as JSON.
- Troubleshooting docs cover invalid config, port conflicts, management access
  failures, sink path failures, parser failures, backpressure, and shutdown.
- Smoke coverage exercises config validation, dry-run, PID file creation,
  graceful stop, status export, and deployment example sanity.

## Non-Goals

- Spring Boot, Micronaut, Quarkus, or other application framework adoption.
- Database, Redis, durable scheduler, external lock, object storage, or service
  registry integration.
- Prometheus, OpenTelemetry, dashboard, or external observability exporter
  implementation.
- Installing systemd/launchd services from Maven or tests.
- Moving deployment wrappers, shell scripts, service-manager policy, runtime
  directories, log-file policy, or status-export policy into `runtime-core`.
- Changing parser behavior inside `protocol-sdk`.

## Readiness Checklist

- [x] README and Chinese README describe the `0.13.0` deployment governance
  line.
- [x] `docs/module-plan.md` and `docs/module-boundaries.md` describe the
  deployment-governance boundary.
- [x] configuration profile behavior is documented and covered by focused
  tests or smoke checks.
- [x] runtime directory conventions are documented with examples.
- [x] logging file policy and redaction posture are documented.
- [x] PID/stop-script behavior is documented and smoke covered where practical.
- [x] systemd and launchd examples are provided as operator-owned templates.
- [x] config validation CLI and startup dry-run are documented and covered.
- [x] status export output is documented and smoke covered.
- [x] troubleshooting docs cover common startup, shutdown, config, sink,
  management, and backpressure failures.
- [ ] `git diff --check` passes.
- [ ] `mvn -q verify` passes.
- [ ] dependency boundary checks prove new dependencies stay out of
  `runtime-core`, `runtime-protocol-*`, and `protocol-sdk`.
- [ ] GitHub CI passes before merge.
