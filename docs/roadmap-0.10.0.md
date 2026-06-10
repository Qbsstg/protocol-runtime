# Protocol Runtime 0.10.0 Roadmap

`0.10.0` starts after the published `0.9.0` sink and operations hardening
release. The development line opens with Maven reactor version
`0.10.0-SNAPSHOT`.

The release target is health checks and runtime status productionization for
the standalone collector while preserving the published TCP, HTTP, Kafka, MQTT,
protocol-binding, and sink-hardening boundaries.

## Goals

- Keep `runtime-core` free of Spring, Netty, Kafka, MQTT, HTTP, database,
  Redis, object storage, and observability exporter dependencies.
- Make collector health and readiness state explicit for configured, starting,
  running, degraded, failed, stopping, and stopped collectors.
- Distinguish listener health, source health, sink health, parser failure
  posture, and backpressure posture in runtime-app status output.
- Add operator-facing examples that show healthy, degraded, and failed runtime
  status without requiring external observability systems.
- Keep any management HTTP endpoint, metrics exporter, dashboard, database,
  Redis, or broker-publishing dependency in dedicated app/adapter modules.

## Target Module Work

| Module | `0.10.0` target |
| --- | --- |
| `runtime-core` | Preserve the dependency-light contract surface; add no framework, transport, broker, storage, database, Redis, or exporter dependencies. |
| `runtime-app` | Formalize app-owned health/readiness state, status formatting, degraded-state calculation, failure summaries, and operator examples. |
| `runtime-ingress-*` | Preserve published ingress behavior and expose only app-consumable status evidence needed by runtime-app health calculations. |
| `runtime-protocol-*` | Continue to parse protocol payloads without transport, app, health endpoint, metrics exporter, or downstream sink dependencies. |
| `runtime-smoke-tests` | Add repository-only smoke coverage for stable health/status paths that cross ingress, parser binding, runner, and sink boundaries. |

## Candidate Work Items

1. Audit current collector lifecycle/status snapshots and identify missing
   health/readiness distinctions.
2. Define app-local health state calculation for listener, source, parser, sink,
   and backpressure posture.
3. Add status formatter output for healthy, degraded, failed, and stopped
   collectors.
4. Add tests for partial listener failure, sink-failure degradation, parser
   failure pressure, and backpressure-driven degradation.
5. Add example commands and troubleshooting docs for status output.
6. Decide whether a dedicated app/adapter management endpoint belongs in a
   later release after the health model is stable.
7. Add release-readiness notes before the `0.10.0` release branch.

## Progress

- Added app-local `CollectorHealthSnapshot`, `CollectorHealthState`, and
  `CollectorReadinessState` derived from the existing `CollectorStatusSnapshot`.
- Status output now includes `health`, `readiness`, and health reasons without
  adding management endpoint, framework, exporter, or adapter dependencies to
  `runtime-core`.
- Focused tests cover configured/not-ready, healthy/running, parse-failure
  degradation, backpressure degradation, and startup-failure health states.
- Added English and Chinese operator guides with a health/readiness matrix,
  trimmed status-line examples, reason catalog, and triage order.

## Non-Goals

- Spring Boot or application framework adoption.
- HTTP management API or metrics exporter implementation inside `runtime-core`.
- Database, Redis, object storage, durable queues, retry stores, or dashboards.
- Kafka producer, MQTT publisher, or external sink delivery behavior.
- New parser behavior inside `protocol-sdk`.

## Readiness Criteria

Before `0.10.0` release readiness:

- README and Chinese README describe the `0.10.0-SNAPSHOT` development line.
- `docs/module-plan.md` and `docs/module-boundaries.md` describe the health and
  status boundary.
- health/readiness behavior is covered by focused tests.
- status output examples are documented for healthy, degraded, and failed
  runtime states.
- `git diff --check` passes.
- `mvn -q verify` passes.
- dependency boundary checks prove new dependencies stay out of `runtime-core`
  and `protocol-sdk`.
