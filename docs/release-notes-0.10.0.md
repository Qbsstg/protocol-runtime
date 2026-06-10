# Protocol Runtime 0.10.0 Release Notes

Release notes for the published `0.10.0` runtime release.

`0.10.0` has been tagged as `v0.10.0`, published to Maven Central, verified
from isolated local Maven repositories, and published as a GitHub Release.

## Highlights

- Published the Maven reactor at `0.10.0` after the published `0.9.0` sink and
  operations hardening release.
- Formalized runtime-app health and readiness state for standalone collector
  operation.
- Improved status output so operators can distinguish listener, source, parser,
  sink, failure, and backpressure posture.
- Added app-local `CollectorHealthSnapshot` derivation with `HEALTHY`,
  `DEGRADED`, `FAILED`, lifecycle-aligned non-running states, `READY` /
  `NOT_READY`, and explainable health reasons.
- Added examples and troubleshooting guidance for healthy, degraded, failed, and
  stopped collector states.
- Added English and Chinese status guides with a health/readiness matrix,
  trimmed status-line examples, reason catalog, and operator triage order.
- Added repository smoke coverage proving standalone TCP/IEC104 collector health
  status across healthy/ready and degraded/ready parser-failure states.
- Added a `0.10.0` release-readiness audit covering release scope, module policy,
  health/status gates, verification commands, and readiness evidence.
- Preserved `runtime-core` as a dependency-light contract module with no Spring,
  Netty, Kafka, MQTT, HTTP, database, Redis, object storage, or observability
  exporter dependencies.

## Scope

`0.10.0` focuses on health checks and runtime status productionization after the
published TCP, HTTP, Kafka, MQTT, and sink-hardening baselines.

## Dependency Policy

`runtime-core` must remain free of transport, broker, storage, database,
framework, health endpoint, management API, dashboard, and exporter
dependencies.

Dedicated app or adapter modules may own external dependencies only after their
boundary is explicit. `runtime-protocol-*` modules continue to depend only on
`runtime-core`, published `protocol-sdk` parser artifacts, and tests.

## Verification

The release passed:

- `git diff --check`
- `mvn -q verify`
- `mvn -q -Pcentral-release -Dgpg.skip=true -Dcentral.skipPublishing=true deploy`
- signed Central dry run with publishing disabled
- standalone smoke coverage for supported collector examples
- dependency boundary checks proving new dependencies do not enter
  `runtime-core`, `runtime-protocol-*`, or `protocol-sdk`
- Maven Central deployment `976f18d2-4067-4163-8bf4-2f37425e3507` reached
  `PUBLISHED`
- public Maven Central resolution passed for `runtime-core`,
  `runtime-ingress-kafka`, `runtime-ingress-mqtt`, `runtime-app`, and the
  `runtime-app` `standalone` classifier at `0.10.0`

The detailed plan is tracked in [`roadmap-0.10.0.md`](roadmap-0.10.0.md).

## Publication

- Tag: `v0.10.0`
- Central deployment: `976f18d2-4067-4163-8bf4-2f37425e3507`
- Central state: `PUBLISHED`
- GitHub Release:
  <https://github.com/Qbsstg/protocol-runtime/releases/tag/v0.10.0>
