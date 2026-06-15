# Runtime 0.13.0 Release Readiness Audit

This note records the release-readiness decision for the `0.13.0`
`protocol-runtime` release branch.

The release branch fixes the Maven reactor version at `0.13.0`. No tag is
created and no real Maven Central upload happens during the release branch PR;
final publication is completed after merge.

## Release Scope

`0.13.0` moves the runtime from the management-plane productionization baseline
to the first standalone collector deployment-governance baseline. It should
present:

- `runtime-core` as the dependency-light runtime contract module with no
  adapter, broker, storage, framework, database, Redis, management endpoint,
  access-control, request-logging, deployment wrapper, service-manager,
  filesystem-layout, distribution-packaging, or observability exporter
  dependencies.
- `runtime-protocol-iec104`, `runtime-protocol-iec101`,
  `runtime-protocol-iec103`, and `runtime-protocol-modbus` as parser-binding
  modules around the published `protocol-sdk:0.7.0` artifacts.
- `runtime-ingress-tcp-netty`, `runtime-ingress-http`,
  `runtime-ingress-kafka`, and `runtime-ingress-mqtt` as ingress adapters that
  map external payloads into runtime envelopes and backpressure results without
  owning deployment governance or management endpoints.
- `runtime-app` as the JDK 21 standalone collector assembly for TCP, HTTP,
  Kafka, and MQTT ingress, app-level protocol selection, sink routing,
  lifecycle/status output, app-local health/readiness calculation, app-owned
  JDK `HttpServer` management endpoints, management productionization, and
  app-owned deployment governance.
- `runtime-smoke-tests` as repository-only cross-module verification for TCP
  ingress, runtime-core, runtime protocol bindings, the standalone app,
  health/status evidence, and deployment-governance smoke paths.

Out of scope:

- Spring Boot or any application framework.
- Database, Redis, durable queue, object storage, external storage sinks,
  retry stores, dashboards, durable health history, external observability
  exporters, service managers, or distribution installers.
- Reusing `runtime-ingress-http` as a management endpoint or deployment API. It
  remains the HTTP protocol-payload ingestion adapter.
- Kafka producer/sink publishing, MQTT publisher/sink behavior, dead-letter
  topics, retry topics, schema registry integration, Kafka Streams, or Kafka
  transactions.
- Serial port implementation for IEC101/IEC103.
- UDP ingress for Modbus UDP.
- New parser behavior inside `protocol-sdk`.

## Publishing Policy

The selected `0.13.0` policy is to publish the runtime library, ingress
adapter, and application assembly modules as one versioned Maven reactor
release:

| Module | Publish at `0.13.0` | Release posture |
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
| `runtime-app` | Yes | Standalone collector assembly with app-owned management and deployment-governance surfaces. |
| `runtime-smoke-tests` | No | Test-only integration module; `maven.deploy.skip=true` and `central.skipPublishing=true` are both set. |

`runtime-app` remains the deployable assembly boundary. It may combine ingress
adapters, protocol bindings, app-level configuration, app-level sinks,
operator-facing status, app-local health/readiness calculation, app-owned
management endpoints, and deployment-governance behavior. It must not move
adapter, management, exporter, broker, database, Redis, access-control,
request-logging, deployment-wrapper, service-manager, filesystem-layout, or
distribution-packaging dependencies into `runtime-core`, `runtime-protocol-*`,
or `protocol-sdk`.

## Baseline Gates

| Gate | Release evidence | Decision |
| --- | --- | --- |
| Maven release line | Root and module parent versions are fixed at `0.13.0` on the release branch. | Complete. |
| Runtime core dependency isolation | `runtime-core` has no compile dependencies on Spring, Netty, Kafka, MQTT, HTTP, database, Redis, storage, management endpoints, access-control, request-logging, deployment wrappers, service managers, filesystem layout, distribution packaging, or exporters. | Complete. |
| Protocol binding dependency isolation | `runtime-protocol-*` modules depend only on `runtime-core`, released `protocol-sdk` parser artifacts, and tests. | Complete. |
| Deployment boundary | Deployment governance is implemented in `runtime-app`, docs, examples, and smoke scripts. | Complete. |
| Profile loading | `collector.profile` and `--profile` select optional sibling profile files such as `collector-production.properties` after base config files and before CLI overrides. | Complete. |
| Runtime directories | `collector.runtime.dir` plus `conf`, `logs`, `data`, `run`, `tmp`, PID, status, and log-file paths define the app-owned directory convention. | Complete. |
| Validation and dry-run CLI | `--validate` and `--dry-run` validate configuration without binding ports or connecting external systems. | Complete. |
| PID and stop behavior | `collector.runtime.pidFile` records the JVM PID after startup; `--stop --pid-file` signals the process and treats missing/stale PID files as already stopped. | Complete. |
| Status export | `--status-export` and `collector.runtime.statusFile` write JSON status snapshots for local operator scripts. | Complete. |
| Deployment examples | Production-style config, stop script, systemd unit, launchd plist, and deployment-governance docs are provided as operator-owned templates. | Complete. |
| Standalone smoke | TCP and HTTP smoke scripts cover validate, dry-run, PID file creation, status export, management checks, ingestion, and graceful stop. | Complete. |
| Release metadata | Maven Central metadata, source/javadoc jars, GPG signing, and Central publishing profile remain configured. | Complete. |
| CI action runtime | GitHub Actions uses `actions/checkout@v6` and `actions/setup-java@v5` with Temurin JDK 21. | Complete. |

