# Runtime 0.5.0 Release Readiness Audit

This note records the release-readiness decision for the `0.5.0`
`protocol-runtime` release target.

The readiness branch keeps the Maven reactor version at `0.5.0-SNAPSHOT`.
The release branch fixes the Maven reactor version at `0.5.0`. No tag is
created and no real Maven Central upload is part of readiness or release branch
PR work.

## Release Scope

`0.5.0` moves the runtime from the published multi-protocol collector baseline
to the first adapter-boundary release. It should present:

- `runtime-core` as the dependency-light runtime contract module.
- `runtime-protocol-iec104`, `runtime-protocol-iec101`,
  `runtime-protocol-iec103`, and `runtime-protocol-modbus` as parser-binding
  modules around the published `protocol-sdk:0.7.0` artifacts.
- `runtime-ingress-tcp-netty` as the existing TCP/Netty byte-ingress adapter.
- `runtime-ingress-http` as the first JDK `HttpServer` HTTP ingress baseline.
  It accepts POST bodies, maps them to `IngressEnvelope`, resolves source ids
  from configured/header/path sources, enforces payload limits, and maps
  backpressure decisions to HTTP responses.
- `runtime-app` as the JDK 21 standalone collector assembly from the `0.4.0`
  line. The existing TCP collector path remains compatible.
- `runtime-smoke-tests` as repository-only cross-module verification for TCP
  ingress, runtime-core, runtime protocol bindings, and the standalone app.
- HTTP, Kafka, and MQTT adapter boundary documents that keep broker, network,
  and acknowledgement/session policy out of `runtime-core`.

Out of scope:

- Spring Boot or any application framework.
- Kafka client implementation, MQTT client implementation, database, Redis,
  durable queue, object storage, or external sink adapters.
- Kafka sink publishing, MQTT publishing, dead-letter topics, retry topics, or
  broker-backed production delivery.
- Metrics exporters, HTTP management APIs, and operational dashboards.
- Serial port implementation for IEC101/IEC103.
- UDP ingress for Modbus UDP.
- TLS, production secrets management, and durable reconnect scheduling.
- New parser behavior inside `protocol-sdk`.

## Publishing Policy

The selected `0.5.0` policy is to publish the runtime library and application
assembly modules as one versioned Maven reactor release:

| Module | Publish at `0.5.0` | Release posture |
| --- | --- | --- |
| `protocol-runtime` | Yes | Parent POM for repository builds and release metadata. |
| `runtime-core` | Yes | Stable baseline runtime contracts. |
| `runtime-protocol-iec104` | Yes | IEC104 runtime binding against `protocol-iec104:0.7.0`. |
| `runtime-protocol-iec101` | Yes | IEC101 runtime binding against `protocol-iec101:0.7.0`. |
| `runtime-protocol-iec103` | Yes | IEC103 runtime binding against `protocol-iec103:0.7.0`. |
| `runtime-protocol-modbus` | Yes | Modbus runtime binding against `protocol-modbus:0.7.0`. |
| `runtime-ingress-tcp-netty` | Yes | TCP/Netty ingress adapter retained for runtime app assembly. |
| `runtime-ingress-http` | Yes | New JDK `HttpServer` HTTP ingress adapter baseline. |
| `runtime-app` | Yes | Standalone collector assembly with app-level protocol selection. |
| `runtime-smoke-tests` | No | Test-only integration module; `maven.deploy.skip=true` and `central.skipPublishing=true` are both set. |

`runtime-app` remains the assembly boundary. It may combine ingress, protocol
bindings, app-level configuration, and app-level sinks, but it must not move
those adapter dependencies into `runtime-core` or `protocol-sdk`.

## Baseline Gates

| Gate | Release evidence | Decision |
| --- | --- | --- |
| Maven development line | Root and module parent versions are `0.5.0-SNAPSHOT` on the readiness branch. | Complete. |
| HTTP ingress module | `runtime-ingress-http` is part of the Maven reactor and uses JDK `HttpServer` without third-party HTTP dependencies. | Complete. |
| HTTP request mapping | `HttpIngressHandler` maps POST request payloads to `IngressEnvelope` with `transport=http`, source id attributes, method/path/query/content-type metadata, listener name, and selected protocol. | Complete. |
| HTTP source mapping | Configured, header, and path source id modes are implemented and covered by unit tests. | Complete. |
| HTTP response and backpressure policy | `ACCEPT`, `DROP`, and `RETRY_LATER` decisions map to adapter-owned HTTP responses; payload limit and invalid-source failures stop before parsing. | Complete. |
| HTTP design contract | [`runtime-ingress-http-design.md`](runtime-ingress-http-design.md) reflects the JDK `HttpServer` baseline and deferred decisions. | Complete. |
| Kafka design contract | [`runtime-ingress-kafka-design.md`](runtime-ingress-kafka-design.md) defines topic/partition/offset attributes, source mapping, commit timing, replay posture, backpressure, and parse-failure routing without adding Kafka dependencies. | Complete. |
| MQTT design contract | [`runtime-ingress-mqtt-design.md`](runtime-ingress-mqtt-design.md) defines topic/source mapping, QoS posture, retained/duplicate message policy, reconnect/session ownership, backpressure, and parse-failure routing without adding MQTT dependencies. | Complete. |
| Existing protocol bindings | IEC104, IEC101, IEC103, and Modbus runtime binding behavior from `0.4.0` remains unchanged and covered by existing tests. | Complete. |
| Existing TCP/app behavior | TCP/Netty ingress, runtime-app, and smoke-test behavior from `0.4.0` remains compatible. | Complete. |
| Dependency boundaries | `runtime-core` remains free of Netty, protocol SDK modules, Spring, Kafka, MQTT, HTTP, database, Redis, and observability exporter dependencies. | Complete. |
| Protocol binding boundaries | `runtime-protocol-*` modules depend only on `runtime-core` and their corresponding `protocol-sdk` parser artifacts. | Complete. |
| Adapter boundaries | Kafka and MQTT dependencies are documented as future adapter-module dependencies only. No Kafka or MQTT client dependency is present in the current reactor. | Complete. |
| Release metadata | Maven Central metadata, source/javadoc jars, GPG signing, and Central publishing profile remain configured. | Complete. |
| CI action runtime | GitHub Actions workflow remains compatible with current GitHub runner action requirements. | Complete. |

