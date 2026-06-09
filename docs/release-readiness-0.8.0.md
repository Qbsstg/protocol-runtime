# Runtime 0.8.0 Release Readiness Audit

This note records the release-readiness decision for the `0.8.0`
`protocol-runtime` release target.

The readiness branch keeps the Maven reactor version at `0.8.0-SNAPSHOT`.
The release branch fixes the Maven reactor version at `0.8.0`. Readiness and
release branch PR work did not create a tag or perform a real Maven Central
upload; final publication happened after the release PR merged to `main`.

## Release Scope

`0.8.0` moves the runtime from the published Kafka collector release to the
first MQTT ingress and standalone MQTT collector assembly baseline. It should
present:

- `runtime-core` as the dependency-light runtime contract module with no
  adapter dependencies.
- `runtime-protocol-iec104`, `runtime-protocol-iec101`,
  `runtime-protocol-iec103`, and `runtime-protocol-modbus` as parser-binding
  modules around the published `protocol-sdk:0.7.0` artifacts.
- `runtime-ingress-tcp-netty` as the existing TCP/Netty byte-ingress adapter.
- `runtime-ingress-http` as the existing JDK `HttpServer` HTTP ingress
  adapter.
- `runtime-ingress-kafka` as the existing Kafka ingress adapter.
- `runtime-ingress-mqtt` as the MQTT ingress adapter. It owns the Paho MQTT
  client dependency, message-to-envelope mapping, configured/topic source id
  modes, client lifecycle, MQTT message attributes, and backpressure result
  mapping.
- `runtime-app` as the JDK 21 standalone collector assembly for TCP, HTTP,
  Kafka, and MQTT ingress. MQTT-only configurations do not implicitly open the
  legacy TCP listener.
- `runtime-smoke-tests` as repository-only cross-module verification for TCP
  ingress, runtime-core, runtime protocol bindings, and the standalone app.

Out of scope:

- Spring Boot or any application framework.
- Database, Redis, durable queue, object storage, or external sink adapters.
- MQTT publishing/sink behavior, broker-backed MQTT smoke tests in normal
  `mvn verify`, TLS, authentication, secret management, and advanced reconnect
  hardening.
- Kafka producer/sink publishing, dead-letter topics, retry topics, schema
  registry integration, Kafka Streams, or Kafka transactions.
- HTTP management APIs, metrics exporters, dashboards, and health endpoints.
- Serial port implementation for IEC101/IEC103.
- UDP ingress for Modbus UDP.
- New parser behavior inside `protocol-sdk`.

## Publishing Policy

The selected `0.8.0` policy is to publish the runtime library, ingress adapter,
and application assembly modules as one versioned Maven reactor release:

| Module | Publish at `0.8.0` | Release posture |
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
| `runtime-app` | Yes | Standalone collector assembly with TCP, HTTP, Kafka, and MQTT app-level protocol selection. |
| `runtime-smoke-tests` | No | Test-only integration module; `maven.deploy.skip=true` and `central.skipPublishing=true` are both set. |

`runtime-app` remains the deployable assembly boundary. It may combine ingress
adapters, protocol bindings, app-level configuration, and app-level sinks, but
it must not move those adapter dependencies into `runtime-core`,
`runtime-protocol-*`, or `protocol-sdk`.

## Baseline Gates

