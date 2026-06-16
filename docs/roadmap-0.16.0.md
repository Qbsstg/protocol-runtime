# Protocol Runtime 0.16.0 Roadmap

`0.16.0` starts after the published `0.15.0` standalone collector
distribution package productionization release. The release target is
production runtime operations hardening for long-running standalone collector
deployments.

This planning line is intentionally operational and app-owned. It should make
the collector easier to run, inspect, diagnose, and recover in real production
deployments without adding a runtime supervisor, service manager, external
observability exporter, database, Redis, Spring, or reverse dependency into
`protocol-sdk`.

## Goals

- Keep `runtime-core` free of Spring, Netty, Kafka, MQTT, HTTP, database,
  Redis, observability exporter, access-control, request-logging, deployment
  wrapper, service-manager, filesystem-layout, distribution-packaging,
  checksum/signing, installer, runtime-supervisor, and operations-agent
  dependencies.
- Keep `protocol-sdk` parser-only and preserve the dependency direction from
  `protocol-runtime` to published SDK artifacts.
- Treat production runtime operations as an app/docs/smoke concern owned by
  `runtime-app`, examples, docs, CI/smoke, or a future dedicated
  app/operations module.
- Define long-running stability expectations and the evidence that operators
  should collect during extended runtime smoke.
- Add a runtime self-check boundary for Java, package layout, writable
  directories, sink paths, listener bind readiness, management posture, and
  package integrity evidence.
- Define configuration hot-check behavior that detects changed configuration
  and reports validation results without hot-reloading a running collector.
- Strengthen logging and status evidence around startup, shutdown, listener
  bind, active connections, sink health, parse failures, backpressure,
  management requests, package verification, and release artifact provenance.
- Document failure recovery runbooks for common standalone collector incidents.
- Define long-running smoke and release artifact regression smoke coverage.
- Provide an operator runbook and a production issue diagnostics flow that can
  be followed without attaching a debugger or changing application code.

## Target Module Work

| Module | `0.16.0` target |
| --- | --- |
| `runtime-core` | Preserve the dependency-light contract surface; add no operations, supervisor, service-manager, external exporter, database, Redis, framework, installer, filesystem-layout, or package-management dependencies. |
| `runtime-app` | Own runtime self-check output, config hot-check reporting, status/log evidence, app-local diagnostics, failure recovery guidance integration points, and production-oriented operator commands if implementation starts. |
| `examples` and `docs` | Own operator runbook, production issue diagnostics flow, failure recovery manuals, long-running smoke instructions, release artifact regression smoke instructions, and troubleshooting updates. |
| CI/smoke | Own long-running smoke and release artifact regression smoke only as verification; do not turn smoke support into production dependencies. |
| `runtime-ingress-*` | Preserve published ingress behavior; expose app-consumable lifecycle/status evidence only when needed by runtime-app diagnostics. |
| `runtime-protocol-*` | Continue to parse payloads without transport, app, operations, supervisor, status-export, storage, or sink dependencies. |
| `runtime-smoke-tests` | Keep repository-only cross-module smoke coverage and avoid becoming an application dependency. |

## Planning Work

- Define a runtime self-check report shape covering Java version, command path,
  runtime version, package metadata, runtime directory writability, configured
  listeners, source mappings, sink configuration, management access posture,
  and package verification state.
- Define config hot-check semantics:
  - detect file timestamp/hash changes for configured property files
  - re-run validation on demand
  - report whether a restart is required
  - explicitly avoid hot-reloading live collectors in this line
- Define logging and status evidence that operators can correlate across:
  startup, stop, PID handling, listener bind, connection lifecycle, file sink
  rotation, parse failure routing, backpressure decisions, management access
  rejection, and package integrity verification.
- Define failure recovery runbooks for stale PID files, port conflicts, sink
  path failures, malformed config, management token errors, parse failures,
  backpressure saturation, package checksum mismatches, partial extraction,
  interrupted upgrades, and rollback validation.
- Define long-running smoke coverage that exercises a packaged collector over
  an extended runtime window and records status snapshots, sink evidence,
  management health/readiness, log markers, stop behavior, and package
  verification evidence.
- Define release artifact regression smoke that verifies published standalone
  jar and distribution packages remain runnable after Central publication.
- Define production issue diagnostics flow for collecting version output,
  config validation, self-check report, status export, management snapshots,
  logs, PID state, package verification output, and relevant artifact
  coordinates.

## Non-Goals

- Runtime supervisor, process manager, service manager, installer, package
  manager, daemon controller, or automatic restart implementation.
- Spring Boot, Micronaut, Quarkus, or other application framework adoption.
- Database, Redis, object storage, durable queue, service registry, external
  scheduler, or external observability exporter integration.
- Hot-reloading live collector configuration.
- Moving operations, diagnostics, self-check, recovery, package, deployment,
  or smoke policy into `runtime-core`, `runtime-protocol-*`, or `protocol-sdk`.
- Reusing `runtime-ingress-http` as a management API, deployment API, package
  API, upgrade API, operations API, or diagnostics API.
- Changing parser behavior inside `protocol-sdk`.

## Readiness Checklist

- [x] `0.15.0` release artifacts are published and verified from Maven Central.
- [x] GitHub Release `v0.15.0` is published.
- [x] Maven reactor is opened at `0.16.0-SNAPSHOT` for development.
- [x] README and Chinese README describe the `0.16.0` production runtime
  operations planning line.
- [x] `docs/module-plan.md` and `docs/module-boundaries.md` describe the
  `0.16.0` planning boundary.
- [ ] runtime self-check boundary is designed.
- [ ] config hot-check without hot-reload is designed.
- [ ] logging and status evidence requirements are documented.
- [ ] failure recovery runbooks are drafted.
- [ ] long-running smoke is designed.
- [ ] release artifact regression smoke is designed.
- [ ] operator runbook and production issue diagnostics flow are drafted.
