# Runtime 0.16.0 Release Readiness Audit

This note records the release-readiness decision for the `0.16.0`
`protocol-runtime` release branch.

The release branch fixes the Maven reactor version at `0.16.0`. No tag is
created and no real Maven Central upload happens during the release branch PR;
final publication is completed after merge.

## Release Scope

`0.16.0` moves the runtime from distribution package productionization to the
first production runtime operations baseline. It should present:

- `runtime-core` as the dependency-light runtime contract module with no
  adapter, broker, storage, framework, database, Redis, management endpoint,
  access-control, request-logging, deployment wrapper, service-manager,
  filesystem-layout, distribution-packaging, checksum/signing, installer,
  runtime-supervisor, operations-agent, sink-adapter, or external observability
  dependencies.
- `runtime-protocol-iec104`, `runtime-protocol-iec101`,
  `runtime-protocol-iec103`, and `runtime-protocol-modbus` as parser-binding
  modules around the published `protocol-sdk:0.7.0` artifacts.
- `runtime-ingress-tcp-netty`, `runtime-ingress-http`,
  `runtime-ingress-kafka`, and `runtime-ingress-mqtt` as ingress adapters that
  map external payloads into runtime envelopes and backpressure results without
  owning package layout, package integrity, service management, management
  endpoints, deployment APIs, diagnostics APIs, downstream sink adapters, or
  upgrade APIs.
- `runtime-app` as the JDK 21 standalone collector assembly for TCP, HTTP,
  Kafka, and MQTT ingress, app-level protocol selection, sink routing,
  lifecycle/status output, app-local health/readiness calculation, app-owned
  JDK `HttpServer` management endpoints, deployment governance, distribution
  package assembly, package metadata, version diagnostics, package integrity
  verification, runtime self-check, configuration hot-check without
  hot-reload, and operator-visible production diagnostics.
- `runtime-smoke-tests` as repository-only cross-module verification for TCP
  ingress, runtime-core, runtime protocol bindings, the standalone app,
  health/status evidence, deployment-governance smoke paths, distribution
  package smoke paths, release artifact smoke paths, long-running operations
  smoke, and release artifact regression smoke.

Out of scope:

- Spring Boot or any application framework.
- Database, Redis, durable queue, object storage, external storage sinks,
  retry stores, dashboards, durable health history, external observability
  exporters, service managers, installers, package managers, daemon
  supervisors, runtime supervisors, or operations agents.
- Reusing `runtime-ingress-http` as a management endpoint, package distribution
  endpoint, deployment API, upgrade API, package API, operations API,
  diagnostics API, or downstream HTTP sink. It remains the HTTP protocol-payload
  ingestion adapter.
- Kafka producer/sink publishing, HTTP downstream delivery, MQTT downstream
  publishing, dead-letter topics, retry topics, schema registry integration,
  Kafka Streams, or Kafka transactions.
- Serial port implementation for IEC101/IEC103.
- UDP ingress for Modbus UDP.
- New parser behavior inside `protocol-sdk`.

## Publishing Policy

The selected `0.16.0` policy is to publish the runtime library, ingress
adapter, and application assembly modules as one versioned Maven reactor
release:

| Module | Publish at `0.16.0` | Release posture |
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
| `runtime-app` | Yes | Standalone collector assembly with app-owned management, deployment governance, distribution packages, package metadata, checksum sidecars, runtime self-check, config hot-check, long-running smoke, and release artifact regression smoke support. |
| `runtime-smoke-tests` | No | Test-only integration module; `maven.deploy.skip=true` and `central.skipPublishing=true` are both set. |

`runtime-app` remains the deployable assembly boundary. It may combine ingress
adapters, protocol bindings, app-level configuration, app-level sinks,
operator-facing status, app-local health/readiness calculation, app-owned
management endpoints, deployment-governance behavior, executable standalone
jar assembly, distribution package assembly, version diagnostics, package
integrity verification, runtime self-check, config hot-check, and operations
runbooks. It must not move adapter, management, exporter, broker, database,
Redis, access-control, request-logging, deployment-wrapper, service-manager,
filesystem-layout, distribution-packaging, checksum/signing, installer,
package-manager, runtime-supervisor, operations-agent, or sink-adapter
dependencies into `runtime-core`, `runtime-protocol-*`, or `protocol-sdk`.

## Baseline Gates

