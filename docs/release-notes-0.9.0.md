# Protocol Runtime 0.9.0 Release Notes

Release notes for the published `0.9.0` runtime release.

`0.9.0` has been tagged as `v0.9.0`, published to Maven Central, verified from
isolated local Maven repositories, and published as a GitHub Release.

## Highlights

- Harden downstream sink boundaries after TCP, HTTP, Kafka, and MQTT ingress
  baselines are available.
- Improve standalone collector sink configuration, sink failure isolation,
  status output, and operator-facing examples.
- Add app-level sink failure counters and last-error status output so bad
  downstream delivery does not collapse ingress or parsing.
- Report file sink operational state, including output path, open state, active
  byte count, retained history count, in-process rotation count, and rotation
  limits.
- Add optional app-level sink-failure-triggered backpressure so downstream sink
  failures can make later ingress payloads return `RETRY_LATER` or `DROP`
  before parsing.
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

The release branch passed:

- `git diff --check`
- `mvn -q verify`
- `mvn -q -Pcentral-release -Dgpg.skip=true -Dcentral.skipPublishing=true deploy`
- standalone smoke coverage for supported collector examples
- dependency boundary checks proving new dependencies do not enter
  `runtime-core`, `runtime-protocol-*`, or `protocol-sdk`

The readiness audit is tracked in
[`release-readiness-0.9.0.md`](release-readiness-0.9.0.md).

## Publication

- Release tag: `v0.9.0`
- Release commit: `16fcd5f831c5a90d27b46b3db9ccbc9a34a0ca8d`
- Central deployment: `f3a7448f-c79d-4a5b-a73c-a251bfb1ad8f`
- GitHub Release:
  <https://github.com/Qbsstg/protocol-runtime/releases/tag/v0.9.0>
- Public Maven Central resolution passed for `runtime-core`,
  `runtime-ingress-kafka`, `runtime-ingress-mqtt`, `runtime-app`, and
  `runtime-app` with the `standalone` classifier.
