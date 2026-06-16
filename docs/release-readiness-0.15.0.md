# Runtime 0.15.0 Release Readiness Audit

This note records the release-readiness decision for the `0.15.0`
`protocol-runtime` release branch.

The release branch fixes the Maven reactor version at `0.15.0`. No tag is
created and no real Maven Central upload happens during the release branch PR;
final publication is completed after merge.

## Release Scope

`0.15.0` moves the runtime from the first runtime package distribution
governance baseline to the first distribution package productionization
baseline. It should present:

- `runtime-core` as the dependency-light runtime contract module with no
  adapter, broker, storage, framework, database, Redis, management endpoint,
  access-control, request-logging, deployment wrapper, service-manager,
  filesystem-layout, distribution-packaging, checksum/signing, installer,
  runtime-supervisor, or external observability dependencies.
- `runtime-protocol-iec104`, `runtime-protocol-iec101`,
  `runtime-protocol-iec103`, and `runtime-protocol-modbus` as parser-binding
  modules around the published `protocol-sdk:0.7.0` artifacts.
- `runtime-ingress-tcp-netty`, `runtime-ingress-http`,
  `runtime-ingress-kafka`, and `runtime-ingress-mqtt` as ingress adapters that
  map external payloads into runtime envelopes and backpressure results without
  owning package layout, package integrity, service management, management
  endpoints, deployment APIs, or upgrade APIs.
- `runtime-app` as the JDK 21 standalone collector assembly for TCP, HTTP,
  Kafka, and MQTT ingress, app-level protocol selection, sink routing,
  lifecycle/status output, app-local health/readiness calculation, app-owned
  JDK `HttpServer` management endpoints, deployment governance, distribution
  package assembly, package metadata, version diagnostics, package integrity
  verification, and operator-visible diagnostics.
- `runtime-smoke-tests` as repository-only cross-module verification for TCP
  ingress, runtime-core, runtime protocol bindings, the standalone app,
  health/status evidence, deployment-governance smoke paths, distribution
  package smoke paths, and release artifact smoke paths.

Out of scope:

- Spring Boot or any application framework.
- Database, Redis, durable queue, object storage, external storage sinks,
  retry stores, dashboards, durable health history, external observability
  exporters, service managers, installers, package managers, daemon
  supervisors, or runtime supervisors.
- Reusing `runtime-ingress-http` as a management endpoint, package distribution
  endpoint, deployment API, upgrade API, package API, or operations API. It
  remains the HTTP protocol-payload ingestion adapter.
- Kafka producer/sink publishing, MQTT publisher/sink behavior, dead-letter
  topics, retry topics, schema registry integration, Kafka Streams, or Kafka
  transactions.
- Serial port implementation for IEC101/IEC103.
- UDP ingress for Modbus UDP.
- New parser behavior inside `protocol-sdk`.

## Publishing Policy

The selected `0.15.0` policy is to publish the runtime library, ingress
adapter, and application assembly modules as one versioned Maven reactor
release:

| Module | Publish at `0.15.0` | Release posture |
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
| `runtime-app` | Yes | Standalone collector assembly with app-owned management, deployment governance, standalone jar, distribution packages, package metadata, checksum sidecars, and release artifact smoke support. |
| `runtime-smoke-tests` | No | Test-only integration module; `maven.deploy.skip=true` and `central.skipPublishing=true` are both set. |

`runtime-app` remains the deployable assembly boundary. It may combine ingress
adapters, protocol bindings, app-level configuration, app-level sinks,
operator-facing status, app-local health/readiness calculation, app-owned
management endpoints, deployment-governance behavior, executable standalone
jar assembly, distribution package assembly, version diagnostics, and package
integrity verification. It must not move adapter, management, exporter,
broker, database, Redis, access-control, request-logging, deployment-wrapper,
service-manager, filesystem-layout, distribution-packaging, checksum/signing,
installer, package-manager, or runtime-supervisor dependencies into
`runtime-core`, `runtime-protocol-*`, or `protocol-sdk`.

## Baseline Gates

| Gate | Release evidence | Decision |
| --- | --- | --- |
| Maven release line | Root and module parent versions are fixed at `0.15.0` on the release branch. | Complete. |
| Runtime core dependency isolation | `runtime-core` has no compile dependencies on Spring, Netty, Kafka, MQTT, HTTP, database, Redis, storage, management endpoints, access-control, request-logging, deployment wrappers, service managers, filesystem layout, distribution packaging, checksum/signing, installers, runtime supervisors, or exporters. | Complete. |
| Protocol binding dependency isolation | `runtime-protocol-*` modules depend only on `runtime-core`, released `protocol-sdk` parser artifacts, and tests. | Complete. |
| Distribution productionization boundary | Package metadata, package integrity verification, checksum sidecar generation, docs, examples, and smoke scripts are implemented in `runtime-app`, build configuration, docs, examples, and CI. | Complete. |
| Package metadata | Distribution packages include `package.properties` with runtime version, artifact id/version, standalone jar path, package layout, layout version, and build Java version. | Complete. |
| Version diagnostics | `bin/protocol-runtime version` prints runtime version, artifact, Java version, Java binary, package layout, app home, and standalone jar path. | Complete. |
| Package integrity | `bin/protocol-runtime verify-package` verifies unpacked package layout and SHA-256/SHA-512 checksum sidecars for zip/tar artifacts. | Complete. |
| Checksum sidecars | The build generates `.sha256` and `.sha512` sidecars for standalone jar, distribution zip, and distribution tar.gz artifacts. | Complete. |
| Release artifact smoke | `examples/smoke-release-artifact.sh` validates local build outputs or downloaded distribution artifacts through checksum verification and app commands. | Complete. |
| Operator documentation | Distribution package docs cover checksum/signature policy, version diagnostics, package verification, migration, rollback, offline deployment, and troubleshooting. | Complete. |
| CI action runtime | GitHub Actions uses current checkout/setup-java actions with Temurin JDK 21 and includes standalone, distribution, and release artifact smoke. | Complete. |