## Test Coverage Evidence

`runtime-ingress-http/src/test/java/.../HttpIngressServerTest.java` covers:

- local port `0` binding through JDK `HttpServer`,
- configured, header, and path source id resolution,
- request attributes and payload mapping to `IngressEnvelope`,
- unsupported method rejection,
- invalid source id rejection,
- payload limit rejection,
- `ACCEPT`, `DROP`, and `RETRY_LATER` backpressure response mapping,
- no-body response mode,
- idempotent start/stop behavior, and
- public `HttpIngressModule` factories.

`runtime-app/src/test/java/.../StandaloneCollectorTest.java` covers the
existing standalone collector boundary:

- legacy IEC104 default configuration,
- app-level protocol selection for IEC101, IEC103, and Modbus,
- invalid protocol validation,
- in-memory and file sink record routing,
- malformed frame failure routing,
- fixed and payload-size backpressure behavior,
- named multi-source/listener configuration parsing,
- multi-listener startup and source-specific routing,
- active session cleanup, graceful stop, port conflict, partial startup
  rollback, stop idempotency, restart rejection, and startup validation errors.

`runtime-smoke-tests/src/test/java/.../Iec104TcpRuntimeSmokeTest.java` covers:

- IEC104 TCP bytes through the runtime pipeline,
- split TCP reads,
- backpressure that prevents parsing,
- parse failure routing, and
- real localhost TCP socket parsing with runner stop on disconnect.

`runtime-smoke-tests/src/test/java/.../MultiProtocolTcpRuntimeSmokeTest.java`
covers:

- IEC101, IEC103, and Modbus TCP bytes through `TcpNettyIngressHandler`,
  `RuntimePipelineRunner`, and the selected runtime binding,
- embedded channel verification, and
- real localhost TCP socket verification.

The Kafka and MQTT boundaries are design-only in `0.5.0`; they intentionally
have no runtime module tests until an implementation module is introduced.

## Release Verification Checklist

Run these checks on the final `0.5.0-SNAPSHOT` readiness commit:

```bash
git diff --check

mvn -q verify

mvn -q -Pcentral-release \
  -Dgpg.skip=true \
  -Dcentral.skipPublishing=true \
  deploy

JAVA_BIN=/path/to/jdk-21-or-newer/bin/java sh examples/smoke-standalone.sh

mvn -pl runtime-core dependency:tree -Dscope=compile

mvn -pl runtime-protocol-iec104,runtime-protocol-iec101,runtime-protocol-iec103,runtime-protocol-modbus \
  dependency:tree \
  -Dscope=compile

mvn -pl runtime-ingress-http dependency:tree -Dscope=compile

mvn -pl runtime-app -am dependency:tree \
  -Dscope=compile \
  '-Dincludes=io.netty:*,io.github.qbsstg:*,org.apache.kafka:*,org.eclipse.paho:*'

mvn -pl runtime-smoke-tests -am dependency:tree \
  -Dscope=test \
  '-Dincludes=io.netty:*,io.github.qbsstg:*,org.apache.kafka:*,org.eclipse.paho:*'
```

The Central profile command above is intentionally a smoke check with
publishing disabled and signing skipped. A real release still requires a signed
dry run:

```bash
mvn -Pcentral-release -Dcentral.skipPublishing=true clean deploy
```

That signed dry run must pass before any real Central upload.

## Readiness Branch Checks On 2026-06-08

These checks passed on the readiness branch before opening the release-readiness
PR:

| Check | Result | Note |
| --- | --- | --- |
| Maven reactor version | Passed | Root and module parent versions remain `0.5.0-SNAPSHOT` on the readiness branch. |
| `git diff --check` | Passed | No whitespace errors exist in the readiness diff. |
| `mvn -q verify` | Passed | Full JDK 21+ reactor verification passed. |
| `mvn -q -Pcentral-release -Dgpg.skip=true -Dcentral.skipPublishing=true deploy` | Passed | Central profile smoke passed with publishing disabled and signing skipped. |
| `JAVA_BIN=/opt/homebrew/Cellar/openjdk/23.0.2/libexec/openjdk.jdk/Contents/Home/bin/java sh examples/smoke-standalone.sh` | Passed | Standalone collector built the current `runtime-app` standalone jar, started on an ephemeral localhost TCP port, accepted the IEC104 example frame, and wrote a parsed record to the file sink. |
| Dependency boundary checks | Passed | `runtime-core` has no compile dependencies; `runtime-ingress-http` depends only on `runtime-core`; `runtime-protocol-*` modules depend only on `runtime-core` and protocol SDK artifacts; no Kafka or MQTT client dependencies are present; Netty remains in TCP ingress, app, and smoke-test scopes. |

## Release Branch Entry Criteria

`0.5.0` can move to a release branch after:

- this readiness PR merges into `main`,
- GitHub Actions passes on the merged readiness commit,
- all readiness branch checks above pass locally, and
- the release branch changes Maven reactor versions from `0.5.0-SNAPSHOT` to
  `0.5.0`.

No tag is created and no real Maven Central upload is part of this readiness
work.
