# Protocol Runtime 0.1.0 Release Notes

`0.1.0` is the first JDK 21 Protocol Runtime baseline for applications that
need runtime ingestion around the Java 8 compatible
[`protocol-sdk`](https://github.com/Qbsstg/protocol-sdk) parser modules.

## Highlights

- Adds `runtime-core`, a dependency-light contract module for source identity,
  ingress envelopes, parser bindings, parse results, record/failure sinks,
  backpressure, pipeline runners, and lifecycle boundaries.
- Adds `runtime-protocol-iec104`, the first runtime protocol binding around the
  published `io.github.qbsstg:protocol-iec104:0.7.0` SDK artifact.
- Adds `runtime-ingress-tcp-netty`, the first Netty TCP ingress adapter.
- Supports TCP server bootstrap, port `0` binding for tests, one
  `RuntimePipelineRunner` per accepted connection, graceful shutdown, active
  session registry, session attributes, lifecycle events, backpressure
  handling, and exception-to-failure routing.
- Adds cross-module IEC104 smoke tests through EmbeddedChannel and real
  localhost TCP sockets.
- Keeps runtime dependencies out of `protocol-sdk`; the SDK remains
  parser-only.

## Maven Coordinates

Use runtime modules directly:

```xml
<dependency>
    <groupId>io.github.qbsstg</groupId>
    <artifactId>runtime-core</artifactId>
    <version>0.1.0</version>
</dependency>
```

```xml
<dependency>
    <groupId>io.github.qbsstg</groupId>
    <artifactId>runtime-protocol-iec104</artifactId>
    <version>0.1.0</version>
</dependency>
```

```xml
<dependency>
    <groupId>io.github.qbsstg</groupId>
    <artifactId>runtime-ingress-tcp-netty</artifactId>
    <version>0.1.0</version>
</dependency>
```

`runtime-smoke-tests` is repository-only and is not intended as a published
application dependency.

## Module Status

| Module | Status in `0.1.0` |
| --- | --- |
| `runtime-core` | Bootstrap runtime contracts. |
| `runtime-protocol-iec104` | First IEC104 runtime parser binding against SDK `0.7.0`. |
| `runtime-ingress-tcp-netty` | TCP/Netty ingress baseline with server bootstrap and connection lifecycle. |
| `runtime-smoke-tests` | Test-only cross-module verification; deploy skipped. |

## Scope

The `0.1.0` scope is a library baseline:

- Runtime-neutral core contracts.
- IEC104 parser binding around released SDK artifacts.
- TCP/Netty byte ingress and server bootstrap.
- Session attributes and active connection tracking.
- Backpressure decisions before parsing.
- Record and failure sink routing.
- Embedded and real TCP smoke coverage.

Out of scope:

- A deployable collector application.
- Spring Boot assembly or runtime configuration framework.
- Kafka, MQTT, HTTP ingestion.
- Database, Redis, durable queues, or storage sinks.
- TLS, reconnect scheduling, persistent retry, or heartbeat policy.
- IEC104 STARTDT/STOPDT/TESTFR state-machine policy.
- New parser behavior inside `protocol-sdk`.

## Verification

The `0.1.0` release branch passed these checks before the release PR:

- `git diff --check`
- `mvn -q verify`
- Central release profile smoke check with publishing disabled
- dependency boundary checks confirming Netty is absent from `runtime-core` and
  present only in TCP ingress or test paths

The final release should still be tagged only after the release PR passes
GitHub Actions and merges. A signed dry run with `central.skipPublishing=true`
must pass before the real Maven Central upload.

No Maven Central deployment has been performed by this release branch.
