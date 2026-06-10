# Runtime 0.10.0 Release Readiness Audit

This note records the release-readiness decision for the `0.10.0`
`protocol-runtime` release target.

The readiness branch keeps the Maven reactor version at `0.10.0-SNAPSHOT`.
The release branch will fix the Maven reactor version at `0.10.0`. Readiness
and release branch PR work must not create a tag or perform a real Maven
Central upload; final publication happens only after the release PR merges to
`main`.

## Release Scope

`0.10.0` moves the runtime from the published sink and operations hardening
release to a health and runtime status productionization release. It should
present:

- `runtime-core` as the dependency-light runtime contract module with no
  adapter, broker, storage, framework, database, Redis, management endpoint, or
  observability exporter dependencies.
- `runtime-protocol-iec104`, `runtime-protocol-iec101`,
  `runtime-protocol-iec103`, and `runtime-protocol-modbus` as parser-binding
  modules around the published `protocol-sdk:0.7.0` artifacts.
- `runtime-ingress-tcp-netty`, `runtime-ingress-http`,
  `runtime-ingress-kafka`, and `runtime-ingress-mqtt` as ingress adapters that
  map external payloads into runtime envelopes and backpressure results without
  owning downstream sink delivery or health endpoints.
- `runtime-app` as the JDK 21 standalone collector assembly for TCP, HTTP,
  Kafka, and MQTT ingress, app-level protocol selection, sink routing,
  lifecycle/status output, file/logging/in-memory sinks, sink failure
  isolation, and app-local health/readiness calculation.
- `runtime-smoke-tests` as repository-only cross-module verification for TCP
  ingress, runtime-core, runtime protocol bindings, the standalone app, and
  health/status evidence.

Out of scope:

- Spring Boot or any application framework.
- Database, Redis, durable queue, object storage, external storage sinks,
  retry stores, dashboards, or durable health history.
- HTTP management APIs, metrics exporters, OpenTelemetry exporters, or
  externally hosted health endpoints.
- Kafka producer/sink publishing, MQTT publisher/sink behavior, dead-letter
  topics, retry topics, schema registry integration, Kafka Streams, or Kafka
  transactions.
- Serial port implementation for IEC101/IEC103.
- UDP ingress for Modbus UDP.
- New parser behavior inside `protocol-sdk`.

## Publishing Policy

The selected `0.10.0` policy is to publish the runtime library, ingress
adapter, and application assembly modules as one versioned Maven reactor
release:

| Module | Publish at `0.10.0` | Release posture |
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
| `runtime-app` | Yes | Standalone collector assembly with app-owned health/readiness and status output. |
| `runtime-smoke-tests` | No | Test-only integration module; `maven.deploy.skip=true` and `central.skipPublishing=true` are both set. |

`runtime-app` remains the deployable assembly boundary. It may combine ingress
adapters, protocol bindings, app-level configuration, app-level sinks,
operator-facing status, and app-local health/readiness calculation. It must not
move adapter, management, exporter, broker, database, or Redis dependencies
into `runtime-core`, `runtime-protocol-*`, or `protocol-sdk`.

## Baseline Gates

| Gate | Release evidence | Decision |
| --- | --- | --- |
| Maven development line | Root and module parent versions are `0.10.0-SNAPSHOT` on the readiness branch. | Complete. |
| Runtime core dependency isolation | `runtime-core` has no compile dependencies on Spring, Netty, Kafka, MQTT, HTTP, database, Redis, storage, management endpoints, or exporters. | Complete. |
| Protocol binding dependency isolation | `runtime-protocol-*` modules depend only on `runtime-core`, released `protocol-sdk` parser artifacts, and tests. | Complete. |
| App-local health model | `CollectorHealthSnapshot`, `CollectorHealthState`, and `CollectorReadinessState` are owned by `runtime-app` and derived from `CollectorStatusSnapshot`. | Complete. |
| Lifecycle health mapping | Configured, starting, running, stopping, stopped, and failed collectors map to explicit health/readiness states. | Complete. |
| Readiness blocking reasons | Missing listeners, non-running listeners, missing file sink status, and closed file sinks produce `NOT_READY` reasons. | Complete. |
| Degraded reasons | Parser failures, sink failures, retry-later backpressure, and drop backpressure produce degraded health reasons without blocking readiness when ingress remains available. | Complete. |
| Status output | `CollectorStatusFormatter` includes `health`, `readiness`, and `healthReasons` in the existing single-line status output. | Complete. |
| Operator documentation | English and Chinese guides document the health/readiness matrix, status examples, reason catalog, and triage order. | Complete. |
| Smoke coverage | `runtime-smoke-tests` proves standalone TCP/IEC104 health status across healthy/ready and degraded/ready parser-failure states. | Complete. |
| Release metadata | Maven Central metadata, source/javadoc jars, GPG signing, and Central publishing profile remain configured. | Complete. |
| CI action runtime | GitHub Actions uses `actions/checkout@v6` and `actions/setup-java@v5` with Temurin JDK 21. | Complete. |

