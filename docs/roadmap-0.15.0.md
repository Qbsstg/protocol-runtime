# Protocol Runtime 0.15.0 Roadmap

`0.15.0` starts after the published `0.14.0` runtime package distribution
governance release. The release target is distribution package
productionization for the standalone collector.

The first `0.15.0-SNAPSHOT` implementation baseline adds package metadata,
checksum verification, release artifact smoke, upgrade migration notes,
rollback guidance, offline deployment guidance, script diagnostics, and
operator troubleshooting while keeping the work in `runtime-app`, build
configuration, examples, and docs.

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
- Provide release artifact integrity checks for standalone jar, zip, and tar.gz
  outputs.
- Provide checksum and signature policy for package artifacts without adding
  signing or checksum dependencies to `runtime-core`.
- Improve cross-platform script compatibility expectations for POSIX shells and
  document operator-owned Windows usage paths.
- Provide configuration migration notes for package upgrades.
- Provide an upgrade rollback strategy for failed package replacement.
- Add offline deployment guidance for servers without direct Maven Central
  access.
- Add package-embedded version information for support diagnostics and smoke
  checks.
- Add release artifact smoke coverage that validates local build outputs and
  can validate downloaded package outputs.
- Expand operator troubleshooting around install, upgrade, rollback, Java
  discovery, PID files, status checks, package integrity, and offline installs.

## Target Module Work

| Module | `0.15.0` target |
| --- | --- |
| `runtime-core` | Preserve the dependency-light contract surface; add no packaging, checksum/signing, installer, filesystem-layout, service-manager, framework, database, Redis, or exporter dependencies. |
| `runtime-app` | Own package-facing runtime behavior, package metadata exposure, local validation entry points, package layout verification, archive checksum verification, and operator-visible diagnostics. |
| Build configuration | Own package metadata generation and local `.sha256`/`.sha512` sidecar generation for standalone jar, zip, and tar.gz outputs; Central remains responsible for published sidecar hosting. |
| `examples` and `docs` | Own migration notes, rollback guidance, offline deployment examples, cross-platform script notes, package integrity procedures, release artifact smoke, and troubleshooting. |
| `runtime-ingress-*` | Preserve published ingress behavior; do not own package integrity, install, upgrade, rollback, or deployment API behavior. |
| `runtime-protocol-*` | Continue to parse payloads without transport, app, packaging, checksum/signing, service-manager, filesystem-layout, status-export, or sink dependencies. |
| `runtime-smoke-tests` | Keep repository-only cross-module checks; release artifact smoke remains verification and does not become a supported dependency surface. |

## Baseline Implementation Work

- Package integrity verification covers distribution zip/tar.gz checksum
  sidecars and unpacked package layout checks.
- Build output includes `.sha256` and `.sha512` sidecars for standalone jar,
  distribution zip, and distribution tar.gz artifacts.
- Signature policy reuses Maven Central `.asc` sidecars and operator trust
  policy without adding signing dependencies to runtime modules.
- Cross-platform script guidance identifies POSIX `sh` as the supported script
  target and Windows usage as operator-owned.
- Configuration migration notes explain how to compare old and new
  `collector.properties` templates and preserve operator-owned overrides.
- Upgrade rollback guidance covers symlink rollback, package directory
  preservation, PID safety, log preservation, status checks, and smoke
  validation after rollback.
- Offline deployment guidance explains how to move published artifacts,
  checksums, signatures, configs, and docs onto restricted servers.
- Package version metadata lets operators inspect runtime version, artifact,
  Java version, package layout, app home, and standalone jar path.
- Release artifact smoke can validate local build outputs or downloaded
  distribution artifacts through checksum verification and app commands.
- Troubleshooting covers checksum mismatch, missing signatures, partial package
  extraction, script permissions, stale PID, config migration errors, offline
  artifact gaps, and version mismatch.

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
- Implementing an installer, package manager integration, or automatic upgrade
  service.

## Readiness Checklist

- [x] `0.14.0` release artifacts are published and verified from Maven Central.
- [x] GitHub Release `v0.14.0` is published.
- [x] Maven reactor is opened at `0.15.0-SNAPSHOT`.
- [x] README and Chinese README describe the `0.15.0` distribution package
  productionization planning line.
- [x] `docs/module-plan.md` and `docs/module-boundaries.md` describe the
  `0.15.0` planning boundary.
- [x] package integrity policy is designed and implemented at the package
  script/build/docs boundary.
- [x] checksum/signature policy is designed.
- [x] cross-platform script compatibility guidance is documented.
- [x] configuration migration notes are documented.
- [x] upgrade rollback strategy is documented.
- [x] offline deployment guidance is documented.
- [x] package version metadata boundary is implemented.
- [x] release artifact smoke design is implemented.
- [x] operator troubleshooting additions are documented.
- [ ] implementation PRs keep `runtime-core`, `runtime-protocol-*`, and
  `protocol-sdk` free of packaging, checksum/signing, installer, service
  manager, database, Redis, and exporter dependencies.
