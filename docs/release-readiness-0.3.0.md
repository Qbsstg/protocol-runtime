# Runtime 0.3.0 Release Readiness Audit

This note records the release-readiness audit and final publication result for
the `0.3.0` `protocol-runtime` release.

The readiness branch kept the Maven reactor version at `0.3.0-SNAPSHOT`. The
release branch fixed the Maven reactor version at `0.3.0`. The final release
was tagged as `v0.3.0`, uploaded to Maven Central, published, and verified.

## Release Scope

`0.3.0` hardens the standalone collector app that was introduced in `0.2.0`.
It should present:

- `runtime-core` as the dependency-light runtime contract module.
- `runtime-protocol-iec104` as the IEC104 parser binding around the published
  `protocol-iec104:0.7.0` parser.
- `runtime-ingress-tcp-netty` as the TCP/Netty ingress adapter with connection
  sessions, active connection registry, lifecycle events, backpressure
  decisions, and failure routing.
- `runtime-app` as the JDK 21 standalone IEC104 TCP collector assembly with
  stronger startup validation, multi-source and multi-listener configuration,
  lifecycle/status snapshots, status log output, metrics counters, file sink
  rotation, parse failure diagnostics, and payload-size backpressure policy.
- `runtime-smoke-tests` as repository-only cross-module verification for IEC104
  over embedded and real TCP socket paths.
- `examples/collector.properties`, `examples/Iec104SendSinglePoint.java`, and
  `examples/smoke-standalone.sh` as the operator-facing local run path for the
  `0.3.0` release line.

Out of scope:

- Spring Boot or any application framework.
- Kafka, MQTT, HTTP, database, Redis, durable queue, object storage, or
  external sink adapters.
- Metrics exporters, HTTP health endpoints, and operational dashboards.
- TLS, reconnect scheduling, and IEC104 command/session state policy.
- Queue-depth or sink-pressure backpressure policy.
- New parser behavior inside `protocol-sdk`.

## Publishing Policy

The selected `0.3.0` policy is to publish the runtime library and application
assembly modules as one versioned Maven reactor release:

| Module | Publish at `0.3.0` | Release posture |
| --- | --- | --- |
| `protocol-runtime` | Yes | Parent POM for repository builds and release metadata. |
| `runtime-core` | Yes | Stable baseline runtime contracts from `0.1.0` and `0.2.0`. |
| `runtime-protocol-iec104` | Yes | IEC104 runtime binding against `protocol-iec104:0.7.0`. |
| `runtime-ingress-tcp-netty` | Yes | TCP/Netty ingress adapter baseline retained for app assembly. |
| `runtime-app` | Yes | Standalone collector assembly with `0.3.0` production-hardening features. |
| `runtime-smoke-tests` | Not intended | Test-only integration module; `0.3.0` became visible on Maven Central because the Central publishing plugin did not honor `maven.deploy.skip=true`. Future releases add `central.skipPublishing=true`. |

`runtime-app` remains the assembly boundary. It may combine ingress, protocol
binding, app-level configuration, and app-level sinks, but it must not move
those adapter dependencies into `runtime-core` or `protocol-sdk`.

## Baseline Gates

