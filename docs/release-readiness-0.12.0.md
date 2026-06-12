# Runtime 0.12.0 Release Readiness Audit

This note records the release-readiness decision for the `0.12.0`
`protocol-runtime` release branch.

The release branch fixes the Maven reactor version at `0.12.0`. No tag is
created and no real Maven Central upload happens during the release branch PR;
final publication is completed after merge.

## Release Scope

`0.12.0` moves the runtime from the first standalone collector management-plane
baseline to the first management-plane productionization baseline. It should
present:

- `runtime-core` as the dependency-light runtime contract module with no
  adapter, broker, storage, framework, database, Redis, management endpoint,
  access-control, request-logging, deployment wrapper, or observability
  exporter dependencies.
- `runtime-protocol-iec104`, `runtime-protocol-iec101`,
  `runtime-protocol-iec103`, and `runtime-protocol-modbus` as parser-binding
  modules around the published `protocol-sdk:0.7.0` artifacts.
- `runtime-ingress-tcp-netty`, `runtime-ingress-http`,
  `runtime-ingress-kafka`, and `runtime-ingress-mqtt` as ingress adapters that
  map external payloads into runtime envelopes and backpressure results without
  owning downstream sink delivery or management endpoints.
- `runtime-app` as the JDK 21 standalone collector assembly for TCP, HTTP,
  Kafka, and MQTT ingress, app-level protocol selection, sink routing,
  lifecycle/status output, app-local health/readiness calculation, app-owned
  JDK `HttpServer` management endpoints, management access control, request
  logging, management metrics, bounded health history, and stable management
  error JSON.
- `runtime-smoke-tests` as repository-only cross-module verification for TCP
  ingress, runtime-core, runtime protocol bindings, the standalone app, and
  health/status evidence.

Out of scope:

- Spring Boot or any application framework.
- Database, Redis, durable queue, object storage, external storage sinks,
  retry stores, dashboards, durable health history, external observability
  exporters, or management-plane storage.
- Reusing `runtime-ingress-http` as a management endpoint. It remains the HTTP
  protocol-payload ingestion adapter.
- Kafka producer/sink publishing, MQTT publisher/sink behavior, dead-letter
  topics, retry topics, schema registry integration, Kafka Streams, or Kafka
  transactions.
- Serial port implementation for IEC101/IEC103.
- UDP ingress for Modbus UDP.
- New parser behavior inside `protocol-sdk`.

## Publishing Policy

The selected `0.12.0` policy is to publish the runtime library, ingress
adapter, and application assembly modules as one versioned Maven reactor
release:

| Module | Publish at `0.12.0` | Release posture |
| --- | --- | --- |
| `protocol-runtime` | Yes | Parent POM for repository builds and release metadata. |
| `runtime-core` | Yes | Stable baseline runtime contracts. |
| `runtime-protocol-iec104` | Yes | IEC104 runtime binding against `protocol-iec104:0.7.0`. |
| `runtime-protocol-iec101` | Yes | IEC101 runtime binding against `protocol-iec101:0.7.0`. |
| `runtime-protocol-iec103` | Yes | IEC103 runtime binding against `protocol-iec103:0.7.0`. |
| `runtime-protocol-modbus` | Yes | Modbus runtime binding against `protocol-modbus:0.7.0`. |
| `runtime-ingress-tcp-netty` | Yes | TCP/Netty ingress adapter retained for runtime app assembly. |
| `runtime-ingress-http` | Yes | JDK `HttpServer` HTTP ingress adapter retained for runtime app assembly. |
| `runtime-ingress-kafka` | Yes | Kafka ingress adapter retained for runtime app assembly. |
| `runtime-ingress-mqtt` | Yes | MQTT ingress adapter retained for runtime app assembly. |
| `runtime-app` | Yes | Standalone collector assembly with app-owned health/readiness, status output, JDK HTTP management endpoints, access control, request logging, management metrics, bounded health history, and stable error JSON. |
| `runtime-smoke-tests` | No | Test-only integration module; `maven.deploy.skip=true` and `central.skipPublishing=true` are both set. |

