# Runtime 0.7.0 Release Readiness Audit

This note records the release-readiness decision for the `0.7.0`
`protocol-runtime` release target.

The readiness branch keeps the Maven reactor version at `0.7.0-SNAPSHOT`.
The release branch fixes the Maven reactor version at `0.7.0`. No tag is
created and no real Maven Central upload is part of readiness or release branch
PR work.

## Release Scope

`0.7.0` moves the runtime from the published HTTP collector release to the
first Kafka ingress and standalone Kafka collector assembly baseline. It should
present:

- `runtime-core` as the dependency-light runtime contract module with no
  adapter dependencies.
- `runtime-protocol-iec104`, `runtime-protocol-iec101`,
  `runtime-protocol-iec103`, and `runtime-protocol-modbus` as parser-binding
  modules around the published `protocol-sdk:0.7.0` artifacts.
- `runtime-ingress-tcp-netty` as the existing TCP/Netty byte-ingress adapter.
- `runtime-ingress-http` as the published JDK `HttpServer` HTTP ingress
  adapter.
- `runtime-ingress-kafka` as the Kafka ingress adapter. It owns the Kafka
  client dependency, record-to-envelope mapping, source id modes, polling
  source lifecycle, backpressure result mapping, and commit-mode decisions.
- `runtime-app` as the JDK 21 standalone collector assembly for TCP, HTTP, and
  Kafka ingress. Kafka-only configurations do not implicitly open the legacy TCP
  listener.
- `runtime-smoke-tests` as repository-only cross-module verification for TCP
  ingress, runtime-core, runtime protocol bindings, and the standalone app.

Out of scope:

- Spring Boot or any application framework.
- MQTT client implementation, database, Redis, durable queue, object storage,
  or external sink adapters.
- Kafka producer/sink publishing, dead-letter topics, retry topics, schema
  registry integration, Kafka Streams, or Kafka transactions.
- Live Kafka broker integration tests in normal `mvn verify`.
- HTTP management APIs, metrics exporters, dashboards, and health endpoints.
- Serial port implementation for IEC101/IEC103.
- UDP ingress for Modbus UDP.
- TLS, authentication, authorization, production secrets management, and
  durable reconnect scheduling.
- New parser behavior inside `protocol-sdk`.

## Publishing Policy

The selected `0.7.0` policy is to publish the runtime library, ingress adapter,
and application assembly modules as one versioned Maven reactor release:

| Module | Publish at `0.7.0` | Release posture |
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
| `runtime-app` | Yes | Standalone collector assembly with TCP, HTTP, and Kafka app-level protocol selection. |
| `runtime-smoke-tests` | No | Test-only integration module; `maven.deploy.skip=true` and `central.skipPublishing=true` are both set. |

`runtime-app` remains the deployable assembly boundary. It may combine ingress
adapters, protocol bindings, app-level configuration, and app-level sinks, but
it must not move those adapter dependencies into `runtime-core`,
`runtime-protocol-*`, or `protocol-sdk`.

## Baseline Gates