| Gate | Release evidence | Decision |
| --- | --- | --- |
| Maven release line | Root and module parent versions are fixed at `0.16.0` on the release branch. | Complete. |
| Runtime core dependency isolation | `runtime-core` has no compile dependencies on Spring, Netty, Kafka, MQTT, HTTP, database, Redis, storage, management endpoints, access-control, request-logging, deployment wrappers, service managers, filesystem layout, distribution packaging, checksum/signing, installers, runtime supervisors, operations agents, sink adapters, or exporters. | Complete. |
| Protocol binding dependency isolation | `runtime-protocol-*` modules depend only on `runtime-core`, released `protocol-sdk` parser artifacts, and tests. | Complete. |
| Production operations boundary | Runtime self-check, config hot-check, operations runbooks, long-running smoke, release artifact regression smoke, and production diagnostics flow are implemented in `runtime-app`, docs, examples, and CI/smoke. | Complete. |
| Runtime self-check | `bin/protocol-runtime self-check` reports Java version, runtime version, package layout, runtime directory writability, configured listeners, source mappings, sink configuration, management posture, and package verification state. | Complete. |
| Config hot-check | `bin/protocol-runtime hot-check` detects config file changes, re-runs validation, reports restart requirements, and explicitly avoids hot-reloading a live collector. | Complete. |
| Operations runbooks | English and Chinese runbooks cover self-check, hot-check, failure recovery, status/log evidence, and production issue diagnostics. | Complete. |
| Long-running smoke | `examples/smoke-long-running.sh` exercises a packaged collector over a running window with status, management, sink, log, PID, and stop evidence. | Complete. |
| Release artifact regression smoke | `examples/smoke-release-artifact-regression.sh` validates local or published standalone jar and distribution packages through Java checks, version, verify-package, validate, dry-run, self-check, status, start, and stop. | Complete. |
| CI action runtime | GitHub Actions uses current checkout/setup-java actions with Temurin JDK 21 and includes standalone, distribution, release artifact, long-running operations, and release artifact regression smoke. | Complete. |

## Test Coverage Evidence

`runtime-app/src/test/java/.../StandaloneCollectorTest.java` covers the app-level
configuration, lifecycle, management, sink, listener, backpressure, status,
profile, runtime directory, dry-run, validation, stop behavior, self-check,
hot-check, and operations diagnostics behavior inherited by the package
scripts.

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
  exercises Java discovery diagnostics, runs `version`, `verify-package`,
  `validate`, `self-check`, `hot-check`, performs dry-run and status export,
  starts the packaged collector, verifies management health/status, sends an
  IEC104 test frame, verifies file sink output, rejects duplicate start and TCP
  port conflicts, verifies missing/bad checksum diagnostics, and stops cleanly.
- `examples/smoke-release-artifact.sh` validates local or downloaded release
  artifacts by verifying tar/zip checksum sidecars, unpacking the package, and
  running `java-check`, `version`, `verify-package`, `validate`, `self-check`,
  `hot-check`, `dry-run`, `start`, `status`, and `stop`.
- `examples/smoke-long-running.sh` validates a packaged collector over a
  sustained running window with repeated status snapshots, management
  health/readiness/status, file sink evidence, logs, PID state, package
  verification evidence, and graceful stop.
- `examples/smoke-release-artifact-regression.sh` validates published or local
  release artifacts through standalone jar and distribution package regression
  commands.

The operator guides are maintained in
[`distribution-package.md`](distribution-package.md),
[`distribution-package.zh-CN.md`](distribution-package.zh-CN.md),
[`operations-runbook.md`](operations-runbook.md), and
[`operations-runbook.zh-CN.md`](operations-runbook.zh-CN.md).

## Release Verification Checklist

Run these checks on the final `0.16.0` release branch commit:

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

JAVA_BIN=/path/to/jdk-21-or-newer/bin/java sh examples/smoke-long-running.sh

JAVA_BIN=/path/to/jdk-21-or-newer/bin/java sh examples/smoke-release-artifact-regression.sh

mvn -q -pl runtime-core dependency:tree -Dscope=compile

mvn -q -pl runtime-protocol-iec104,runtime-protocol-iec101,runtime-protocol-iec103,runtime-protocol-modbus \
  dependency:tree -Dscope=compile

mvn -q -pl runtime-ingress-tcp-netty,runtime-ingress-http,runtime-ingress-kafka,runtime-ingress-mqtt,runtime-app \
  dependency:tree -Dscope=compile