## Test Coverage Evidence

`runtime-app/src/test/java/.../StandaloneCollectorTest.java` covers the app-level
configuration, lifecycle, management, sink, listener, backpressure, status,
profile, runtime directory, dry-run, validation, and stop behavior inherited by
the package scripts.

The standalone and package smoke scripts cover executable app behavior:

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
  verifies zip/tar artifacts and checksum sidecars, checks package layout,
  exercises Java discovery diagnostics, runs `version` and `verify-package`,
  validates config, performs dry-run and status export, starts the packaged
  collector, verifies management health/status, sends an IEC104 test frame,
  verifies file sink output, rejects duplicate start and TCP port conflicts,
  verifies missing/bad checksum diagnostics, and stops cleanly.
- `examples/smoke-release-artifact.sh` validates local or downloaded release
  artifacts by verifying tar/zip checksum sidecars, unpacking the package, and
  running `java-check`, `version`, `verify-package`, `validate`, `dry-run`,
  `start`, `status`, and `stop`.

The operator guides are maintained in
[`distribution-package.md`](distribution-package.md) and
[`distribution-package.zh-CN.md`](distribution-package.zh-CN.md).

## Release Verification Checklist

Run these checks on the final `0.15.0` release branch commit:

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

JAVA_BIN=/path/to/jdk-21-or-newer/bin/java sh examples/smoke-release-artifact.sh

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

## Release Branch Checks On 2026-06-16

These checks must pass on the `0.15.0` release branch before opening the
release PR:

| Check | Result | Note |
| --- | --- | --- |
| Maven reactor version | Passed | Root and module parent versions are fixed at `0.15.0`; `protocol-sdk.version` remains `0.7.0`. |
| `git diff --check` | Passed | No whitespace errors were reported on the release branch diff. |
| `mvn -q verify` | Passed | Full JDK 21+ reactor verification passed at version `0.15.0`. |
| `mvn -q -Pcentral-release -Dgpg.skip=true -Dcentral.skipPublishing=true deploy` | Passed | Central profile smoke passed with publishing disabled and signing skipped. |
| `JAVA_BIN=/opt/homebrew/opt/openjdk/bin/java sh examples/smoke-standalone.sh` | Passed | Standalone TCP collector smoke passed and verified validate, dry-run, PID file, IEC104 file sink output, management status, runtime status export, and stop behavior. |
| `JAVA_BIN=/opt/homebrew/opt/openjdk/bin/java sh examples/smoke-standalone-http.sh` | Passed | Standalone HTTP collector smoke passed and verified validate, dry-run, PID file, HTTP response, file sink output, management status, runtime status export, and stop behavior. |
| `JAVA_BIN=/opt/homebrew/opt/openjdk/bin/java sh examples/smoke-distribution-package.sh` | Passed | Distribution package smoke passed and verified zip/tar artifacts, checksum sidecars, package metadata, version, verify-package, Java diagnostics, validate, dry-run, status, startup, management checks, parsed file output, duplicate start, TCP port conflict, bad/missing checksum diagnostics, and stop behavior. |
| `JAVA_BIN=/opt/homebrew/opt/openjdk/bin/java sh examples/smoke-release-artifact.sh` | Passed | Release artifact smoke passed and verified local release artifacts, checksum sidecars, unpacked package layout, version, verify-package, validate, dry-run, start, status, and stop behavior. |
| Dependency boundary checks | Passed | `runtime-core`, `runtime-protocol-*`, adapter, app, and smoke-test dependency trees were checked with Maven. |

No tag is created and no real Maven Central upload is part of the release
branch PR.

## Final Publication

Final `0.15.0` publication is completed after the release branch merge:

| Step | Result | Evidence |
| --- | --- | --- |
| Tag | Pending | Create `v0.15.0` from the merged release commit and push it to GitHub. |
| Real Central upload | Pending | Run `mvn -Pcentral-release clean deploy` from the `v0.15.0` release commit. |
| Manual Central publish | Pending | Publish the created Central deployment after repository validation completes. |
| Public Maven Central verification | Pending | Verify `runtime-core:0.15.0` and `io.github.qbsstg:runtime-app:0.15.0:jar:standalone` from isolated local Maven repositories backed by Maven Central. |
| Distribution package verification | Pending | Verify `io.github.qbsstg:runtime-app:0.15.0:zip:distribution` and `io.github.qbsstg:runtime-app:0.15.0:tar.gz:distribution` from isolated local Maven repositories backed by Maven Central. |
| GitHub Release | Pending | Publish GitHub release notes at `https://github.com/Qbsstg/protocol-runtime/releases/tag/v0.15.0`. |