`runtime-app` remains the deployable assembly boundary. It may combine ingress
adapters, protocol bindings, app-level configuration, app-level sinks,
operator-facing status, app-local health/readiness calculation, and app-owned
management endpoints. It must not move adapter, management, exporter, broker,
database, Redis, access-control, request-logging, or deployment-wrapper
dependencies into `runtime-core`, `runtime-protocol-*`, or `protocol-sdk`.

## Baseline Gates

| Gate | Release evidence | Decision |
| --- | --- | --- |
| Maven release line | Root and module parent versions are fixed at `0.12.0` on the release branch. | Complete. |
| Runtime core dependency isolation | `runtime-core` has no compile dependencies on Spring, Netty, Kafka, MQTT, HTTP, database, Redis, storage, management endpoints, access-control, request-logging, deployment wrappers, or exporters. | Complete. |
| Protocol binding dependency isolation | `runtime-protocol-*` modules depend only on `runtime-core`, released `protocol-sdk` parser artifacts, and tests. | Complete. |
| Management boundary | Management HTTP is implemented in `runtime-app` with JDK `HttpServer`; `runtime-ingress-http` remains the protocol-payload ingestion adapter. | Complete. |
| Management access control | `collector.management.access` supports `local`, `open`, and `token`; token mode accepts bearer or `X-Management-Token` headers and rejects missing/invalid tokens. | Complete. |
| Safe default posture | Management defaults to loopback host and `local` access mode. | Complete. |
| Request logging | Management request logs include method, path, status, duration, remote address, and rejection reason without logging tokens, headers, or payload bytes. | Complete. |
| Management JSON metrics | `/status` exposes management request counts, status-code counts, rejected/error counters, last request evidence, listener/source/sink/backpressure evidence, and runtime failure counters. | Complete. |
| Health history | `/status` exposes bounded in-memory health-history entries for lifecycle, degraded, failed, and recovered transitions without database or Redis. | Complete. |
| Stable error JSON | Management not found, method not allowed, malformed request, unauthorized, forbidden, and internal error paths use stable JSON fields. | Complete. |
| Standalone examples | TCP and HTTP standalone smoke scripts verify token-protected management success and failure paths in addition to ingestion paths. | Complete. |
| Release metadata | Maven Central metadata, source/javadoc jars, GPG signing, and Central publishing profile remain configured. | Complete. |
| CI action runtime | GitHub Actions uses `actions/checkout@v6` and `actions/setup-java@v5` with Temurin JDK 21. | Complete. |

## Test Coverage Evidence

`runtime-app/src/test/java/.../StandaloneCollectorTest.java` covers the 0.12.0
management productionization boundary:

- management configuration defaults, custom paths, access mode, request
  logging, and health-history settings,
- localhost port `0` management binding,
- `/health`, `/readiness`, and `/status` JSON output,
- token access control with missing, invalid, bearer, and
  `X-Management-Token` flows,
- local access control rejecting a non-loopback remote address,
- stable JSON error responses for not found, method not allowed, and malformed
  request paths,
- request logging redaction for token and payload bytes,
- bounded health-history entries and degraded transition evidence,
- degraded-but-ready status after parser failures,
- validation for management ports, paths, access mode, token requirement,
  request-logging boolean, and health-history bounds,
- management endpoint conflicts with data-ingress listeners, and
- management bind failure rollback when the configured port is already in use.

The standalone smoke scripts cover executable app behavior:

- `examples/smoke-standalone.sh` starts a TCP collector, verifies management
  unauthorized/not-found/method-not-allowed/malformed request JSON, queries
  token-protected management endpoints, sends the IEC104 example frame, waits
  for file sink output, and verifies management readiness, metrics, and health
  history.