## Test Coverage Evidence

`runtime-app/src/test/java/.../StandaloneCollectorTest.java` covers the 0.13.0
deployment-governance boundary:

- profile-specific config loading and CLI override order,
- runtime directory parsing and directory preparation,
- dry-run validation and status export without binding ports,
- validate-only failure output,
- repeated stop with a missing PID file and invalid PID file handling,
- existing management, sink, listener, backpressure, and lifecycle behavior.

The standalone smoke scripts cover executable app behavior:

- `examples/smoke-standalone.sh` validates config, dry-runs, exports configured
  status JSON, starts a TCP collector, verifies PID file creation, exercises
  token-protected management paths, sends the IEC104 example frame, waits for
  file sink output, verifies runtime status export, and stops through
  `--stop --pid-file`.
- `examples/smoke-standalone-http.sh` validates config, dry-runs, exports
  configured status JSON, starts an HTTP collector, verifies PID file creation,
  exercises the same management paths, posts a raw IEC104 payload, verifies
  runtime status export, and stops through `--stop --pid-file`.

The operator guides are maintained in
[`deployment-governance.md`](deployment-governance.md),
[`deployment-governance.zh-CN.md`](deployment-governance.zh-CN.md),
[`status-health-readiness.md`](status-health-readiness.md), and
[`status-health-readiness.zh-CN.md`](status-health-readiness.zh-CN.md).

## Release Verification Checklist

Run these checks on the final `0.13.0` release branch commit:

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

## Release Branch Checks On 2026-06-15

These checks must pass on the `0.13.0` release branch before opening the
release PR:

| Check | Result | Note |
| --- | --- | --- |
| Maven reactor version | Passed | Root and module parent versions are fixed at `0.13.0`; `protocol-sdk.version` remains `0.7.0`. |
| `git diff --check` | Passed | No whitespace errors were reported before final documentation updates. |
| `mvn -q verify` | Passed | Full JDK 21+ reactor verification passed at version `0.13.0`. |
| `mvn -q -Pcentral-release -Dgpg.skip=true -Dcentral.skipPublishing=true deploy` | Passed | Central profile smoke passed with publishing disabled and signing skipped. |
| `JAVA_BIN=/opt/homebrew/opt/openjdk/bin/java sh examples/smoke-standalone.sh` | Passed | Standalone TCP collector smoke passed and verified validate, dry-run, PID file, IEC104 file sink output, management status, runtime status export, and stop behavior. |
| `JAVA_BIN=/opt/homebrew/opt/openjdk/bin/java sh examples/smoke-standalone-http.sh` | Passed | Standalone HTTP collector smoke passed and verified validate, dry-run, PID file, HTTP response, file sink output, management status, runtime status export, and stop behavior. |
| Dependency boundary checks | Passed | `runtime-core`, `runtime-protocol-*`, adapter, app, and smoke-test dependency trees were checked with Maven. |

No tag is created and no real Maven Central upload is part of the release
branch PR.

## Final Publication

Final `0.13.0` publication happens after the release branch merges:

| Step | Result | Evidence |
| --- | --- | --- |
| Tag | Pending | `v0.13.0` will be created from the merged release commit and pushed to GitHub. |
| Real Central upload | Pending | `mvn -Pcentral-release clean deploy` will create a Central deployment. |
| Manual Central publish | Pending | The deployment must be published after human confirmation and reach `PUBLISHED`. |
| Public Maven Central verification | Pending | Runtime artifacts and `io.github.qbsstg:runtime-app:0.13.0:jar:standalone` must resolve from an isolated local Maven repository backed by Maven Central. |
| GitHub Release | Pending | Release notes will be published at `https://github.com/Qbsstg/protocol-runtime/releases/tag/v0.13.0`. |