mvn -q -pl runtime-smoke-tests dependency:tree -Dscope=test
```

Management HTTP smoke is covered by the standalone TCP, standalone HTTP,
distribution package, and long-running smoke scripts. Those scripts exercise
health, readiness, status, token rejection, not-found, method-not-allowed, and
malformed request JSON paths.

The Central profile command above is intentionally a smoke check with
publishing disabled and signing skipped. A real release still requires a signed
dry run:

```bash
mvn -Pcentral-release -Dcentral.skipPublishing=true clean deploy
```

That signed dry run must pass before any real Central upload.

## Release Branch Checks On 2026-06-16

These checks must pass on the `0.16.0` release branch before opening the
release PR:

| Check | Result | Note |
| --- | --- | --- |
| Maven reactor version | Passed | Root and module parent versions are fixed at `0.16.0`; `protocol-sdk.version` remains `0.7.0`. |
| `git diff --check` | Passed | No whitespace errors were reported on the release branch diff. |
| `mvn -q verify` | Passed | Full JDK 21+ reactor verification passed at version `0.16.0`. |
| `mvn -q -Pcentral-release -Dgpg.skip=true -Dcentral.skipPublishing=true deploy` | Passed | Central profile smoke passed with publishing disabled and signing skipped. |
| `JAVA_BIN=/opt/homebrew/opt/openjdk/bin/java sh examples/smoke-standalone.sh` | Passed | Standalone TCP collector smoke passed and verified validate, dry-run, PID file, IEC104 file sink output, management status, runtime status export, and stop behavior. |
| `JAVA_BIN=/opt/homebrew/opt/openjdk/bin/java sh examples/smoke-standalone-http.sh` | Passed | Standalone HTTP collector smoke passed and verified validate, dry-run, PID file, HTTP response, file sink output, management status, runtime status export, and stop behavior. |
| Management HTTP smoke | Passed | Token-protected management health/readiness/status, unauthorized responses, not-found, method-not-allowed, and malformed request JSON were covered by standalone TCP, standalone HTTP, distribution package, and long-running smoke. |
| `JAVA_BIN=/opt/homebrew/opt/openjdk/bin/java sh examples/smoke-distribution-package.sh` | Passed | Distribution package smoke passed and verified zip/tar artifacts, checksum sidecars, package metadata, version, verify-package, Java diagnostics, validate, self-check, hot-check, dry-run, status, startup, management checks, parsed file output, duplicate start, TCP port conflict, bad/missing checksum diagnostics, and stop behavior. |
| `JAVA_BIN=/opt/homebrew/opt/openjdk/bin/java sh examples/smoke-release-artifact.sh` | Passed | Release artifact smoke passed and verified local release artifacts, checksum sidecars, unpacked package layout, version, verify-package, validate, self-check, hot-check, dry-run, start, status, and stop behavior. |
| `JAVA_BIN=/opt/homebrew/opt/openjdk/bin/java sh examples/smoke-long-running.sh` | Passed | Long-running operations smoke passed and verified repeated IEC104 input, status snapshots, management health/readiness/status, file sink evidence, log evidence, PID state, package verification, and graceful stop. |
| `JAVA_BIN=/opt/homebrew/opt/openjdk/bin/java sh examples/smoke-release-artifact-regression.sh` | Passed | Release artifact regression smoke passed and verified standalone jar plus distribution package regression commands. |
| Dependency boundary checks | Passed | `runtime-core`, `runtime-protocol-*`, adapter, app, and smoke-test dependency trees were checked with Maven. |

No tag is created and no real Maven Central upload is part of the release
branch PR.

## Final Publication

Final `0.16.0` publication is completed after the release branch merge:

| Step | Result | Evidence |
| --- | --- | --- |
| Tag | Pending | `v0.16.0` will point at the merged release commit. |
| Real Central upload | Pending | The final `mvn -Pcentral-release clean deploy` run will create a Central deployment. |
| Manual Central publish | Pending | The deployment must reach `PUBLISHED`. |
| Public Maven Central verification | Pending | Runtime modules and `io.github.qbsstg:runtime-app:0.16.0:jar:standalone` must resolve from an isolated local Maven repository backed by Maven Central. |
| Distribution package verification | Pending | `io.github.qbsstg:runtime-app:0.16.0:zip:distribution`, `io.github.qbsstg:runtime-app:0.16.0:tar.gz:distribution`, `.asc`, and Maven Central checksum sidecars must be verified from Maven Central. |
| GitHub Release | Pending | GitHub release notes will be published after Central verification. |

`0.16.0` remains the current release candidate until the final publication
steps complete.
