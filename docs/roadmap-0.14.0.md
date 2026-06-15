# Protocol Runtime 0.14.0 Roadmap

`0.14.0` starts after the published `0.13.0` production deployment
governance release. The Maven reactor is open at `0.14.0-SNAPSHOT`.

The release target is runtime package distribution governance for the
standalone collector. The work should make a released `runtime-app` easier to
package, unpack, inspect, install, upgrade, and smoke test on operator-owned
servers while preserving the existing module boundaries.

## Goals

- Keep `runtime-core` free of Spring, Netty, Kafka, MQTT, HTTP, database,
  Redis, observability exporter, access-control, request-logging, deployment
  wrapper, service-manager, filesystem-layout, and distribution-packaging
  dependencies.
- Keep `protocol-sdk` parser-only and preserve the dependency direction from
  `protocol-runtime` to published SDK artifacts.
- Treat distribution packaging as an app/build/docs concern owned by
  `runtime-app`, build configuration, examples, docs, or a future dedicated
  app/distribution module.
- Define zip and tar distribution package boundaries for the standalone
  collector without changing runtime-core contracts.
- Define package directory templates for `bin`, `conf`, `logs`, `data`, `run`,
  and `tmp`.
- Provide default configuration templates that operators can copy and edit
  before running validation or startup dry-run.
- Enhance start and stop script expectations around Java discovery, PID files,
  runtime directories, and repeated stop behavior.
- Add upgrade guidance for replacing the application jar and preserving
  operator-owned config, logs, data, run, and temporary directories.
- Add distribution package smoke coverage that can prove the package unpacks,
  validates config, starts, answers management endpoints, exports status, and
  stops cleanly.
- Add JDK 21 checks and default `java` troubleshooting guidance so operators
  can distinguish an old system Java from the required runtime Java.
- Provide an operator install guide for local package installation without
  installing system services from Maven or tests.

## Target Module Work

| Module | `0.14.0` target |
| --- | --- |
| `runtime-core` | Preserve the dependency-light contract surface; add no packaging, filesystem-layout, service-manager, framework, database, Redis, or exporter dependencies. |
| `runtime-app` | Own the standalone executable jar, package-facing configuration templates, script expectations, runtime directory assumptions, validation/dry-run/status-export behavior, and operator-facing install guidance. |
| Build configuration | May assemble zip/tar package artifacts from published app outputs and repository examples; must not make packaging dependencies visible to runtime-core or protocol bindings. |
| `examples` and `docs` | Own package layout templates, operator install guide, upgrade notes, default config examples, JDK 21 checks, and default Java troubleshooting. |
| `runtime-ingress-*` | Preserve published ingress behavior; do not own package layout, installation, service management, or deployment API behavior. |
| `runtime-protocol-*` | Continue to parse payloads without transport, app, packaging, service-manager, filesystem-layout, status-export, or sink dependencies. |
| `runtime-smoke-tests` | Keep repository-only cross-module checks; package smoke tests prove deployable package behavior without becoming supported application dependencies. |

## Baseline Work

- Distribution packages expose a predictable top-level layout with `bin`,
  `conf`, `logs`, `data`, `run`, and `tmp` directories.
- Package templates include default collector configuration and optional
  production-style examples.
- Startup scripts document and enforce JDK 21+ expectations before launching
  the collector.
- Stop scripts continue to use app-owned PID file behavior and handle repeated
  stop or stale PID scenarios predictably.
- Upgrade notes describe jar replacement, config preservation, log/data/run
  directory preservation, and smoke validation after upgrade.
- Package smoke verifies unpack, config validation, dry-run, startup,
  management health/readiness/status, status export, and graceful stop.
- Operator install guide covers local install layout, runtime directory
  ownership, JDK selection, config validation, startup, stop, and rollback.

## Non-Goals

- Spring Boot, Micronaut, Quarkus, or other application framework adoption.
- Database, Redis, durable scheduler, external lock, object storage, service
  registry, or external observability exporter integration.
- Installing systemd/launchd services from Maven or tests.
- Moving package layout, script policy, Java discovery, install guides, or
  distribution artifact generation into `runtime-core`.
- Reusing `runtime-ingress-http` as a management API or deployment API.
- Changing parser behavior inside `protocol-sdk`.

## Readiness Checklist

- [x] README and Chinese README describe the `0.14.0` distribution governance
  line.
- [x] `docs/module-plan.md` and `docs/module-boundaries.md` describe the
  package distribution boundary.
- [x] zip/tar package layout is documented with `bin`, `conf`, `logs`, `data`,
  `run`, and `tmp` directory templates.
- [x] default configuration templates are documented and smoke covered.
- [x] startup and stop script expectations cover JDK 21 checks, PID behavior,
  stale PID handling, and repeated stop behavior.
- [x] upgrade notes preserve operator-owned config, logs, data, run, and tmp
  directories.
- [x] package smoke proves unpack, validate, dry-run, startup, management
  status, status export, and graceful stop.
- [x] operator install guide covers local install, JDK selection, config
  validation, startup, stop, smoke, and rollback.
- [x] `git diff --check` passes.
- [x] `mvn -q verify` passes.
- [x] standalone TCP and HTTP smoke scripts continue to pass with JDK 21+.
- [x] distribution package smoke passes with JDK 21+.
- [x] dependency boundary checks prove new packaging work stays out of
  `runtime-core`, `runtime-protocol-*`, and `protocol-sdk`.
- [ ] GitHub CI passes before merge.
