# Runtime 0.14.0 Release Readiness Audit

This note records the release-readiness decision for the `0.14.0`
`protocol-runtime` release branch.

The release branch fixes the Maven reactor version at `0.14.0`. No tag is
created and no real Maven Central upload happens during the release branch PR;
final publication is completed after merge.

## Release Scope

`0.14.0` moves the runtime from the standalone collector deployment-governance
baseline to the first runtime package distribution governance baseline. It
should present:

- `runtime-core` as the dependency-light runtime contract module with no
  adapter, broker, storage, framework, database, Redis, management endpoint,
  access-control, request-logging, deployment wrapper, service-manager,
  filesystem-layout, distribution-packaging, checksum/signing, or external
  observability dependencies.
- `runtime-protocol-iec104`, `runtime-protocol-iec101`,
  `runtime-protocol-iec103`, and `runtime-protocol-modbus` as parser-binding
  modules around the published `protocol-sdk:0.7.0` artifacts.
- `runtime-ingress-tcp-netty`, `runtime-ingress-http`,
  `runtime-ingress-kafka`, and `runtime-ingress-mqtt` as ingress adapters that
  map external payloads into runtime envelopes and backpressure results without
  owning package layout, service management, management endpoints, or
  deployment APIs.
- `runtime-app` as the JDK 21 standalone collector assembly for TCP, HTTP,
  Kafka, and MQTT ingress, app-level protocol selection, sink routing,
  lifecycle/status output, app-local health/readiness calculation, app-owned
  JDK `HttpServer` management endpoints, management productionization,
  app-owned deployment governance, and the first app-owned distribution
  package boundary.
- `runtime-smoke-tests` as repository-only cross-module verification for TCP
  ingress, runtime-core, runtime protocol bindings, the standalone app,
  health/status evidence, deployment-governance smoke paths, and distribution
  package smoke paths.

Out of scope:

- Spring Boot or any application framework.
- Database, Redis, durable queue, object storage, external storage sinks,
  retry stores, dashboards, durable health history, external observability
  exporters, service managers, installers, package managers, or daemon
  supervisors.
- Reusing `runtime-ingress-http` as a management endpoint, package distribution
  endpoint, or deployment API. It remains the HTTP protocol-payload ingestion
  adapter.
- Kafka producer/sink publishing, MQTT publisher/sink behavior, dead-letter
  topics, retry topics, schema registry integration, Kafka Streams, or Kafka
  transactions.
- Serial port implementation for IEC101/IEC103.
- UDP ingress for Modbus UDP.
- New parser behavior inside `protocol-sdk`.

## Publishing Policy

The selected `0.14.0` policy is to publish the runtime library, ingress
adapter, and application assembly modules as one versioned Maven reactor
release:

| Module | Publish at `0.14.0` | Release posture |
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
| `runtime-app` | Yes | Standalone collector assembly with app-owned management, deployment governance, and attached standalone/distribution artifacts. |
| `runtime-smoke-tests` | No | Test-only integration module; `maven.deploy.skip=true` and `central.skipPublishing=true` are both set. |

`runtime-app` remains the deployable assembly boundary. It may combine ingress
adapters, protocol bindings, app-level configuration, app-level sinks,
operator-facing status, app-local health/readiness calculation, app-owned
management endpoints, deployment-governance behavior, executable standalone
jar assembly, and distribution package assembly. It must not move adapter,
management, exporter, broker, database, Redis, access-control, request-logging,
deployment-wrapper, service-manager, filesystem-layout, distribution-packaging,
checksum/signing, or package-manager dependencies into `runtime-core`,
`runtime-protocol-*`, or `protocol-sdk`.

## Baseline Gates

| Gate | Release evidence | Decision |
| --- | --- | --- |
| Maven release line | Root and module parent versions are fixed at `0.14.0` on the release branch. | Complete. |
| Runtime core dependency isolation | `runtime-core` has no compile dependencies on Spring, Netty, Kafka, MQTT, HTTP, database, Redis, storage, management endpoints, access-control, request-logging, deployment wrappers, service managers, filesystem layout, distribution packaging, checksum/signing, or exporters. | Complete. |
| Protocol binding dependency isolation | `runtime-protocol-*` modules depend only on `runtime-core`, released `protocol-sdk` parser artifacts, and tests. | Complete. |
| Distribution boundary | Distribution package governance is implemented in `runtime-app`, build configuration, docs, examples, and smoke scripts. | Complete. |
| Package layout | The zip and tar.gz packages include `bin`, `conf`, `lib`, `logs`, `data`, `run`, `tmp`, `docs`, and `examples` under a versioned top-level directory. | Complete. |
| Configuration templates | Default local and production-style collector properties are present in package `conf` and repository examples. | Complete. |
| Start/stop scripts | Package scripts check JDK 21+, resolve `JAVA_HOME`/`JAVA_BIN`, prepare runtime directories, handle PID files, reject duplicate starts, and provide stop/status/validate/dry-run commands. | Complete. |
| Operator documentation | Distribution package docs cover unpack, install, config, Java selection, start, stop, status, logs, smoke, upgrade, rollback, systemd, and launchd handoff. | Complete. |
| Distribution smoke | Package smoke covers unpack, missing Java diagnostics, validate, dry-run, status export, start, management endpoints, parsed record output, duplicate start, TCP port conflict, and graceful stop. | Complete. |
| Release metadata | Maven Central metadata, source/javadoc jars, GPG signing, Central publishing profile, standalone classifier, and distribution assembly remain configured. | Complete. |
| CI action runtime | GitHub Actions uses current checkout/setup-java actions with Temurin JDK 21 and includes distribution package smoke. | Complete. |

