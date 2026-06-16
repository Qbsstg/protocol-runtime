# Protocol Runtime 0.15.0 Roadmap

`0.15.0` starts after the published `0.14.0` runtime package distribution
governance release. The release target is distribution package
productionization for the standalone collector.

The first `0.15.0-SNAPSHOT` baseline is planning only. Implementation work
should happen after the package integrity, script compatibility, migration,
rollback, offline deployment, version metadata, release artifact smoke, and
operator troubleshooting boundaries are explicit.

## Goals

- Keep `runtime-core` free of Spring, Netty, Kafka, MQTT, HTTP, database,
  Redis, observability exporter, access-control, request-logging, deployment
  wrapper, service-manager, filesystem-layout, distribution-packaging,
  checksum/signing, installer, and package integrity dependencies.
- Keep `protocol-sdk` parser-only and preserve the dependency direction from
  `protocol-runtime` to published SDK artifacts.
- Treat package productionization as an app/build/docs concern owned by
  `runtime-app`, build configuration, examples, docs, or a future dedicated
  app/distribution module.
- Define release artifact integrity checks for standalone jar, zip, and tar.gz
  outputs.
- Define checksum and signature policy for package artifacts without adding
  signing or checksum dependencies to `runtime-core`.
- Improve cross-platform script compatibility expectations for POSIX shells and
  document operator-owned Windows usage paths.
- Provide configuration migration notes for package upgrades.
- Define an upgrade rollback strategy for failed package replacement.
- Add offline deployment guidance for servers without direct Maven Central
  access.
- Define package-embedded version information for support diagnostics and smoke
  checks.
- Plan release artifact smoke coverage that validates published artifacts, not
  only local build outputs.
- Expand operator troubleshooting around install, upgrade, rollback, Java
  discovery, PID files, status checks, package integrity, and offline installs.

## Target Module Work

| Module | `0.15.0` target |
| --- | --- |
| `runtime-core` | Preserve the dependency-light contract surface; add no packaging, checksum/signing, installer, filesystem-layout, service-manager, framework, database, Redis, or exporter dependencies. |
| `runtime-app` | Continue to own package-facing runtime behavior, package metadata exposure, local validation entry points, and operator-visible diagnostics. |
| Build configuration | May own checksum/signature attachment policy, version metadata generation, published artifact verification, and package artifact smoke wiring after the boundary is explicit. |
| `examples` and `docs` | Own migration notes, rollback guidance, offline deployment examples, cross-platform script notes, package integrity procedures, and troubleshooting. |
| `runtime-ingress-*` | Preserve published ingress behavior; do not own package integrity, install, upgrade, rollback, or deployment API behavior. |
| `runtime-protocol-*` | Continue to parse payloads without transport, app, packaging, checksum/signing, service-manager, filesystem-layout, status-export, or sink dependencies. |
| `runtime-smoke-tests` | Keep repository-only cross-module checks; release artifact smoke remains verification and does not become a supported dependency surface. |

## Baseline Planning Work

- Package integrity policy covers standalone jar, distribution zip, distribution
  tar.gz, checksums, signatures, and expected verification commands.
- Cross-platform script guidance identifies what is guaranteed for POSIX shells
  and what is operator-owned for Windows deployments.
- Configuration migration notes explain how to compare old and new
  `collector.properties` templates and preserve operator-owned overrides.
- Upgrade rollback guidance covers symlink rollback, package directory
  preservation, PID safety, log preservation, status checks, and smoke
  validation after rollback.
- Offline deployment guidance explains how to move published artifacts,
  checksums, signatures, configs, and docs onto restricted servers.
- Package version metadata is planned so operators can inspect the package and
  running process version without unpacking implementation internals.
- Release artifact smoke is planned to verify Maven Central-published package
  artifacts from an isolated local repository or downloaded package location.
- Troubleshooting expands to cover checksum mismatch, missing signatures,
  partial package extraction, incompatible shell behavior, stale symlink,
  failed rollback, offline artifact gaps, and version mismatch.

## Non-Goals

- Spring Boot, Micronaut, Quarkus, or other application framework adoption.
- Database, Redis, durable scheduler, external lock, object storage, service
  registry, external observability exporter, installer daemon, package manager,
  or service manager integration.
- Moving package integrity, signing, version metadata, migration, rollback,
  offline deployment, install guides, or release artifact smoke policy into
  `runtime-core`.
- Reusing `runtime-ingress-http` as a management API, package distribution API,
  deployment API, upgrade API, or status export API.
- Changing parser behavior inside `protocol-sdk`.
- Implementing the full package productionization surface before the boundary
  design is reviewed.

## Readiness Checklist

- [x] `0.14.0` release artifacts are published and verified from Maven Central.
- [x] GitHub Release `v0.14.0` is published.
- [x] Maven reactor is opened at `0.15.0-SNAPSHOT`.
- [x] README and Chinese README describe the `0.15.0` distribution package
  productionization planning line.
- [x] `docs/module-plan.md` and `docs/module-boundaries.md` describe the
  `0.15.0` planning boundary.
- [ ] package integrity policy is designed.
- [ ] checksum/signature policy is designed.
- [ ] cross-platform script compatibility guidance is designed.
- [ ] configuration migration notes are designed.
- [ ] upgrade rollback strategy is designed.
- [ ] offline deployment guidance is designed.
- [ ] package version metadata boundary is designed.
- [ ] release artifact smoke design is complete.
- [ ] operator troubleshooting additions are designed.
- [ ] implementation PRs keep `runtime-core`, `runtime-protocol-*`, and
  `protocol-sdk` free of packaging, checksum/signing, installer, service
  manager, database, Redis, and exporter dependencies.