| Gate | Release evidence | Decision |
| --- | --- | --- |
| Maven development line | Root and module parent versions are `0.8.0-SNAPSHOT` on the readiness branch. | Complete. |
| MQTT adapter dependency isolation | `runtime-ingress-mqtt` owns `org.eclipse.paho:org.eclipse.paho.client.mqttv3`; `runtime-core` and `runtime-protocol-*` do not depend on MQTT or Paho. | Complete. |
| MQTT message mapping | `MqttMessageEnvelopeMapper` maps payload, topic, packet id, QoS, retained flag, duplicate flag, protocol, source id mode, client identity, and source attributes into `IngressEnvelope`. | Complete. |
| MQTT source id modes | Configured source id and topic-derived source id modes are supported by the adapter. | Complete. |
| MQTT backpressure results | `MqttMessageHandler` maps `ACCEPT`, `DROP`, and `RETRY_LATER` runtime decisions into MQTT ingress results, including acknowledge posture. | Complete. |
| MQTT client lifecycle | `MqttPahoMessageSource` owns Paho client connect, subscribe, callback, stop, and startup failure handling without adding broker APIs to `runtime-core`. | Complete. |
| MQTT app configuration | `StandaloneCollectorConfig` supports named `collector.mqtt.clients` with broker URI, client id, topic filters, QoS, source binding, source id mode, clean session, automatic reconnect, connection timeout, and keep alive settings. | Complete. |
| MQTT-only compatibility | Declaring MQTT clients without TCP, HTTP, or Kafka declarations starts MQTT only and preserves the legacy TCP path for existing single-source configs. | Complete. |
| MQTT app assembly | `StandaloneCollector` creates MQTT client runtimes, selected protocol bindings, shared sinks, and shared backpressure strategy through `runtime-ingress-mqtt`. | Complete. |
| MQTT status snapshot | `CollectorStatusSnapshot` and `CollectorStatusFormatter` include MQTT client status without changing `runtime-core`. | Complete. |
| MQTT examples | `examples/collector-mqtt.properties` provides a minimal standalone MQTT collector configuration. | Complete. |
| Existing TCP/HTTP/Kafka behavior | Existing TCP/Netty, HTTP, and Kafka runtime-app behavior remains compatible. | Complete. |
| Release metadata | Maven Central metadata, source/javadoc jars, GPG signing, and Central publishing profile remain configured. | Complete. |
| CI action runtime | GitHub Actions uses `actions/checkout@v6` and `actions/setup-java@v5` with Temurin JDK 21. | Complete. |

## Test Coverage Evidence

`runtime-ingress-mqtt/src/test/java/.../MqttMessageHandlerTest.java` covers the
MQTT adapter boundary:

- configured and topic source id resolution,
- MQTT topic, packet id, QoS, retained flag, duplicate flag, source, client,
  and protocol attributes,
- invalid source handling,
- backpressure result mapping,
- public `MqttIngressModule` factories, and
- Paho source startup failure recording.

`runtime-app/src/test/java/.../StandaloneCollectorTest.java` covers the MQTT
app assembly boundary:

- MQTT-only app configuration without implicit default TCP, HTTP, or Kafka
  listener,
- MQTT client parsing and validation,
- fake MQTT message dispatch through `RuntimePipelineRunner` and the IEC104
  parser binding to the in-memory record sink,
- topic-derived MQTT source id dispatch with the configured protocol,
- malformed MQTT payload routing to the failure sink,
- `RETRY_LATER` MQTT backpressure without parsing and without acknowledge
  permission, and
- MQTT client status snapshot and formatter output.

The same test class continues to cover the existing standalone collector
boundary for TCP, HTTP, Kafka, protocol selection, sinks, parse failures,
lifecycle, status snapshots, and backpressure behavior.

Broker-backed MQTT smoke tests are intentionally not required in normal
`mvn verify`. The first `0.8.0` baseline proves the application boundary with a
fake source while leaving production MQTT operational hardening to later
release lines.

## Release Verification Checklist

Run these checks on the final `0.8.0-SNAPSHOT` readiness commit:

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

mvn -pl runtime-protocol-iec104,runtime-protocol-iec101,runtime-protocol-iec103,runtime-protocol-modbus \
  dependency:tree -Dscope=compile

mvn -pl runtime-ingress-tcp-netty,runtime-ingress-http,runtime-ingress-kafka \
  dependency:tree -Dscope=compile

mvn -pl runtime-ingress-mqtt dependency:tree -Dscope=compile

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
| Maven reactor version | Passed | Root and module parent versions remain `0.8.0-SNAPSHOT` on the readiness branch. |
| `git diff --check` | Passed | No whitespace errors exist in the readiness diff. |
| `mvn -q verify` | Passed | Full JDK 21+ reactor verification passed. |
| `mvn -q -Pcentral-release -Dgpg.skip=true -Dcentral.skipPublishing=true deploy` | Passed | Central profile smoke passed with publishing disabled and signing skipped. |
| `JAVA_BIN=/opt/homebrew/Cellar/openjdk/23.0.2/libexec/openjdk.jdk/Contents/Home/bin/java sh examples/smoke-standalone.sh` | Passed | Standalone TCP collector built the current `runtime-app` standalone jar, started on an ephemeral localhost TCP port, accepted the IEC104 example frame, and wrote a parsed record to the file sink. |
| `JAVA_BIN=/opt/homebrew/Cellar/openjdk/23.0.2/libexec/openjdk.jdk/Contents/Home/bin/java sh examples/smoke-standalone-http.sh` | Passed | Standalone HTTP collector built the current `runtime-app` standalone jar, started on an ephemeral localhost HTTP port, accepted an IEC104 POST payload, and wrote a parsed record to the file sink. |
| Dependency boundary checks | Passed | `runtime-core` has no compile dependencies; `runtime-protocol-*` modules depend only on `runtime-core` and protocol SDK artifacts; `runtime-ingress-mqtt` depends only on `runtime-core` and Paho; MQTT also appears in `runtime-app` assembly only; TCP/HTTP/Kafka dependencies remain isolated to their adapter modules and app assembly. |
| CI workflow action versions | Passed | `.github/workflows/ci.yml` uses `actions/checkout@v6`, `actions/setup-java@v5`, and Temurin JDK 21. |

