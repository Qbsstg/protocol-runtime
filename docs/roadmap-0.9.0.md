# Protocol Runtime 0.9.0 Roadmap

`0.9.0` starts after the published `0.8.0` MQTT runtime-app release. The
development line opens with Maven reactor version `0.9.0-SNAPSHOT`.

The release target is downstream sink and operations hardening after the TCP,
HTTP, Kafka, and MQTT ingress baselines are available. Sink, health, status, and
delivery dependencies must stay out of `runtime-core` unless a small
protocol-neutral contract is proven necessary.

## Goals

- Keep `runtime-core` free of Spring, Netty, Kafka, MQTT, HTTP, database,
  Redis, object storage, and observability exporter dependencies.
- Stabilize downstream sink boundaries before adding broker or storage sinks.
- Harden file/logging delivery behavior, sink failure reporting, and
  parse-failure isolation in the standalone collector.
- Improve runtime status output so operators can inspect configured sources,
  ingress clients, sink state, lifecycle state, counters, and recent failures.
- Strengthen backpressure and sink-failure behavior across TCP, HTTP, Kafka,
  and MQTT app assembly paths.
- Add operational examples that cover TCP, HTTP, Kafka, MQTT, file sink output,
  and failure troubleshooting without requiring heavyweight frameworks.

## Target Module Work

| Module | `0.9.0` target |
| --- | --- |
| `runtime-core` | Preserve the dependency-light contract surface; only accept protocol-neutral sink or status contracts if app-only code cannot express them cleanly. |
| `runtime-app` | Harden app-owned sink configuration, lifecycle/status output, file/logging delivery behavior, parse-failure isolation, and operator-facing examples. |
| `runtime-ingress-*` | Keep ingress adapters focused on source-to-envelope mapping and backpressure result handling; do not couple them to downstream sinks. |
| `runtime-protocol-*` | Keep parser bindings free of transport, app, sink, and retry-store dependencies. |
| `runtime-sink-*` | Add only after the sink contract is clear; dependencies must stay inside the dedicated sink module. |
| `runtime-smoke-tests` | Add repository-only smoke coverage for stable end-to-end app paths that should not become supported application dependencies. |

## Candidate Work Items

1. Audit existing logging/file/in-memory sink behavior and failure routing.
2. Add app-level sink failure isolation and status output without changing
   `runtime-core`.
3. Harden file sink rotation and output format documentation.
4. Define the first explicit sink lifecycle/status contract if app-local
   behavior is no longer enough.
5. Add status output for sink state, failure counters, and recent sink errors.
6. Re-check backpressure behavior when the record sink is slow or failing.
7. Add cross-ingress examples for TCP, HTTP, Kafka, and MQTT collectors.
8. Add release-readiness notes before the `0.9.0` release branch.

## Progress

- runtime-app now isolates record/failure sink write exceptions at the app
  assembly boundary.
- `CollectorRuntimeMetrics` exposes sink failure count, last target, last source
  id, timestamp, exception type, and message.
- status formatter output includes sink failure counters and the latest sink
  failure summary.
- focused tests cover record sink and failure sink exceptions without adding
  dependencies to `runtime-core`.

## Non-Goals

- Spring Boot or application framework adoption.
- Database, Redis, object storage, durable queues, or retry stores inside
  `runtime-core`.
- Kafka producer or MQTT publisher sink implementation unless a dedicated
  module boundary is added first.
- HTTP management API or metrics exporter implementation in `runtime-core`.
- New parser behavior inside `protocol-sdk`.

## Readiness Criteria

Before `0.9.0` release readiness:

- README and Chinese README describe the `0.9.0-SNAPSHOT` development line.
- `docs/module-plan.md` and `docs/module-boundaries.md` describe the sink and
  operations boundary.
- sink failure and parse-failure isolation are covered by tests.
- status output covers source, ingress, sink, lifecycle, counters, and recent
  failure posture.
- examples document the supported standalone collector paths without adding
  framework dependencies.
- `git diff --check` passes.
- `mvn -q verify` passes.
- dependency boundary checks prove new dependencies stay out of `runtime-core`
  and `protocol-sdk`.
