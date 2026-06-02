# Runtime 0.2.0 Release Readiness Audit

This note records the release-readiness decision for the `0.2.0`
`protocol-runtime` release target.

The readiness branch keeps the Maven reactor version at `0.2.0-SNAPSHOT`.
The release branch will set the Maven reactor version to `0.2.0`.

## Release Scope

`0.2.0` turns the `0.1.0` library baseline into the first runnable collector
baseline. It should present:

- `runtime-core` as the dependency-light runtime contract module.
- `runtime-protocol-iec104` as the IEC104 parser binding around the published
  `protocol-iec104:0.7.0` parser.
- `runtime-ingress-tcp-netty` as the TCP/Netty ingress adapter with server
  bootstrap, per-connection runner creation, active session registry,
  lifecycle events, backpressure decisions, and failure routing.
- `runtime-app` as the first standalone JDK 21 IEC104 TCP collector assembly
  with property-based configuration, logging/file/in-memory sinks, and a
  shaded executable jar.
- `runtime-smoke-tests` as repository-only cross-module verification for IEC104
  over embedded and real TCP socket paths.
- `examples/collector.properties`, `examples/Iec104SendSinglePoint.java`, and
  `examples/smoke-standalone.sh` as the first operator-facing local run path.

Out of scope:

- Spring Boot or any application framework.
- Kafka, MQTT, and HTTP ingestion.
- Database, Redis, durable queue, object storage, or external sink adapters.
- TLS, reconnect scheduling, and IEC104 command/session state policy.
- Metrics exporters and operational dashboards.
- New parser behavior inside `protocol-sdk`.

## Publishing Policy

The selected `0.2.0` policy is to publish the runtime library and application
assembly modules as one versioned Maven reactor release:

| Module | Publish at `0.2.0` | Release posture |
| --- | --- | --- |
| `protocol-runtime` | Yes | Parent POM for repository builds and release metadata. |
| `runtime-core` | Yes | Stable baseline runtime contracts from `0.1.0`. |
| `runtime-protocol-iec104` | Yes | IEC104 runtime binding against `protocol-iec104:0.7.0`. |
| `runtime-ingress-tcp-netty` | Yes | TCP/Netty ingress adapter baseline. |
| `runtime-app` | Yes | Standalone collector assembly and executable shaded jar. |
| `runtime-smoke-tests` | No | Test-only integration module; Maven deploy is skipped. |

`runtime-app` is intentionally the assembly boundary. It may combine ingress,
protocol binding, and app-level sinks, but it must not move those adapter
dependencies into `runtime-core` or `protocol-sdk`.

## Baseline Gates

| Gate | Release evidence | Decision |
| --- | --- | --- |
| Runtime app assembly | `runtime-app` contains `StandaloneCollectorMain`, property parsing, collector lifecycle, logging/file/in-memory sinks, and shaded jar packaging. | Complete. |
| Operator examples | Example properties, a single-file IEC104 sender, file sink format docs, CLI docs, troubleshooting, and smoke script are present. | Complete. |
| Runtime core contracts | `runtime-core` remains the shared source, envelope, binding, parse result, sink, backpressure, runner, and lifecycle contract module. | Complete. |
| IEC104 binding | `runtime-protocol-iec104` consumes released `protocol-iec104:0.7.0` and routes stream parser success/failure results into runtime records. | Complete. |
| TCP/Netty ingress | `runtime-ingress-tcp-netty` contains ByteBuf ingress, source resolution, backpressure handling, server bootstrap, session lifecycle, and graceful shutdown. | Complete. |
| IEC104 runtime smoke | `runtime-smoke-tests` verifies IEC104 over EmbeddedChannel and real localhost socket paths, including split reads, backpressure, failures, and disconnect lifecycle. | Complete. |
| Dependency boundaries | `runtime-core` remains free of Netty, protocol SDK modules, Spring, Kafka, MQTT, HTTP, database, and Redis dependencies. | Complete. |
| Release metadata | Maven Central metadata, source/javadoc jars, GPG signing, and Central publishing profile are configured. | Complete. |
| CI action runtime | GitHub Actions workflow uses Node 24 compatible action versions: `actions/checkout@v6` and `actions/setup-java@v5`. | Complete. |

## Required Checks Before Release Branch

Run these checks on the final `0.2.0-SNAPSHOT` readiness commit:

```bash
git diff --check

mvn -q verify

mvn -q -Pcentral-release \
  -Dgpg.skip=true \
  -Dcentral.skipPublishing=true \
  deploy

sh examples/smoke-standalone.sh

mvn -pl runtime-core dependency:tree -Dincludes=io.netty

mvn -pl runtime-core dependency:tree \
  -Dincludes=io.github.qbsstg

mvn -pl runtime-protocol-iec104 dependency:tree \
  -Dincludes=io.netty,org.springframework,org.apache.kafka,org.eclipse.paho,redis.clients,io.lettuce,org.redisson

mvn -pl runtime-ingress-tcp-netty dependency:tree -Dincludes=io.netty

mvn -pl runtime-app -am dependency:tree \
  -Dincludes=io.netty,io.github.qbsstg \
  -Dscope=compile

mvn -pl runtime-smoke-tests -am dependency:tree \
  -Dincludes=io.netty,io.github.qbsstg \
  -Dscope=test
```

The Central profile command above is intentionally a smoke check with
publishing disabled and signing skipped. A real release still requires a signed
dry run:

```bash
mvn -Pcentral-release -Dcentral.skipPublishing=true clean deploy
```

That signed dry run must pass before any real Central upload.

## Readiness Branch Checks On 2026-06-02

These checks passed on the readiness branch before opening the release-readiness
PR:

| Check | Result | Note |
| --- | --- | --- |
| `git diff --check` | Passed | No whitespace errors in the readiness diff. |
| `mvn -q verify` | Passed | Full JDK 21+ reactor verification passed with Maven running on JDK 23. |
| `mvn -q -Pcentral-release -Dgpg.skip=true -Dcentral.skipPublishing=true deploy` | Passed | Central profile smoke passed with publishing disabled and signing skipped. |
| `JAVA_BIN=/opt/homebrew/Cellar/openjdk/23.0.2/libexec/openjdk.jdk/Contents/Home/bin/java sh examples/smoke-standalone.sh` | Passed | Standalone collector built, started on an ephemeral localhost TCP port, accepted the IEC104 example frame, and wrote a parsed record to the file sink. |
| Dependency boundary checks | Passed | `runtime-core` has no Netty, protocol SDK, or adapter dependencies; `runtime-protocol-iec104` has no adapter dependencies; Netty appears through `runtime-ingress-tcp-netty`; SDK artifacts appear through `runtime-protocol-iec104`. |

## Release Branch Entry Criteria

`0.2.0` can move to a release branch after:

- this readiness PR merges into `main`,
- GitHub Actions passes on the merged readiness commit,
- all readiness branch checks above pass locally, and
- the release branch changes Maven reactor versions from `0.2.0-SNAPSHOT` to
  `0.2.0`.

No tag is created and no real Maven Central upload is part of this readiness
work.
