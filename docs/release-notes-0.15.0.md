# Protocol Runtime 0.15.0 Release Notes

Release notes draft for the `0.15.0` runtime release.

`0.15.0` follows the published `0.14.0` runtime package distribution
governance release. The release branch fixes the Maven reactor version at
`0.15.0` for distribution package productionization. No `v0.15.0` tag is
created and no real Maven Central upload is part of the release branch PR.

## Baseline Highlights

- Adds package metadata through `package.properties` in the distribution root.
- Adds `bin/protocol-runtime version` for support diagnostics covering runtime
  version, artifact, Java version, package layout, app home, and standalone jar.
- Adds `bin/protocol-runtime verify-package` for unpacked package layout checks
  and SHA-256/SHA-512 verification of distribution archive checksum sidecars.
- Generates local `.sha256` and `.sha512` sidecars for standalone jar,
  distribution zip, and distribution tar.gz build outputs.
- Documents checksum/signature policy for release artifacts without moving
  signing or checksum dependencies into `runtime-core`.
- Improves cross-platform script compatibility guidance for POSIX shells and
  operator-owned Windows usage.
- Adds configuration migration notes for package upgrades.
- Defines an upgrade rollback strategy for failed package replacement.
- Adds offline deployment guidance for servers without direct Maven Central
  access.
- Adds release artifact smoke coverage for local build outputs or downloaded
  distribution artifacts.
- Expands operator troubleshooting for install, upgrade, rollback, Java
  discovery, PID files, status checks, package integrity, script permissions,
  config migration, version mismatch, and offline deployments.
- Preserve `runtime-core` as a dependency-light contract module with no Spring,
  Netty, Kafka, MQTT, HTTP, database, Redis, packaging, checksum/signing,
  installer, service-manager, filesystem-layout, access-control,
  request-logging, or external observability dependencies.

## Scope

`0.15.0` focuses on making the standalone collector distribution packages more
production-ready without turning `runtime-core`, protocol bindings, or parser
SDK modules into a packaging framework. Package productionization belongs in
`runtime-app`, build configuration, examples, docs, or a future dedicated
app/distribution module.

The detailed plan is tracked in [`roadmap-0.15.0.md`](roadmap-0.15.0.md).

## Dependency Policy

`runtime-core` must remain free of transport, broker, storage, database,
framework, service-manager, shell-wrapper, deployment-wrapper,
distribution-packaging, filesystem-layout, checksum/signing, installer,
access-control, request-logging, health endpoint, management API, dashboard,
and exporter dependencies.

`runtime-ingress-http` remains the protocol payload ingestion adapter. It must
not become the management endpoint, package distribution endpoint, deployment
API, upgrade API, or status export API.

## Verification Targets

Implementation and future release PRs must pass:

- `git diff --check`
- `mvn -q verify`
- central-release dry run with publishing disabled
- standalone TCP collector smoke
- standalone HTTP collector smoke
- management HTTP smoke
- package distribution smoke
- release artifact smoke for local build or downloaded package outputs
- dependency boundary checks proving package productionization work does not
  enter `runtime-core`, `runtime-protocol-*`, or `protocol-sdk`
- GitHub CI on release PRs

## Release Branch Checks

The release branch checks are tracked in
[`release-readiness-0.15.0.md`](release-readiness-0.15.0.md). They must pass
before the release branch is merged to `main`.

## Publication

- Tag: not created
- Central deployment: not started
- Central state: not published
- GitHub Release: not created
