# Runtime 0.6.0 Release Readiness Audit

This note records the release-readiness decision for the `0.6.0`
`protocol-runtime` release target.

The readiness branch keeps the Maven reactor version at `0.6.0-SNAPSHOT`.
The release branch fixes the Maven reactor version at `0.6.0`. No tag is
created and no real Maven Central upload is part of readiness or release branch
PR work.

## Release Scope

`0.6.0` moves the runtime from the published adapter-boundary release to the
first standalone HTTP collector app assembly. It should present:

- `runtime-core` as the dependency-light runtime contract module with no
  adapter dependencies.
- `runtime-protocol-iec104`, `runtime-protocol-iec101`,
  `runtime-protocol-iec103`, and `runtime-protocol-modbus` as parser-binding
  modules around the published `protocol-sdk:0.7.0` artifacts.
- `runtime-ingress-tcp-netty` as the existing TCP/Netty byte-ingress adapter.
- `runtime-ingress-http` as the JDK `HttpServer` HTTP ingress adapter. It owns
  HTTP request handling, source mapping, request limits, response policy, and
  conversion to runtime envelopes.
- `runtime-app` as the JDK 21 standalone collector assembly for both TCP and
  HTTP ingress. HTTP-only configurations do not implicitly open the legacy TCP
  listener.
- `runtime-smoke-tests` as repository-only cross-module verification for TCP
  ingress, runtime-core, runtime protocol bindings, and the standalone app.

Out of scope:

- Spring Boot or any application framework.
- Kafka client implementation, MQTT client implementation, database, Redis,
  durable queue, object storage, or external sink adapters.
- Kafka sink publishing, MQTT publishing, dead-letter topics, retry topics, or
  broker-backed production delivery.
- HTTP management APIs, metrics exporters, dashboards, and health endpoints.
- Serial port implementation for IEC101/IEC103.
- UDP ingress for Modbus UDP.
- TLS, authentication, authorization, production secrets management, and
  durable reconnect scheduling.
- New parser behavior inside `protocol-sdk`.

## Publishing Policy

The selected `0.6.0` policy is to publish the runtime library and application
assembly modules as one versioned Maven reactor release:

| Module | Publish at `0.6.0` | Release posture |
| --- | --- | --- |
| `protocol-runtime` | Yes | Parent POM for repository builds and release metadata. |
| `runtime-core` | Yes | Stable baseline runtime contracts. |
| `runtime-protocol-iec104` | Yes | IEC104 runtime binding against `protocol-iec104:0.7.0`. |
| `runtime-protocol-iec101` | Yes | IEC101 runtime binding against `protocol-iec101:0.7.0`. |
| `runtime-protocol-iec103` | Yes | IEC103 runtime binding against `protocol-iec103:0.7.0`. |
| `runtime-protocol-modbus` | Yes | Modbus runtime binding against `protocol-modbus:0.7.0`. |
| `runtime-ingress-tcp-netty` | Yes | TCP/Netty ingress adapter retained for runtime app assembly. |
| `runtime-ingress-http` | Yes | JDK `HttpServer` HTTP ingress adapter retained for runtime app assembly. |
| `runtime-app` | Yes | Standalone collector assembly with TCP and HTTP app-level protocol selection. |
| `runtime-smoke-tests` | No | Test-only integration module; `maven.deploy.skip=true` and `central.skipPublishing=true` are both set. |

`runtime-app` remains the assembly boundary. It may combine ingress adapters,
protocol bindings, app-level configuration, and app-level sinks, but it must
not move those adapter dependencies into `runtime-core`, `runtime-protocol-*`,
or `protocol-sdk`.

## Baseline Gates