| Gate | Release evidence | Decision |
| --- | --- | --- |
| Maven development line | Root and module parent versions are `0.7.0-SNAPSHOT` on the readiness branch. | Complete. |
| Kafka adapter dependency isolation | `runtime-ingress-kafka` owns `org.apache.kafka:kafka-clients`; `runtime-core` and `runtime-protocol-*` do not depend on Kafka. | Complete. |
| Kafka record mapping | `KafkaRecordEnvelopeMapper` maps payload, topic, partition, offset, timestamp, key, headers, protocol, source id mode, and source attributes into `IngressEnvelope`. | Complete. |
| Kafka source id modes | Configured, header, topic, and key source id modes are supported by the adapter. | Complete. |
| Kafka backpressure results | `KafkaRecordHandler` maps `ACCEPT`, `DROP`, and `RETRY_LATER` runtime decisions into Kafka ingress results. | Complete. |
| Kafka polling source lifecycle | `KafkaPollingRecordSource` provides minimal consumer lifecycle and commit-on-accepted-record behavior without adding broker APIs to `runtime-core`. | Complete. |
| Kafka app configuration | `StandaloneCollectorConfig` supports named `collector.kafka.consumers` with bootstrap servers, group id, topics/topic pattern, source binding, source id mode/header, commit mode, offset reset, max poll records, and poll timeout. | Complete. |
| Kafka-only compatibility | Declaring Kafka consumers without `collector.tcp.listeners` or `collector.http.listeners` starts Kafka only and preserves the legacy TCP path for existing single-source configs. | Complete. |
| Kafka app assembly | `StandaloneCollector` creates Kafka consumer runtimes, selected protocol bindings, shared sinks, and shared backpressure strategy through `runtime-ingress-kafka`. | Complete. |
| Kafka status snapshot | `CollectorStatusSnapshot` and `CollectorStatusFormatter` include Kafka consumer status without changing `runtime-core`. | Complete. |
| Kafka examples | `examples/collector-kafka.properties` provides a minimal standalone Kafka collector configuration. | Complete. |
| Existing TCP/HTTP behavior | Existing TCP/Netty and HTTP runtime-app behavior and smoke scripts remain compatible. | Complete. |
| Release metadata | Maven Central metadata, source/javadoc jars, GPG signing, and Central publishing profile remain configured. | Complete. |
| CI action runtime | GitHub Actions workflow remains compatible with current GitHub runner action requirements. | Complete. |

## Test Coverage Evidence

`runtime-ingress-kafka/src/test/java/.../KafkaRecordHandlerTest.java` covers the
Kafka adapter boundary:

- configured, header, topic, and key source id resolution,
- Kafka topic, partition, offset, key, header, source, and protocol attributes,
- invalid source handling,
- backpressure result mapping,
- commit-mode decisions, and
- public `KafkaIngressModule` factories.

`runtime-app/src/test/java/.../StandaloneCollectorTest.java` covers the Kafka
app assembly boundary:

- Kafka-only app configuration without implicit default TCP or HTTP listener,
- Kafka consumer parsing and validation,
- fake Kafka record dispatch through `RuntimePipelineRunner` and the IEC104
  parser binding to the in-memory record sink,
- malformed Kafka payload routing to the failure sink,
- `RETRY_LATER` Kafka backpressure without parsing, and
- Kafka consumer status snapshot and formatter output.

The same test class continues to cover the existing standalone collector
boundary for TCP, HTTP, protocol selection, sinks, parse failures, lifecycle,
status snapshots, and backpressure behavior.

Broker-backed Kafka smoke tests are intentionally not required in normal
`mvn verify`. The first `0.7.0` baseline proves the application boundary with a
fake source while leaving production Kafka operational hardening to later
release lines.

## Release Verification Checklist

Run these checks on the final `0.7.0-SNAPSHOT` readiness commit:

```bash
git diff --check

mvn -q verify

mvn -q -Pcentral-release \
  -Dgpg.skip=true \
  -Dcentral.skipPublishing=true \
  deploy

JAVA_BIN=/path/to/jdk-21-or-newer/bin/java sh examples/smoke-standalone.sh

JAVA_BIN=/path/to/jdk-21-or-newer/bin/java sh examples/smoke-standalone-http.sh

mvn -pl runtime-core dependency:tree -Dscope=compile

mvn -pl runtime-protocol-iec104 dependency:tree -Dscope=compile

mvn -pl runtime-protocol-iec101 dependency:tree -Dscope=compile

mvn -pl runtime-protocol-iec103 dependency:tree -Dscope=compile

mvn -pl runtime-protocol-modbus dependency:tree -Dscope=compile

mvn -pl runtime-ingress-kafka dependency:tree -Dscope=compile

mvn -pl runtime-app -am dependency:tree -Dscope=compile
```

The Central profile command above is intentionally a smoke check with
publishing disabled and signing skipped. A real release still requires a signed
dry run:

```bash
mvn -Pcentral-release -Dcentral.skipPublishing=true clean deploy
```

That signed dry run must pass before any real Central upload.

## Readiness Branch Checks On 2026-06-09

