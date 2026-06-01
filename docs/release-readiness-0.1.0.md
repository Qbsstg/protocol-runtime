# Runtime 0.1.0 Release Readiness Audit

This note records the release-readiness decision for the first
`protocol-runtime` release target.

The final release commit should set the Maven reactor version to `0.1.0`.

## Release Scope

`0.1.0` is the first open-source runtime baseline. It should present:

- `runtime-core` as the dependency-light runtime contract module.
- `runtime-protocol-iec104` as the first protocol binding around the published
  `protocol-iec104:0.7.0` parser.
- `runtime-ingress-tcp-netty` as the first transport adapter with TCP byte
  ingress, server bootstrap, per-connection runner creation, active session
  registry, lifecycle events, backpressure decisions, and failure routing.
- `runtime-smoke-tests` as repository-only cross-module verification for IEC104
  over embedded and real TCP socket paths.

Out of scope:

- A deployable collector application.
- Spring, Kafka, MQTT, HTTP ingress, databases, Redis, or storage sinks.
- Reconnect scheduling, TLS, durable retry queues, or persistent buffering.
- Formal IEC104 session policy such as STARTDT, STOPDT, TESTFR heartbeat
  handling, select-before-operate workflow, or command routing.
- New parser behavior inside `protocol-sdk`.

## Publishing Policy

The selected `0.1.0` policy is to publish the runtime library modules as one
versioned Maven reactor release:

| Module | Publish at `0.1.0` | Release posture |
| --- | --- | --- |
| `protocol-runtime` | Yes | Parent POM for repository builds and release metadata. |
| `runtime-core` | Yes | Baseline runtime contracts. |
| `runtime-protocol-iec104` | Yes | IEC104 runtime binding against `protocol-iec104:0.7.0`. |
| `runtime-ingress-tcp-netty` | Yes | TCP/Netty ingress adapter baseline. |
| `runtime-smoke-tests` | No | Test-only integration module; Maven deploy is skipped. |

This keeps the externally consumable surface small while preserving the smoke
tests that protect dependency direction and cross-module behavior.

## Baseline Gates

| Gate | Release evidence | Decision |
| --- | --- | --- |
| Runtime repository bootstrap | Public `protocol-runtime` repository exists with JDK 21 Maven reactor, CI, README, LICENSE, and module plan. | Complete. |
| Core contracts | `runtime-core` contains source identity, ingress envelope, parser binding, parse result, record/failure sinks, backpressure, pipeline runner, and lifecycle contracts. | Complete. |
| IEC104 binding | `runtime-protocol-iec104` consumes released `protocol-iec104:0.7.0` and routes stream parser success/failure results into runtime records. | Complete. |
| TCP/Netty ingress | `runtime-ingress-tcp-netty` contains ByteBuf ingress, source resolution, backpressure handling, server bootstrap, and graceful shutdown. | Complete. |
| TCP session lifecycle | TCP connection sessions, active session registry, lifecycle events, server stop client closure, and exception-to-failure routing are covered. | Complete. |
| IEC104 runtime smoke | `runtime-smoke-tests` verifies IEC104 over EmbeddedChannel and real localhost socket paths, including split reads, backpressure, failures, and disconnect lifecycle. | Complete. |
| Dependency boundaries | `runtime-core` remains free of Netty and protocol-specific parser modules; Netty is isolated to TCP ingress and test paths. | Complete. |
| Release metadata | Maven Central metadata, source/javadoc jars, GPG signing, and Central publishing profile are configured. | Complete after this readiness PR merges. |

## Required Checks Before Tagging

Run these checks on the final `0.1.0` release commit after all selected gate PRs
have merged:

```bash
git diff --check

mvn -q verify

mvn -q -Pcentral-release \
  -Dgpg.skip=true \
  -Dcentral.skipPublishing=true \
  deploy

mvn -pl runtime-core dependency:tree -Dincludes=io.netty

mvn -pl runtime-core dependency:tree \
  -Dincludes=io.github.qbsstg:protocol-iec104

mvn -pl runtime-ingress-tcp-netty dependency:tree -Dincludes=io.netty

mvn -pl runtime-smoke-tests -am dependency:tree \
  -Dincludes=io.netty \
  -Dscope=test

mvn -pl runtime-smoke-tests -am dependency:tree \
  -Dincludes=io.github.qbsstg \
  -Dscope=test
```

The Central profile command above is intentionally a smoke check with
publishing disabled and signing skipped. A real release still requires a signed
dry run:

```bash
mvn -Pcentral-release -Dcentral.skipPublishing=true clean deploy
```

That signed dry run must pass before any real Central upload.

## Readiness Branch Checks On 2026-06-01

These checks passed before opening the release-readiness PR:

| Check | Result | Note |
| --- | --- | --- |
| `git diff --check` | Passed | No whitespace errors in the readiness diff. |
| `mvn -q verify` | Passed | Full JDK 21 reactor verification passed and generated source/Javadoc jars for published jar modules. |
| `mvn -q -Pcentral-release -Dgpg.skip=true -Dcentral.skipPublishing=true deploy` | Passed | Central profile smoke check passed with publishing disabled and signing skipped. |
| Dependency boundary checks | Passed | `runtime-core` has no Netty or `protocol-iec104`; Netty appears only in TCP ingress and smoke-test paths; SDK artifacts appear through `runtime-protocol-iec104`. |

These checks do not replace the final release PR GitHub Actions checks, signed
dry run, post-tag Maven Central upload, or external dependency resolution
verification.

## Final Release Decision

`0.1.0` is ready to move to a release PR after:

- this readiness PR merges,
- local and GitHub Actions verification pass,
- the release commit sets the reactor version to `0.1.0`,
- the Central profile smoke check passes with publishing disabled, and
- a signed dry run passes before real upload.

No real Maven Central upload is part of this readiness PR.