## Test Coverage Evidence

`runtime-app/src/test/java/.../StandaloneCollectorTest.java` covers the app-level
configuration, lifecycle, management, sink, listener, backpressure, status,
profile, runtime directory, dry-run, validation, and stop behavior inherited by
the package scripts.

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
- `examples/smoke-distribution-package.sh` builds the package, unpacks it,
  verifies the zip and tar.gz artifacts, checks package layout, exercises Java
  discovery diagnostics, validates config, performs dry-run and status export,
  starts the packaged collector, verifies management health/status, sends an
  IEC104 test frame, verifies file sink output, rejects duplicate start and TCP
  port conflicts, and stops cleanly.

The operator guides are maintained in
[`distribution-package.md`](distribution-package.md),
[`distribution-package.zh-CN.md`](distribution-package.zh-CN.md),
[`deployment-governance.md`](deployment-governance.md), and
[`deployment-governance.zh-CN.md`](deployment-governance.zh-CN.md).

## Release Verification Checklist

Run these checks on the final `0.14.0` release branch commit:

```bash
git diff --check

mvn -q verify

mvn -q -Pcentral-release \
  -Dgpg.skip=true \
  -Dcentral.skipPublishing=true \
  deploy

JAVA_BIN=/path/to/jdk-21-or-newer/bin/java sh examples/smoke-standalone.sh

JAVA_BIN=/path/to/jdk-21-or-newer/bin/java sh examples/smoke-standalone-http.sh

JAVA_BIN=/path/to/jdk-21-or-newer/bin/java sh examples/smoke-distribution-package.sh

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

These checks must pass on the `0.14.0` release branch before opening the
release PR:

| Check | Result | Note |
| --- | --- | --- |
| Maven reactor version | Passed | Root and module parent versions are fixed at `0.14.0`; `protocol-sdk.version` remains `0.7.0`. |
| `git diff --check` | Passed | No whitespace errors were reported on the release branch diff. |
| `mvn -q verify` | Passed | Full JDK 21+ reactor verification passed at version `0.14.0`. |
| `mvn -q -Pcentral-release -Dgpg.skip=true -Dcentral.skipPublishing=true deploy` | Passed | Central profile smoke passed with publishing disabled and signing skipped. |
| `JAVA_BIN=/opt/homebrew/opt/openjdk/bin/java sh examples/smoke-standalone.sh` | Passed | Standalone TCP collector smoke passed and verified validate, dry-run, PID file, IEC104 file sink output, management status, runtime status export, and stop behavior. |
| `JAVA_BIN=/opt/homebrew/opt/openjdk/bin/java sh examples/smoke-standalone-http.sh` | Passed | Standalone HTTP collector smoke passed and verified validate, dry-run, PID file, HTTP response, file sink output, management status, runtime status export, and stop behavior. |
| `JAVA_BIN=/opt/homebrew/opt/openjdk/bin/java sh examples/smoke-distribution-package.sh` | Passed | Distribution package smoke passed and verified zip/tar artifacts, unpacked layout, Java diagnostics, validate, dry-run, status, startup, management checks, parsed file output, duplicate start, TCP port conflict, and stop behavior. |
| Dependency boundary checks | Passed | `runtime-core`, `runtime-protocol-*`, adapter, app, and smoke-test dependency trees were checked with Maven. |

No tag is created and no real Maven Central upload is part of the release
branch PR.

## Final Publication

Final `0.14.0` publication is complete after the release branch merge:

| Step | Result | Evidence |
| --- | --- | --- |
| Tag | Complete | `v0.14.0` was created from the merged release commit and pushed to GitHub. |
| Real Central upload | Complete | `mvn -Pcentral-release clean deploy` created Central deployment `fc95f451-5a0d-4d3c-8743-6a78374fa6d9`. |
| Manual Central publish | Complete | Central deployment `fc95f451-5a0d-4d3c-8743-6a78374fa6d9` reached `PUBLISHED`. |
| Public Maven Central verification | Complete | `runtime-core:0.14.0` and `io.github.qbsstg:runtime-app:0.14.0:jar:standalone` resolved from isolated local Maven repositories backed by Maven Central. |
| Distribution package verification | Complete | `io.github.qbsstg:runtime-app:0.14.0:zip:distribution` and `io.github.qbsstg:runtime-app:0.14.0:tar.gz:distribution` resolved from isolated local Maven repositories backed by Maven Central. |
| GitHub Release | Complete | Release notes were published at `https://github.com/Qbsstg/protocol-runtime/releases/tag/v0.14.0`. |
