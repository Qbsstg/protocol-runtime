# Protocol Runtime 0.9.0 Release Notes

Draft release notes for the `0.9.0` runtime release line.

## Planned Highlights

- Open the Maven reactor at `0.9.0-SNAPSHOT` after the published `0.8.0` MQTT
  runtime-app release.
- Harden downstream sink boundaries after TCP, HTTP, Kafka, and MQTT ingress
  baselines are available.
- Improve standalone collector sink configuration, sink failure isolation,
  status output, and operator-facing examples.
- Add app-level sink failure counters and last-error status output so bad
  downstream delivery does not collapse ingress or parsing.
- Preserve `runtime-core` as a dependency-light contract module with no Spring,
  Netty, Kafka, MQTT, HTTP, database, Redis, object storage, or observability
  exporter dependencies.

## Scope

`0.9.0` focuses on sink and operations hardening. It should make the standalone
collector easier to run and diagnose while keeping downstream delivery
dependencies in app or dedicated adapter modules.

## Dependency Policy

`runtime-core` must remain free of transport, broker, storage, database,
framework, and exporter dependencies.

Dedicated sink or operations modules may own external dependencies only after
their boundary is explicit. `runtime-protocol-*` modules continue to depend only
on `runtime-core`, published `protocol-sdk` parser artifacts, and tests.

## Verification Target

Before release branch work, the readiness branch should pass:

- `git diff --check`
- `mvn -q verify`
- `mvn -q -Pcentral-release -Dgpg.skip=true -Dcentral.skipPublishing=true deploy`
- standalone smoke coverage for supported collector examples
- dependency boundary checks proving new dependencies do not enter
  `runtime-core`, `runtime-protocol-*`, or `protocol-sdk`

The readiness audit will be tracked in `release-readiness-0.9.0.md` once the
release scope is stable.