| Gate | Release evidence | Decision |
| --- | --- | --- |
| Maven development line | Root and module parent versions are `0.6.0-SNAPSHOT` on the readiness branch. | Complete. |
| HTTP app configuration | `StandaloneCollectorConfig` supports named `collector.http.listeners` with host, port, path, source, source id mode/header, payload limit, response mode, backlog, and worker threads. | Complete. |
| HTTP-only compatibility | Declaring HTTP listeners without `collector.tcp.listeners` starts HTTP only and preserves the legacy TCP path for existing single-source configs. | Complete. |
| HTTP app assembly | `StandaloneCollector` creates `HttpIngressServer` instances, routes payloads through selected `RuntimePipelineRunner` bindings, and shares configured sinks/backpressure. | Complete. |
| HTTP lifecycle and rollback | HTTP listeners participate in collector start/stop, startup failure rollback, and port-conflict reporting. | Complete. |
| HTTP status snapshot | `CollectorStatusSnapshot` and `CollectorStatusFormatter` include HTTP listener status without changing `runtime-core`. | Complete. |
| HTTP examples | `examples/collector-http.properties` and `examples/smoke-standalone-http.sh` provide runnable local HTTP collector paths. | Complete. |
| Existing TCP/app behavior | Existing TCP/Netty runtime-app behavior and `examples/smoke-standalone.sh` remain compatible. | Complete. |
| Dependency boundaries | `runtime-core` remains free of Netty, protocol SDK modules, Spring, Kafka, MQTT, HTTP, database, Redis, and observability exporter dependencies. | Complete. |
| Protocol binding boundaries | `runtime-protocol-*` modules depend only on `runtime-core` and their corresponding `protocol-sdk` parser artifacts. | Complete. |
| Adapter boundaries | Kafka and MQTT dependencies remain future dedicated-module dependencies only. No Kafka or MQTT client dependency is present in the current reactor. | Complete. |
| Release metadata | Maven Central metadata, source/javadoc jars, GPG signing, and Central publishing profile remain configured. | Complete. |
| CI action runtime | GitHub Actions workflow remains compatible with current GitHub runner action requirements. | Complete. |

## Test Coverage Evidence

`runtime-app/src/test/java/.../StandaloneCollectorTest.java` now covers the
HTTP app assembly boundary:

- HTTP-only app configuration without implicit default TCP listener,
- HTTP listener parsing and validation,
- HTTP POST IEC104 payload routing to the in-memory record sink,
- header-based source id mapping with app-selected protocol binding,
- malformed HTTP payload routing to the failure sink,
- `RETRY_LATER` HTTP backpressure response without parsing,
- HTTP payload-size rejection before runtime parsing,
- HTTP port conflict startup failure and rollback, and
- HTTP listener status snapshot and formatter output.

The same test class continues to cover the existing standalone collector
boundary:

- legacy IEC104 default configuration,
- app-level protocol selection for IEC101, IEC103, and Modbus,
- invalid protocol validation,
- in-memory and file sink record routing,
- malformed frame failure routing,
- fixed and payload-size backpressure behavior,
- named multi-source/listener TCP configuration parsing,
- multi-listener startup and source-specific routing,
- active session cleanup, graceful stop, port conflict, partial startup
  rollback, stop idempotency, restart rejection, and startup validation errors.

`runtime-ingress-http/src/test/java/.../HttpIngressServerTest.java` covers the
HTTP adapter boundary independently:

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

`runtime-smoke-tests/src/test/java/.../Iec104TcpRuntimeSmokeTest.java` and
`MultiProtocolTcpRuntimeSmokeTest.java` continue to cover TCP ingress, split
reads, backpressure, parse failure routing, real localhost sockets, and
multi-protocol parser bindings behind TCP ingress.

Kafka and MQTT remain design-only in `0.6.0`; they intentionally have no
runtime module tests until dedicated implementation modules are introduced.

## Release Verification Checklist

