# Runtime 0.4.0 Release Readiness Audit

This note records the release-readiness decision for the `0.4.0`
`protocol-runtime` release target.

The readiness branch keeps the Maven reactor version at `0.4.0-SNAPSHOT`.
The release branch will set the Maven reactor version to `0.4.0`. No tag is
created and no real Maven Central upload is part of this readiness work.

## Release Scope

`0.4.0` moves the runtime from the hardened IEC104 standalone collector line to
the first multi-protocol runtime baseline. It should present:

- `runtime-core` as the dependency-light runtime contract module.
- `runtime-protocol-iec104` as the existing IEC104 parser binding around the
  published `protocol-iec104:0.7.0` parser.
- `runtime-protocol-iec101` as the IEC101 parser binding around the published
  `protocol-iec101:0.7.0` parser, with per-source stream decoder buffering,
  runtime record mapping, failure routing, and reset support.
- `runtime-protocol-iec103` as the IEC103 parser binding around the published
  `protocol-iec103:0.7.0` parser, with the same runtime binding posture.
- `runtime-protocol-modbus` as the Modbus parser binding around the published
  `protocol-modbus:0.7.0` parser, with TCP stream and datagram parser modes.
- `runtime-ingress-tcp-netty` as the TCP/Netty ingress adapter. It remains a
  byte-ingress adapter and does not depend on protocol SDK modules.
- `runtime-app` as the JDK 21 standalone collector assembly with app-level
  protocol selection for `iec104`, `iec101`, `iec103`, and `modbus`, while
  preserving the legacy IEC104 `collector.properties` path.
- `runtime-smoke-tests` as repository-only cross-module verification for IEC104
  and additional protocol bindings behind TCP ingress and
  `RuntimePipelineRunner`.

Out of scope:

- Spring Boot or any application framework.
- Kafka, MQTT, HTTP, database, Redis, durable queue, object storage, or
  external sink adapters.
- Metrics exporters, HTTP health endpoints, and operational dashboards.
- Serial port implementation for IEC101/IEC103.
- UDP ingress for Modbus UDP.
- TLS, reconnect scheduling, and protocol command/session state policy.
- New parser behavior inside `protocol-sdk`.

## Publishing Policy

The selected `0.4.0` policy is to publish the runtime library and application
assembly modules as one versioned Maven reactor release:

| Module | Publish at `0.4.0` | Release posture |
| --- | --- | --- |
| `protocol-runtime` | Yes | Parent POM for repository builds and release metadata. |
| `runtime-core` | Yes | Stable baseline runtime contracts. |
| `runtime-protocol-iec104` | Yes | Existing IEC104 runtime binding against `protocol-iec104:0.7.0`. |
| `runtime-protocol-iec101` | Yes | New IEC101 runtime binding against `protocol-iec101:0.7.0`. |
| `runtime-protocol-iec103` | Yes | New IEC103 runtime binding against `protocol-iec103:0.7.0`. |
| `runtime-protocol-modbus` | Yes | New Modbus runtime binding against `protocol-modbus:0.7.0`. |
| `runtime-ingress-tcp-netty` | Yes | TCP/Netty ingress adapter retained for runtime app assembly. |
| `runtime-app` | Yes | Standalone collector assembly with app-level protocol selection. |
| `runtime-smoke-tests` | No | Test-only integration module; `maven.deploy.skip=true` and `central.skipPublishing=true` are both set. |

`runtime-app` remains the assembly boundary. It may combine ingress, protocol
bindings, app-level configuration, and app-level sinks, but it must not move
those adapter dependencies into `runtime-core` or `protocol-sdk`.

## Baseline Gates

