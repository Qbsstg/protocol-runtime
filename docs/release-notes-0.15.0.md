# Protocol Runtime 0.15.0 Release Notes

Release notes draft for the future `0.15.0` runtime release.

`0.15.0` follows the published `0.14.0` runtime package distribution
governance release. The development line is opened at `0.15.0-SNAPSHOT` for
distribution package productionization planning. No `v0.15.0` tag is created
and no real Maven Central upload is part of this planning baseline.

## Planned Highlights

- Define package integrity checks for the standalone jar, distribution zip, and
  distribution tar.gz artifacts.
- Define checksum and signature policy for release artifacts without moving
  signing or checksum dependencies into `runtime-core`.
- Improve cross-platform script compatibility guidance for POSIX shells and
  operator-owned Windows usage.
- Add configuration migration notes for package upgrades.
- Define an upgrade rollback strategy for failed package replacement.
- Add offline deployment guidance for servers without direct Maven Central
  access.
- Define package-embedded version information for support diagnostics and smoke
  checks.
- Plan release artifact smoke coverage against published package artifacts.
- Expand operator troubleshooting for install, upgrade, rollback, Java
  discovery, PID files, status checks, package integrity, and offline
  deployments.
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

Future implementation PRs must pass before release:

- `git diff --check`
- `mvn -q verify`
- central-release dry run with publishing disabled
- standalone TCP collector smoke
- standalone HTTP collector smoke
- management HTTP smoke
- package distribution smoke
- future release artifact smoke for published package outputs
- dependency boundary checks proving package productionization work does not
  enter `runtime-core`, `runtime-protocol-*`, or `protocol-sdk`
- GitHub CI on release PRs

## Publication

- Tag: not created
- Central deployment: not started
- Central state: not published
- GitHub Release: not created