- `examples/smoke-standalone-http.sh` starts an HTTP collector, verifies the
  same management paths, posts a raw IEC104 payload, waits for file sink output,
  and verifies management readiness, metrics, and health history.

The operator guides are maintained in
[`status-health-readiness.md`](status-health-readiness.md) and
[`status-health-readiness.zh-CN.md`](status-health-readiness.zh-CN.md).

## Release Verification Checklist

Run these checks on the final `0.12.0` release branch commit:

```bash
git diff --check

mvn -q verify

mvn -q -Pcentral-release \
  -Dgpg.skip=true \
  -Dcentral.skipPublishing=true \
  deploy

JAVA_BIN=/path/to/jdk-21-or-newer/bin/java sh examples/smoke-standalone.sh

JAVA_BIN=/path/to/jdk-21-or-newer/bin/java sh examples/smoke-standalone-http.sh

mvn -q -pl runtime-core dependency:tree -Dscope=compile

mvn -q -pl runtime-protocol-iec104,runtime-protocol-iec101,runtime-protocol-iec103,runtime-protocol-modbus \
  dependency:tree -Dscope=compile

mvn -q -pl runtime-ingress-tcp-netty,runtime-ingress-http,runtime-ingress-kafka,runtime-ingress-mqtt,runtime-app \
  dependency:tree -Dscope=compile

mvn -q -pl runtime-smoke-tests dependency:tree -Dscope=test
```

The Central profile command above is intentionally a smoke check with
publishing disabled and signing skipped. A real release still requires a signed
dry run:

```bash
mvn -Pcentral-release -Dcentral.skipPublishing=true clean deploy
```

That signed dry run must pass before any real Central upload.

## Release Branch Checks On 2026-06-12

These checks passed on the `0.12.0` release branch before opening the release
PR:

| Check | Result | Note |
| --- | --- | --- |
| Maven reactor version | Passed | Root and module parent versions are fixed at `0.12.0`; `protocol-sdk.version` remains `0.7.0`. |
| `git diff --check` | Passed | No whitespace errors were reported before final documentation updates. |
| `mvn -q verify` | Passed | Full JDK 21+ reactor verification passed at version `0.12.0`. |
| `mvn -q -Pcentral-release -Dgpg.skip=true -Dcentral.skipPublishing=true deploy` | Passed | Central profile smoke passed with publishing disabled and signing skipped. |
| `JAVA_BIN=/opt/homebrew/Cellar/openjdk/23.0.2/libexec/openjdk.jdk/Contents/Home/bin/java sh examples/smoke-standalone.sh` | Passed | Standalone TCP collector smoke passed and verified IEC104 file sink output plus management status. |
| `JAVA_BIN=/opt/homebrew/Cellar/openjdk/23.0.2/libexec/openjdk.jdk/Contents/Home/bin/java sh examples/smoke-standalone-http.sh` | Passed | Standalone HTTP collector smoke passed and verified response, file sink output, and management status. |
| Dependency boundary checks | Passed | `runtime-core`, `runtime-protocol-*`, adapter, app, and smoke-test dependency trees were checked with Maven. |

No tag is created and no real Maven Central upload is part of the release
branch PR.

## Final Publication

Final `0.12.0` publication happens after the release branch merges:

| Step | Result | Evidence |
| --- | --- | --- |
| Tag | Pending | Create `v0.12.0` from the merged release commit. |
| Real Central upload | Pending | Create a Central deployment with `mvn -Pcentral-release clean deploy`. |
| Manual Central publish | Pending | Publish the validated deployment after human confirmation. |
| Public Maven Central verification | Pending | Resolve runtime artifacts and `io.github.qbsstg:runtime-app:0.12.0:jar:standalone` from an isolated local Maven repository. |
| GitHub Release | Pending | Publish release notes at `https://github.com/Qbsstg/protocol-runtime/releases/tag/v0.12.0`. |
