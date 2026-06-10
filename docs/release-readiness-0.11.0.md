# Runtime 0.11.0 Release Readiness Audit

This note records the release-readiness decision for the `0.11.0`
`protocol-runtime` release branch.

The release branch fixes the Maven reactor version at `0.11.0`. No tag should
be created and no real Maven Central upload should happen during this release
branch PR.

## Release Scope

`0.11.0` moves the runtime from the published health and status
productionization release to the first standalone collector management-plane
baseline. It should present:

- `runtime-core` as the dependency-light runtime contract module with no
  adapter, broker, storage, framework, database, Redis, management endpoint, or
  observability exporter dependencies.
- `runtime-protocol-iec104`, `runtime-protocol-iec101`,
  `runtime-protocol-iec103`, and `runtime-protocol-modbus` as parser-binding
  modules around the published `protocol-sdk:0.7.0` artifacts.
- `runtime-ingress-tcp-netty`, `runtime-ingress-http`,
  `runtime-ingress-kafka`, and `runtime-ingress-mqtt` as ingress adapters that
  map external payloads into runtime envelopes and backpressure results without
  owning downstream sink delivery or management endpoints.
- `runtime-app` as the JDK 21 standalone collector assembly for TCP, HTTP,
  Kafka, and MQTT ingress, app-level protocol selection, sink routing,
  lifecycle/status output, app-local health/readiness calculation, and JDK
  `HttpServer` management endpoints.
- `runtime-smoke-tests` as repository-only cross-module verification for TCP
  ingress, runtime-core, runtime protocol bindings, the standalone app, and
  health/status evidence.

Out of scope:

- Spring Boot or any application framework.
- Database, Redis, durable queue, object storage, external storage sinks,
  retry stores, dashboards, durable health history, or observability exporters.
- Reusing `runtime-ingress-http` as a management endpoint. It remains the HTTP
  protocol-payload ingestion adapter.
- Kafka producer/sink publishing, MQTT publisher/sink behavior, dead-letter
  topics, retry topics, schema registry integration, Kafka Streams, or Kafka
  transactions.
- Serial port implementation for IEC101/IEC103.
- UDP ingress for Modbus UDP.
- New parser behavior inside `protocol-sdk`.

## Publishing Policy

The selected `0.11.0` policy is to publish the runtime library, ingress
adapter, and application assembly modules as one versioned Maven reactor
release:

| Module | Publish at `0.11.0` | Release posture |
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
| `runtime-app` | Yes | Standalone collector assembly with app-owned health/readiness, status output, and JDK HTTP management endpoints. |
| `runtime-smoke-tests` | No | Test-only integration module; `maven.deploy.skip=true` and `central.skipPublishing=true` are both set. |

`runtime-app` remains the deployable assembly boundary. It may combine ingress
adapters, protocol bindings, app-level configuration, app-level sinks,
operator-facing status, app-local health/readiness calculation, and app-owned
management endpoints. It must not move adapter, management, exporter, broker,
database, or Redis dependencies into `runtime-core`, `runtime-protocol-*`, or
`protocol-sdk`.

## Baseline Gates

| Gate | Release evidence | Decision |
| --- | --- | --- |
| Maven release line | Root and module parent versions are fixed at `0.11.0` on the release branch. | Complete. |
| Runtime core dependency isolation | `runtime-core` has no compile dependencies on Spring, Netty, Kafka, MQTT, HTTP, database, Redis, storage, management endpoints, or exporters. | Complete. |
| Protocol binding dependency isolation | `runtime-protocol-*` modules depend only on `runtime-core`, released `protocol-sdk` parser artifacts, and tests. | Complete. |
| Management boundary | Management HTTP is implemented in `runtime-app` with JDK `HttpServer`; `runtime-ingress-http` remains the protocol-payload ingestion adapter. | Complete. |
| Management configuration | `collector.management.enabled`, `host`, `port`, `healthPath`, `readinessPath`, and `statusPath` are parsed and validated. | Complete. |
| Management JSON | `/health`, `/readiness`, and `/status` expose lifecycle, health, readiness, sources, listeners, sink, backpressure, metrics, and failure counters. | Complete. |
| Failure behavior | Management port conflicts fail startup, record `FAILED` health/readiness evidence, and roll back already-started collector listeners. | Complete. |
| Standalone examples | TCP and HTTP standalone smoke scripts query management endpoints in addition to ingestion paths. | Complete. |
| Release metadata | Maven Central metadata, source/javadoc jars, GPG signing, and Central publishing profile remain configured. | Complete. |
| CI action runtime | GitHub Actions uses `actions/checkout@v6` and `actions/setup-java@v5` with Temurin JDK 21. | Complete. |

