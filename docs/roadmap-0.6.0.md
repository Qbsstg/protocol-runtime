# Protocol Runtime 0.6.0 Roadmap

`0.6.0` starts after the published `0.5.0` adapter-boundary release. The
development line opens with Maven reactor version `0.6.0-SNAPSHOT`.

The release target is HTTP ingress productionization and runtime-app HTTP
collector assembly. Kafka and MQTT remain design-only until the HTTP adapter
path proves the shared app and test boundaries.

## Goals

- Keep `runtime-core` dependency-light and free of HTTP, Kafka, MQTT, Spring,
  database, Redis, and observability exporter dependencies.
- Preserve `runtime-protocol-*` as parser-binding-only modules around the
  published `protocol-sdk:0.7.0` artifacts.
- Harden `runtime-ingress-http` beyond the `0.5.0` JDK `HttpServer` baseline:
  lifecycle ownership, request attributes, response policy, parser failure
  routing, and backpressure behavior should be explicit and tested.
- Add app-level HTTP listener configuration without breaking the existing TCP
  collector configuration.
- Prove an end-to-end HTTP collector path from HTTP POST payload to
  `RuntimePipelineRunner`, selected protocol binding, and configured sinks.
- Keep Kafka and MQTT client dependencies out of the reactor until their
  adapter modules are intentionally opened.

## Target Module Work

| Module | `0.6.0` target |
| --- | --- |
| `runtime-core` | No dependency or API expansion unless HTTP app assembly exposes a protocol-neutral gap. |
| `runtime-ingress-http` | Productionize the JDK HTTP adapter baseline: lifecycle, response contract, payload limits, source mapping, parse failure routing, and test fixtures. |
| `runtime-app` | Add HTTP listener configuration and app assembly around `runtime-ingress-http` while preserving TCP defaults. HTTP-only configurations no longer open the legacy default TCP listener. |
| `runtime-protocol-*` | Reuse existing parser bindings for HTTP payloads without transport-specific code. |
| `runtime-smoke-tests` | Add HTTP end-to-end smoke coverage for at least IEC104 and malformed payload routing. |
| `runtime-ingress-kafka` | Remain design-only. No Kafka client dependency in `0.6.0` unless a later release decision changes scope. |
| `runtime-ingress-mqtt` | Remain design-only. No MQTT client dependency in `0.6.0` unless a later release decision changes scope. |

## Candidate Work Items

1. Open the `0.6.0-SNAPSHOT` Maven line and document the HTTP productionization
   scope.
2. Add runtime-app HTTP listener configuration keys with validation for host,
   port, path, source mapping mode, max payload bytes, response mode, backlog,
   worker threads, source references, duplicate names, and duplicate endpoints.
   Status: implemented in `runtime-app`.
3. Add an app-owned HTTP collector assembly that creates one or more
   `HttpIngressServer` instances and routes payloads through the selected
   `RuntimePipelineRunner`. Status: implemented in `runtime-app`.
4. Add HTTP end-to-end tests for successful IEC104 payloads, malformed payload
   failure routing, retry/drop backpressure responses, payload-size rejection,
   lifecycle start/stop, and port conflict rollback. Status: implemented in
   `runtime-app` unit/integration tests.
5. Update README and Chinese README with HTTP collector configuration examples
   after implementation lands. Status: implemented, with
   `examples/collector-http.properties` and
   `examples/smoke-standalone-http.sh`.
6. Add release-readiness notes before the `0.6.0` release branch. Status:
   pending for the release-readiness branch.

## Non-Goals

- Spring Boot or servlet container integration.
- Kafka or MQTT client implementation.
- Downstream Kafka/MQTT publishing sinks.
- Database, Redis, object storage, or durable queue integrations.
- HTTP management API, metrics exporter, dashboard, or health endpoint.
- TLS, authentication, authorization, or production secrets management.
- New parser behavior inside `protocol-sdk`.

## Dependency Boundaries

- `runtime-core` must not depend on HTTP, Kafka, MQTT, Netty, Spring,
  database, Redis, object storage, or observability exporter artifacts.
- `runtime-ingress-http` may use JDK HTTP APIs but should not introduce a
  third-party HTTP framework in `0.6.0`.
- `runtime-app` may assemble HTTP ingress, TCP ingress, protocol bindings, and
  app-owned sinks because it is the deployable boundary.
- `runtime-protocol-*` modules must not depend on ingress adapters or app code.
- `protocol-sdk` remains parser-only and does not depend on
  `protocol-runtime`.

## Readiness Criteria

Before `0.6.0` release readiness:

- README and Chinese README describe the `0.6.0-SNAPSHOT` development line.
- `docs/module-plan.md` and `docs/module-boundaries.md` describe the HTTP app
  assembly boundary.
- HTTP collector behavior is covered by unit or smoke tests.
- HTTP standalone smoke is available through `examples/smoke-standalone-http.sh`.
- HTTP listener status appears in app status snapshots and formatter output.
- `git diff --check` passes.
- `mvn -q verify` passes.
- dependency boundary checks prove `runtime-core` remains adapter-free.