These checks passed on the readiness branch before opening the release-readiness
PR:

| Check | Result | Note |
| --- | --- | --- |
| Maven reactor version | Passed | Root and module parent versions remain `0.7.0-SNAPSHOT` on the readiness branch. |
| `git diff --check` | Passed | No whitespace errors exist in the readiness diff. |
| `mvn -q verify` | Passed | Full JDK 21+ reactor verification passed. |
| `mvn -q -Pcentral-release -Dgpg.skip=true -Dcentral.skipPublishing=true deploy` | Passed | Central profile smoke passed with publishing disabled and signing skipped. |
| `JAVA_BIN=/opt/homebrew/Cellar/openjdk/23.0.2/libexec/openjdk.jdk/Contents/Home/bin/java sh examples/smoke-standalone.sh` | Passed | Standalone TCP collector built the current `runtime-app` standalone jar, started on an ephemeral localhost TCP port, accepted the IEC104 example frame, and wrote a parsed record to the file sink. |
| `JAVA_BIN=/opt/homebrew/Cellar/openjdk/23.0.2/libexec/openjdk.jdk/Contents/Home/bin/java sh examples/smoke-standalone-http.sh` | Passed | Standalone HTTP collector built the current `runtime-app` standalone jar, started on an ephemeral localhost HTTP port, accepted an IEC104 POST payload, and wrote a parsed record to the file sink. |
| Dependency boundary checks | Passed | `runtime-core` has no compile dependencies; `runtime-protocol-*` modules depend only on `runtime-core` and protocol SDK artifacts; `runtime-ingress-kafka` depends on `runtime-core` and `kafka-clients`; Kafka also appears in `runtime-app` assembly only; Netty remains in TCP ingress and app scopes. |

The default shell `java` was JDK 17 during this audit, so the standalone smoke
scripts used the Homebrew JDK 23 `JAVA_BIN` explicitly.

## Release Branch Entry Criteria

`0.7.0` moved to a release branch after:

- this readiness PR merges into `main`,
- GitHub Actions passes on the merged readiness commit,
- all readiness branch checks above pass locally, and
- the release branch changes Maven reactor versions from `0.7.0-SNAPSHOT` to
  `0.7.0`.

## Release Branch Checks On 2026-06-09

These checks passed on the `0.7.0` release branch before opening the release
PR:

| Check | Result | Note |
| --- | --- | --- |
| Maven reactor version | Passed | Root and module parent versions are fixed at `0.7.0`. |
| `git diff --check` | Passed | No whitespace errors exist in the release diff. |
| `mvn -q verify` | Passed | Full JDK 21+ reactor verification passed at version `0.7.0`. |
| `mvn -q -Pcentral-release -Dgpg.skip=true -Dcentral.skipPublishing=true deploy` | Passed | Central profile smoke passed with publishing disabled and signing skipped. |
| `JAVA_BIN=/opt/homebrew/Cellar/openjdk/23.0.2/libexec/openjdk.jdk/Contents/Home/bin/java sh examples/smoke-standalone.sh` | Passed | Standalone TCP collector built `runtime-app-0.7.0-standalone.jar`, started on an ephemeral localhost TCP port, accepted the IEC104 example frame, and wrote a parsed record to the file sink. |
| `JAVA_BIN=/opt/homebrew/Cellar/openjdk/23.0.2/libexec/openjdk.jdk/Contents/Home/bin/java sh examples/smoke-standalone-http.sh` | Passed | Standalone HTTP collector built `runtime-app-0.7.0-standalone.jar`, started on an ephemeral localhost HTTP port, accepted an IEC104 POST payload, and wrote a parsed record to the file sink. |
| Dependency boundary checks | Passed | `runtime-core` has no compile dependencies; `runtime-protocol-*` modules depend only on `runtime-core` and protocol SDK artifacts; `runtime-ingress-kafka` depends on `runtime-core` and `kafka-clients`; Kafka also appears in `runtime-app` assembly only; Netty remains in TCP ingress and app scopes. |

No tag is created and no real Maven Central upload is part of this readiness
work.