Run these checks on the final `0.6.0-SNAPSHOT` readiness commit:

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
| Maven reactor version | Passed | Root and module parent versions remain `0.6.0-SNAPSHOT` on the readiness branch. |
| `git diff --check` | Passed | No whitespace errors exist in the readiness diff. |
| `mvn -q verify` | Passed | Full JDK 21+ reactor verification passed. |
| `mvn -q -Pcentral-release -Dgpg.skip=true -Dcentral.skipPublishing=true deploy` | Passed | Central profile smoke passed with publishing disabled and signing skipped. |
| `JAVA_BIN=/opt/homebrew/Cellar/openjdk/23.0.2/libexec/openjdk.jdk/Contents/Home/bin/java sh examples/smoke-standalone.sh` | Passed | Standalone TCP collector built the current `runtime-app` standalone jar, started on an ephemeral localhost TCP port, accepted the IEC104 example frame, and wrote a parsed record to the file sink. |
| `JAVA_BIN=/opt/homebrew/Cellar/openjdk/23.0.2/libexec/openjdk.jdk/Contents/Home/bin/java sh examples/smoke-standalone-http.sh` | Passed | Standalone HTTP collector built the current `runtime-app` standalone jar, started on an ephemeral localhost HTTP port, accepted an IEC104 POST payload, and wrote a parsed record to the file sink. |
| Dependency boundary checks | Passed | `runtime-core` has no compile dependencies; `runtime-ingress-http` depends only on `runtime-core`; `runtime-protocol-*` modules depend only on `runtime-core` and protocol SDK artifacts; no Kafka or MQTT client dependencies are present; Netty remains in TCP ingress, app, and smoke-test scopes. |

The default shell `java` was JDK 17 during this audit, so the standalone smoke
scripts used the Homebrew JDK 23 `JAVA_BIN` explicitly.

## Release Branch Entry Criteria

`0.6.0` can move to a release branch after:

- this readiness PR merges into `main`,
- GitHub Actions passes on the merged readiness commit,
- all readiness branch checks above pass locally, and
- the release branch changes Maven reactor versions from `0.6.0-SNAPSHOT` to
  `0.6.0`.

No tag is created and no real Maven Central upload is part of this readiness
work.

## Release Branch Checks On 2026-06-09

These checks passed on the `0.6.0` release branch before opening the release
PR:

| Check | Result | Note |
| --- | --- | --- |
| Maven reactor version | Passed | Root and module parent versions are fixed at `0.6.0`. |
| `git diff --check` | Passed | No whitespace errors exist in the release diff. |
| `mvn -q verify` | Passed | Full JDK 21+ reactor verification passed at version `0.6.0`. |
| `mvn -q -Pcentral-release -Dgpg.skip=true -Dcentral.skipPublishing=true deploy` | Passed | Central profile smoke passed with publishing disabled and signing skipped. |
| `JAVA_BIN=/opt/homebrew/Cellar/openjdk/23.0.2/libexec/openjdk.jdk/Contents/Home/bin/java sh examples/smoke-standalone.sh` | Passed | Standalone TCP collector built `runtime-app-0.6.0-standalone.jar`, started on an ephemeral localhost TCP port, accepted the IEC104 example frame, and wrote a parsed record to the file sink. |
| `JAVA_BIN=/opt/homebrew/Cellar/openjdk/23.0.2/libexec/openjdk.jdk/Contents/Home/bin/java sh examples/smoke-standalone-http.sh` | Passed | Standalone HTTP collector built `runtime-app-0.6.0-standalone.jar`, started on an ephemeral localhost HTTP port, accepted an IEC104 POST payload, and wrote a parsed record to the file sink. |
| Dependency boundary checks | Passed | `runtime-core` has no compile dependencies; `runtime-ingress-http` depends only on `runtime-core`; `runtime-protocol-*` modules depend only on `runtime-core` and protocol SDK artifacts; no Kafka or MQTT client dependencies are present; Netty remains in TCP ingress and app scopes. |

No tag was created and no real Maven Central upload was part of the release
branch PR.

## Published Release Verification On 2026-06-09

`0.6.0` has been tagged, uploaded, manually published, and verified.

| Check | Result | Note |
| --- | --- | --- |
| Release tag | Passed | `v0.6.0` points at `3fb6af480f16e9c95d075c6eb6d8c76b78e429dc`. |
| Central upload | Passed | `mvn -Pcentral-release clean deploy` created deployment `7b908e63-6006-4ecb-9b87-d099d89582be`. |
| Central publish | Passed | Deployment `7b908e63-6006-4ecb-9b87-d099d89582be` reached `PUBLISHED`. |
| GitHub Release | Passed | `v0.6.0` GitHub Release was created after Central publish. |
| Maven Central resolution | Passed | An isolated local Maven repository resolved all published runtime artifacts and the `runtime-app` `standalone` classifier from Maven Central. |