| Gate | Release evidence | Decision |
| --- | --- | --- |
| Maven development line | Root and module parent versions are `0.4.0-SNAPSHOT` on the readiness branch. | Complete. |
| IEC101 runtime binding | `Iec101RuntimeBinding` adapts `protocol-iec101:0.7.0` parser output to runtime records and failures with per-source buffering. | Complete. |
| IEC103 runtime binding | `Iec103RuntimeBinding` adapts `protocol-iec103:0.7.0` parser output to runtime records and failures with per-source buffering. | Complete. |
| Modbus runtime binding | `ModbusRuntimeBinding` adapts `protocol-modbus:0.7.0` parser output for TCP stream and datagram parser modes without adding transport dependencies. | Complete. |
| App-level protocol selection | `runtime-app` parses `collector.protocol` and `collector.source.<name>.protocol`, then creates the selected parser binding per listener/source. | Complete. |
| Legacy IEC104 compatibility | The old single-source `collector.properties` path defaults to `iec104` and remains valid. | Complete. |
| Multi-protocol smoke coverage | `runtime-smoke-tests` covers IEC101, IEC103, and Modbus behind TCP ingress and `RuntimePipelineRunner`, including real localhost socket paths. | Complete. |
| Standalone app smoke | `examples/smoke-standalone.sh` builds the current standalone jar dynamically instead of hard-coding a release version. | Complete. |
| Dependency boundaries | `runtime-core` remains free of Netty, protocol SDK modules, Spring, Kafka, MQTT, HTTP, database, Redis, and observability exporter dependencies. | Complete. |
| Protocol binding boundaries | `runtime-protocol-*` modules depend only on `runtime-core` and their corresponding `protocol-sdk` parser artifacts. | Complete. |
| Release metadata | Maven Central metadata, source/javadoc jars, GPG signing, and Central publishing profile remain configured. | Complete. |
| CI action runtime | GitHub Actions workflow uses Node 24 compatible action versions: `actions/checkout@v6` and `actions/setup-java@v5`. | Complete. |

## Test Coverage Evidence

Protocol binding tests cover parser-to-runtime adaptation and failure routing:

- `runtime-protocol-iec101/src/test/java/.../Iec101RuntimeBindingTest.java`
- `runtime-protocol-iec103/src/test/java/.../Iec103RuntimeBindingTest.java`
- `runtime-protocol-modbus/src/test/java/.../ModbusRuntimeBindingTest.java`

`runtime-app/src/test/java/.../StandaloneCollectorTest.java` covers:

- legacy IEC104 default configuration,
- app-level protocol selection for IEC101, IEC103, and Modbus,
- invalid protocol validation,
- in-memory and file sink record routing,
- malformed frame failure routing,
- fixed and payload-size backpressure behavior,
- named multi-source/listener configuration parsing,
- multi-listener startup and source-specific routing,
- active session cleanup, graceful stop, port conflict, partial startup rollback,
  stop idempotency, restart rejection, and startup validation errors.

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

## Release Verification Checklist

Run these checks on the final `0.4.0-SNAPSHOT` readiness commit:

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

mvn -pl runtime-app -am dependency:tree \
  -Dscope=compile \
  '-Dincludes=io.netty:*,io.github.qbsstg:*'

mvn -pl runtime-smoke-tests -am dependency:tree \
  -Dscope=test \
  '-Dincludes=io.netty:*,io.github.qbsstg:*'
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
| Maven reactor version | Passed | Root and module parent versions remain `0.4.0-SNAPSHOT` on the readiness branch. |
| `git diff --check` | Passed | No whitespace errors exist in the readiness diff. |
| `mvn -q verify` | Passed | Full JDK 21+ reactor verification passed with Maven running on JDK 23. |
| `mvn -q -Pcentral-release -Dgpg.skip=true -Dcentral.skipPublishing=true deploy` | Passed | Central profile smoke passed with publishing disabled and signing skipped. |
| `JAVA_BIN=/opt/homebrew/Cellar/openjdk/23.0.2/libexec/openjdk.jdk/Contents/Home/bin/java sh examples/smoke-standalone.sh` | Passed | Standalone collector built the current `runtime-app` standalone jar, started on an ephemeral localhost TCP port, accepted the IEC104 example frame, and wrote a parsed record to the file sink. |
| Dependency boundary checks | Passed | `runtime-core` has no compile dependencies; `runtime-protocol-*` modules depend only on `runtime-core` and protocol SDK artifacts; Netty appears in `runtime-ingress-tcp-netty`, `runtime-app`, and `runtime-smoke-tests`; new smoke dependencies are test-scoped. |

The default shell `java` was JDK 17 during this audit, so the standalone smoke
used the Homebrew JDK 23 `JAVA_BIN` explicitly. Maven itself was already running
on JDK 23.

## Release Branch Entry Criteria

`0.4.0` can move to a release branch after:

- this readiness PR merges into `main`,
- GitHub Actions passes on the merged readiness commit,
- all readiness branch checks above pass locally, and
- the release branch changes Maven reactor versions from `0.4.0-SNAPSHOT` to
  `0.4.0`.

No tag is created and no real Maven Central upload is part of this readiness
work.

## Final Release Decision

`0.4.0` will be ready to tag and upload after:

- the release PR merges into `main`,
- GitHub Actions passes on the merged release commit,
- a final signed dry run passes with `central.skipPublishing=true`, and
- the operator confirms the real Maven Central upload.