| Gate | Release evidence | Decision |
| --- | --- | --- |
| Runtime app assembly | `runtime-app` contains `StandaloneCollectorMain`, property parsing, collector lifecycle, logging/file/in-memory sinks, and shaded jar packaging. | Complete. |
| Configuration validation | `CollectorConfigValidation` and `StandaloneCollectorAppConfig` validate source ids, listener ports, thread counts, sink type, file sink path, duplicate sources, and duplicate listeners before network bind. | Complete. |
| Multi-source/listener model | `CollectorSourceConfig`, `TcpListenerConfig`, and app config parsing support named `collector.sources` and `collector.tcp.listeners` while preserving the legacy single-source property shape. | Complete. |
| Lifecycle/status snapshot | `CollectorLifecycleState`, `CollectorStatusSnapshot`, source/listener status models, startup failure reason, last exception, timestamps, and active connection count are exposed through `StandaloneCollector`. | Complete. |
| Standalone status output | `CollectorStatusFormatter` writes one-line startup and shutdown status output from `StandaloneCollectorMain` without adding HTTP dependencies. | Complete. |
| Runtime counters | `CollectorRuntimeMetrics` and `RuntimeSinkCounters` track parsed records, parse failures, backpressure retry/drop decisions, and sink/failure output counts. | Complete. |
| File sink rotation | `FileRuntimeSink` and `FileSinkRotationConfig` bound local file output by byte limit and retained history count. | Complete. |
| Parse failure isolation | Malformed frames route to failure output with payload preview, source/session attributes, and cause details without stopping healthy traffic. | Complete. |
| Backpressure policy | `RuntimeAppBackpressureStrategy` supports fixed decisions and app-level payload-size threshold decisions before parsing. | Complete for payload-size policy; queue-depth and sink-pressure policies remain deferred. |
| TCP/Netty ingress boundary | Netty remains isolated to `runtime-ingress-tcp-netty` and app/test assembly boundaries. | Complete. |
| Dependency boundaries | `runtime-core` remains free of Netty, protocol SDK modules, Spring, Kafka, MQTT, HTTP, database, Redis, and observability exporter dependencies. | Complete. |
| Release metadata | Maven Central metadata, source/javadoc jars, GPG signing, and Central publishing profile are configured. | Complete. |
| CI action runtime | GitHub Actions workflow uses Node 24 compatible action versions: `actions/checkout@v6` and `actions/setup-java@v5`. | Complete. |

## Test Coverage Evidence

`runtime-app/src/test/java/.../StandaloneCollectorTest.java` covers:

- in-memory and file sink record routing,
- file sink rotation,
- malformed frame failure routing,
- fixed and payload-size backpressure behavior,
- legacy and named multi-source/listener configuration parsing,
- multi-listener startup and source-specific routing,
- active session cleanup, graceful stop, port conflict, partial startup rollback,
- stop idempotency, restart rejection, and startup validation errors.

`runtime-smoke-tests/src/test/java/.../Iec104TcpRuntimeSmokeTest.java` covers:

- IEC104 TCP bytes through the runtime pipeline,
- split TCP reads,
- backpressure that prevents parsing,
- parse failure routing, and
- real localhost TCP socket parsing with runner stop on disconnect.

## Release Verification Checklist

The readiness and release branches used these local verification commands:

```bash
git diff --check

mvn -q verify

mvn -q -Pcentral-release \
  -Dgpg.skip=true \
  -Dcentral.skipPublishing=true \
  deploy

JAVA_BIN=/path/to/jdk-21-or-newer/bin/java sh examples/smoke-standalone.sh

mvn -q -pl runtime-core dependency:tree \
  '-Dincludes=io.netty:*,org.springframework:*,org.apache.kafka:*,org.eclipse.paho:*,org.apache.httpcomponents:*,org.apache.httpcomponents.client5:*,redis.clients:*,io.micrometer:*,io.opentelemetry:*'

mvn -q -pl runtime-protocol-iec104 dependency:tree \
  '-Dincludes=io.netty:*,org.springframework:*,org.apache.kafka:*,org.eclipse.paho:*,org.apache.httpcomponents:*,org.apache.httpcomponents.client5:*,redis.clients:*,io.micrometer:*,io.opentelemetry:*'

mvn -q -pl runtime-ingress-tcp-netty dependency:tree '-Dincludes=io.netty:*'

mvn -q -pl runtime-app -am dependency:tree \
  '-Dincludes=io.netty:*,io.github.qbsstg:*' \
  '-Dscope=compile'
```

The Central profile command above is intentionally a smoke check with
publishing disabled and signing skipped. The real release also required a signed
dry run:

```bash
mvn -Pcentral-release -Dcentral.skipPublishing=true clean deploy
```

That signed dry run passed before the real Central upload.

## Readiness Branch Checks On 2026-06-02

These checks passed on the readiness branch before opening the release-readiness
PR:

| Check | Result | Note |
| --- | --- | --- |
| Maven reactor version | Passed | Root and module parent versions remain `0.3.0-SNAPSHOT` on the readiness branch. |
| `git diff --check` | Passed | No whitespace errors exist in the readiness diff. |
| `mvn -q verify` | Passed | Full JDK 21+ reactor verification passed with Maven running on JDK 23. |
| `mvn -q -Pcentral-release -Dgpg.skip=true -Dcentral.skipPublishing=true deploy` | Passed | Central profile smoke passed with publishing disabled and signing skipped. |
| `JAVA_BIN=/opt/homebrew/Cellar/openjdk/23.0.2/libexec/openjdk.jdk/Contents/Home/bin/java sh examples/smoke-standalone.sh` | Passed | Standalone collector built `runtime-app-0.3.0-SNAPSHOT-standalone.jar`, started on an ephemeral localhost TCP port, accepted the IEC104 example frame, and wrote a parsed record to the file sink. |
| Dependency boundary checks | Passed | `runtime-core` and `runtime-protocol-iec104` stayed adapter-free; Netty appeared only in `runtime-ingress-tcp-netty` and app assembly; SDK artifacts appeared through `runtime-protocol-iec104`. |

## Release Branch Entry Criteria

`0.3.0` moved to a release branch after:

- the readiness PR merged into `main`,
- GitHub Actions passed on the merged readiness commit,
- all readiness branch checks above passed locally, and
- the release branch changed Maven reactor versions from `0.3.0-SNAPSHOT` to
  `0.3.0`.

No tag was created and no real Maven Central upload was part of the readiness
work.

## Release Branch Checks On 2026-06-02

These checks passed on the `0.3.0` release branch before opening the release
PR:

| Check | Result | Note |
| --- | --- | --- |
| Maven reactor version | Passed | Root and module parent versions are fixed at `0.3.0`. |
| `git diff --check` | Passed | No whitespace errors exist in the release diff. |
| `mvn -q verify` | Passed | Full JDK 21+ reactor verification passed at version `0.3.0` with Maven running on JDK 23. |
| `mvn -q -Pcentral-release -Dgpg.skip=true -Dcentral.skipPublishing=true deploy` | Passed | Central profile smoke passed with publishing disabled and signing skipped. |
| `JAVA_BIN=/opt/homebrew/Cellar/openjdk/23.0.2/libexec/openjdk.jdk/Contents/Home/bin/java sh examples/smoke-standalone.sh` | Passed | Standalone collector built `runtime-app-0.3.0-standalone.jar`, started on an ephemeral localhost TCP port, accepted the IEC104 example frame, and wrote a parsed record to the file sink. |
| Dependency boundary checks | Passed | `runtime-core` and `runtime-protocol-iec104` stayed adapter-free; Netty appeared only in `runtime-ingress-tcp-netty` and app assembly; SDK artifacts appeared through `runtime-protocol-iec104`. |

No tag was created and no real Maven Central upload was part of the release
branch PR.

## Final Release Decision

`0.3.0` was tagged and uploaded after:

- the release PR merged into `main`,
- GitHub Actions passed on the merged release commit,
- a final signed dry run passed with `central.skipPublishing=true`, and
- the operator confirmed the real Maven Central upload.

## Final Release Result

`0.3.0` has been tagged, uploaded, manually published, and verified.

| Check | Result | Note |
| --- | --- | --- |
| Tag | Passed | `v0.3.0` points at `54cfd9ba6fa7f46728226017ffe115712d9b3a52`. |
| Signed dry run | Passed | `mvn -Pcentral-release -Dcentral.skipPublishing=true clean deploy` signed artifacts and completed without upload. |
| Central upload | Passed | Deployment `eaa2bf69-69d3-416f-9529-550924a33b28` was uploaded and validated. |
| Central publish | Passed | Deployment `eaa2bf69-69d3-416f-9529-550924a33b28` reached `PUBLISHED`. |
| Maven Central resolution | Passed | `runtime-core`, `runtime-protocol-iec104`, `runtime-ingress-tcp-netty`, `runtime-app`, and the `runtime-app` `standalone` classifier resolved from Maven Central with an isolated local repository. |
| Test module publishing boundary | Mitigated | `runtime-smoke-tests:0.3.0` is visible on Maven Central. It remains unsupported as an application dependency, and future releases set `central.skipPublishing=true` in `runtime-smoke-tests`. |