## Test Coverage Evidence

`runtime-app/src/test/java/.../StandaloneCollectorTest.java` covers the 0.10.0
application health/status boundary:

- configured collectors report `CONFIGURED` and `NOT_READY`,
- running collectors report `HEALTHY` and `READY`,
- status formatting includes `health`, `readiness`, and `healthReasons`,
- parse failures degrade health while preserving readiness,
- retry-later backpressure degrades health while preserving readiness,
- startup failures, port conflicts, and partial listener startup failures
  report `FAILED` and `NOT_READY`, and
- existing TCP, HTTP, Kafka, and MQTT app assembly behavior remains covered.

`runtime-app/src/test/java/.../RuntimeSinksTest.java` covers sink failure
health evidence:

- sink exceptions are isolated at the app boundary,
- sink failure metrics are recorded, and
- sink failures degrade health and can block readiness when the configured sink
  is not usable.

`runtime-smoke-tests/src/test/java/.../RuntimeAppHealthStatusSmokeTest.java`
covers repository-level cross-module health evidence:

- a real standalone TCP/IEC104 collector starts on an ephemeral localhost port,
- the configured state is `CONFIGURED` and `NOT_READY`,
- the started collector is `HEALTHY` and `READY`,
- a valid IEC104 frame reaches the in-memory record sink, and
- a malformed IEC104 frame reaches the failure sink and changes health to
  `DEGRADED` while readiness remains `READY`.

The operator guides are maintained in
[`status-health-readiness.md`](status-health-readiness.md) and
[`status-health-readiness.zh-CN.md`](status-health-readiness.zh-CN.md).

## Release Verification Checklist

Run these checks on the final `0.10.0-SNAPSHOT` readiness commit:

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

mvn -q -pl runtime-ingress-tcp-netty,runtime-ingress-http,runtime-ingress-kafka,runtime-ingress-mqtt \
  dependency:tree -Dscope=compile

mvn -q -pl runtime-app -am dependency:tree -Dscope=compile

mvn -q -pl runtime-smoke-tests dependency:tree -Dscope=test
```

The Central profile command above is intentionally a smoke check with
publishing disabled and signing skipped. A real release still requires a signed
dry run:

```bash
mvn -Pcentral-release -Dcentral.skipPublishing=true clean deploy
```

That signed dry run must pass before any real Central upload.

## Readiness Branch Checks On 2026-06-10

These checks passed on the readiness branch before opening the
release-readiness PR:

| Check | Result | Note |
| --- | --- | --- |
| Maven reactor version | Passed | Root and module parent versions remain `0.10.0-SNAPSHOT` on the readiness branch. |
| `git diff --check` | Passed | No whitespace errors exist in the readiness diff. |
| `mvn -q verify` | Passed | Full JDK 21+ reactor verification passed. |
| `mvn -q -Pcentral-release -Dgpg.skip=true -Dcentral.skipPublishing=true deploy` | Passed | Central profile smoke passed with publishing disabled and signing skipped. |
| `JAVA_BIN=/opt/homebrew/Cellar/openjdk/23.0.2/libexec/openjdk.jdk/Contents/Home/bin/java sh examples/smoke-standalone.sh` | Passed | Standalone TCP collector built the current `runtime-app` standalone jar, started on an ephemeral localhost TCP port, accepted the IEC104 example frame, and wrote a parsed record to the file sink. |
| `JAVA_BIN=/opt/homebrew/Cellar/openjdk/23.0.2/libexec/openjdk.jdk/Contents/Home/bin/java sh examples/smoke-standalone-http.sh` | Passed | Standalone HTTP collector built the current `runtime-app` standalone jar, started on an ephemeral localhost HTTP port, accepted an IEC104 POST payload, and wrote a parsed record to the file sink. |
| Dependency boundary checks | Passed | `runtime-core` has no compile dependencies; `runtime-protocol-*` modules depend only on `runtime-core` and protocol SDK artifacts; adapter dependencies remain isolated to adapter modules, app assembly, or test-only smoke coverage. |
| CI workflow action versions | Passed | `.github/workflows/ci.yml` uses `actions/checkout@v6`, `actions/setup-java@v5`, and Temurin JDK 21. |

The standalone smoke scripts used the Homebrew JDK 23 `JAVA_BIN` explicitly.

## Release Branch Entry Criteria

`0.10.0` can move to a release branch after:

- this readiness PR merges into `main`,
- GitHub Actions passes on the merged readiness commit,
- all readiness branch checks above pass locally, and
- the release branch changes Maven reactor versions from `0.10.0-SNAPSHOT` to
  `0.10.0`.

No tag should be created and no real Maven Central upload should happen during
readiness or release branch PR work.