The default shell `java` was JDK 17 during this audit, so the standalone smoke
scripts used the Homebrew JDK 23 `JAVA_BIN` explicitly.

## Release Branch Entry Criteria

`0.8.0` moved to a release branch after:

- this readiness PR merges into `main`,
- GitHub Actions passes on the merged readiness commit,
- all readiness branch checks above pass locally, and
- the release branch changes Maven reactor versions from `0.8.0-SNAPSHOT` to
  `0.8.0`.

## Release Branch Checks On 2026-06-09

These checks passed on the `0.8.0` release branch before opening the release
PR:

| Check | Result | Note |
| --- | --- | --- |
| Maven reactor version | Passed | Root and module parent versions are fixed at `0.8.0`. |
| `git diff --check` | Passed | No whitespace errors exist in the release diff. |
| `mvn -q verify` | Passed | Full JDK 21+ reactor verification passed at version `0.8.0`. |
| `mvn -q -Pcentral-release -Dgpg.skip=true -Dcentral.skipPublishing=true deploy` | Passed | Central profile smoke passed with publishing disabled and signing skipped. |
| `JAVA_BIN=/opt/homebrew/Cellar/openjdk/23.0.2/libexec/openjdk.jdk/Contents/Home/bin/java sh examples/smoke-standalone.sh` | Passed | Standalone TCP collector built `runtime-app-0.8.0-standalone.jar`, started on an ephemeral localhost TCP port, accepted the IEC104 example frame, and wrote a parsed record to the file sink. |
| `JAVA_BIN=/opt/homebrew/Cellar/openjdk/23.0.2/libexec/openjdk.jdk/Contents/Home/bin/java sh examples/smoke-standalone-http.sh` | Passed | Standalone HTTP collector built `runtime-app-0.8.0-standalone.jar`, started on an ephemeral localhost HTTP port, accepted an IEC104 POST payload, and wrote a parsed record to the file sink. |
| Dependency boundary checks | Passed | `runtime-core` has no compile dependencies; `runtime-protocol-*` modules depend only on `runtime-core` and protocol SDK artifacts; `runtime-ingress-mqtt` depends only on `runtime-core` and Paho; MQTT also appears in `runtime-app` assembly only; TCP/HTTP/Kafka dependencies remain isolated to their adapter modules and app assembly. |

No tag was created and no real Maven Central upload was part of the release
branch PR.

## Final Publication On 2026-06-10

`0.8.0` has been tagged, uploaded, manually published, and verified.

| Gate | Result | Note |
| --- | --- | --- |
| Release tag | Passed | `v0.8.0` points to `aa3d8da2761ad4d833a501629d9a3b1b8b30a1a2`. |
| Signed dry run | Passed | `mvn -Pcentral-release -Dcentral.skipPublishing=true clean deploy` signed artifacts and completed without upload. |
| Central upload | Passed | `mvn -Pcentral-release clean deploy` created deployment `f2b54d7a-924f-44f2-bbd9-6199fa1514a3`. |
| Central publish | Passed | Deployment `f2b54d7a-924f-44f2-bbd9-6199fa1514a3` reached `PUBLISHED`. |
| GitHub Release | Passed | `v0.8.0` GitHub Release was created after Central publish. |
| Maven Central resolution | Passed | `runtime-core`, `runtime-ingress-mqtt`, `runtime-app`, and the `runtime-app` `standalone` classifier resolved from Maven Central with isolated local Maven repositories. |

The next development line is `0.9.0-SNAPSHOT`.