## Test Coverage Evidence

`runtime-app/src/test/java/.../StandaloneCollectorTest.java` covers the 0.11.0
management boundary:

- management configuration defaults and custom paths,
- localhost port `0` management binding,
- `/health`, `/readiness`, and `/status` JSON output,
- degraded-but-ready status after parser failures,
- validation for management ports and paths,
- management endpoint conflicts with data-ingress listeners, and
- management bind failure rollback when the configured port is already in use.

The standalone smoke scripts cover executable app behavior:

- `examples/smoke-standalone.sh` starts a TCP collector, queries management
  endpoints, sends the IEC104 example frame, waits for file sink output, and
  verifies management readiness.
- `examples/smoke-standalone-http.sh` starts an HTTP collector, queries
  management endpoints, posts a raw IEC104 payload, waits for file sink output,
  and verifies management readiness.

The operator guides are maintained in
[`status-health-readiness.md`](status-health-readiness.md) and
[`status-health-readiness.zh-CN.md`](status-health-readiness.zh-CN.md).

## Release Verification Checklist

Run these checks on the final `0.11.0` release branch commit:

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

## Release Branch Checks On 2026-06-10

These checks passed on the `0.11.0` release branch before opening the release
PR:

| Check | Result | Note |
| --- | --- | --- |
| Maven reactor version | Passed | Root and module parent versions are fixed at `0.11.0`. |
| `git diff --check` | Passed | No whitespace errors exist in the release diff. |
| `mvn -q verify` | Passed | Full JDK 21+ reactor verification passed at version `0.11.0`. |
| `mvn -q -Pcentral-release -Dgpg.skip=true -Dcentral.skipPublishing=true deploy` | Passed | Central profile smoke passed with publishing disabled and signing skipped. |
| `JAVA_BIN=/opt/homebrew/Cellar/openjdk/23.0.2/libexec/openjdk.jdk/Contents/Home/bin/java sh examples/smoke-standalone.sh` | Passed | Standalone TCP collector built `runtime-app-0.11.0-standalone.jar`, verified management HTTP endpoints, accepted the IEC104 example frame, and wrote a parsed record to the file sink. |
| `JAVA_BIN=/opt/homebrew/Cellar/openjdk/23.0.2/libexec/openjdk.jdk/Contents/Home/bin/java sh examples/smoke-standalone-http.sh` | Passed | Standalone HTTP collector built `runtime-app-0.11.0-standalone.jar`, verified management HTTP endpoints, accepted an IEC104 POST payload, and wrote a parsed record to the file sink. |
| Dependency boundary checks | Passed | `runtime-core` has no compile dependencies; `runtime-protocol-*` modules depend only on `runtime-core` and protocol SDK artifacts; adapter dependencies remain isolated to adapter modules, app assembly, or test-only smoke coverage. |

No tag should be created and no real Maven Central upload should be part of the
release branch PR.

## Final Publication

Final `0.11.0` publication is not part of this release branch PR. After this PR
merges, a separate final release step should create and push `v0.11.0`, run the
signed Central upload, wait for validation, publish the Central deployment,
verify public Maven Central resolution, and create the GitHub Release.
