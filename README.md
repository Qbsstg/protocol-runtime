# Protocol Runtime

[简体中文](README.zh-CN.md)

Protocol Runtime is the JDK 21 collector runtime for the Java 8 compatible
[`protocol-sdk`](https://github.com/Qbsstg/protocol-sdk) parser modules.

The runtime owns ingestion, session lifecycle, parser binding, backpressure,
batching, and downstream delivery concerns. The SDK remains parser-only and
must not depend on this repository.

## Status

This repository is in bootstrap. `0.1.0` published the first runtime-core
contract surface, IEC104 binding, and TCP/Netty ingress baseline. `0.2.0`
published the first runnable standalone IEC104 TCP collector app. `0.3.0`
published runtime-app production hardening for configuration validation,
multi-source/listener app configuration, lifecycle/status snapshots, status
output, counters, file sink rotation, parse failure isolation, and payload-size
backpressure policy. `0.4.0` published multi-protocol runtime expansion with
IEC101, IEC103, and Modbus runtime protocol bindings around the published
`protocol-sdk:0.7.0` parser artifacts, plus app-level protocol selection while
preserving the existing IEC104 app path. `0.5.0` published the first adapter
boundary release with a JDK-only HTTP ingress baseline and HTTP, Kafka, and
MQTT adapter design notes while keeping Kafka and MQTT client dependencies out
of the runtime. `0.6.0` published the HTTP ingress productionization line and
runtime-app HTTP collector assembly. `0.7.0` published the Kafka ingress
baseline and runtime-app Kafka collector assembly. `0.8.0` published the MQTT
ingress baseline and runtime-app MQTT collector assembly. `0.9.0` published
the downstream sink and operations hardening line, including app-level sink
failure isolation, file sink status, and sink-failure-triggered backpressure.
`0.10.0` published health checks and runtime status productionization,
including app-local health/readiness snapshots, explainable health reasons,
operator status guides, and standalone health smoke coverage. `0.11.0`
published the app-owned JDK HTTP management plane for standalone collectors,
with independent health, readiness, and status endpoints on a separate
management port. `0.12.0` published management-plane productionization with
explicit security boundaries, configurable access control, management request
logging, expanded JSON metrics, health history snapshots, error response
rules, configuration examples, and smoke coverage. `0.13.0` published the first
production deployment governance baseline for the standalone collector,
including configuration profiles, runtime directory conventions, log file
policy, PID and stop-script behavior, systemd/launchd examples, configuration
validation CLI, startup dry-run, status export, troubleshooting docs, and smoke
coverage. `0.14.0` published the first runtime package distribution governance
baseline for the standalone collector: zip/tar packages, `bin`/`conf`/`logs`/
`data`/`run`/`tmp` layout templates, default config templates, script hardening,
upgrade notes, package smoke, JDK 21 checks, default Java troubleshooting, and
an operator install guide. `0.15.0` published the first distribution package
productionization baseline: package metadata, version diagnostics, archive
checksum verification, checksum/signature policy, cross-platform script
guidance, configuration migration notes, upgrade rollback strategy, offline
deployment guidance, release artifact smoke, and operator troubleshooting
improvements. `0.16.0` published the first production runtime operations
baseline: runtime self-checks, configuration hot-check without hot-reload,
stronger runtime status evidence, failure recovery and operator runbooks,
long-running smoke, release artifact regression smoke, and production issue
diagnostics.

The current `0.17.0-SNAPSHOT` development line implements the first downstream
sink productionization baseline: stable file sink schema v1, delivery failure
classification, failed-record isolation, bounded failed sample export, sink
backpressure evidence, retry/dead-letter boundaries, Kafka/HTTP/MQTT downstream
adapter boundaries, record envelope output rules, operator sink
troubleshooting, and smoke coverage.

The `0.17.0` baseline scope is tracked in
[`docs/roadmap-0.17.0.md`](docs/roadmap-0.17.0.md), and release notes are
tracked in [`docs/release-notes-0.17.0.md`](docs/release-notes-0.17.0.md).
The published `0.16.0` scope is tracked in
[`docs/roadmap-0.16.0.md`](docs/roadmap-0.16.0.md), and release notes are
tracked in [`docs/release-notes-0.16.0.md`](docs/release-notes-0.16.0.md).
The `0.16.0` release-readiness audit is tracked in
[`docs/release-readiness-0.16.0.md`](docs/release-readiness-0.16.0.md).
The published `0.15.0` scope is tracked in
[`docs/roadmap-0.15.0.md`](docs/roadmap-0.15.0.md), and release notes are
tracked in [`docs/release-notes-0.15.0.md`](docs/release-notes-0.15.0.md).
The `0.15.0` release-readiness audit is tracked in
[`docs/release-readiness-0.15.0.md`](docs/release-readiness-0.15.0.md).
The published `0.14.0` release scope is tracked in
[`docs/roadmap-0.14.0.md`](docs/roadmap-0.14.0.md), and release notes are
tracked in [`docs/release-notes-0.14.0.md`](docs/release-notes-0.14.0.md),
and the release-readiness audit is tracked in
[`docs/release-readiness-0.14.0.md`](docs/release-readiness-0.14.0.md).
The package install and upgrade guide is
[`docs/distribution-package.md`](docs/distribution-package.md).
The published `0.13.0` release scope is tracked in
[`docs/roadmap-0.13.0.md`](docs/roadmap-0.13.0.md), release notes are tracked
in [`docs/release-notes-0.13.0.md`](docs/release-notes-0.13.0.md), and the
release-readiness audit is tracked in
[`docs/release-readiness-0.13.0.md`](docs/release-readiness-0.13.0.md).

The published `0.12.0` release scope is tracked in
[`docs/roadmap-0.12.0.md`](docs/roadmap-0.12.0.md), release notes are tracked
in [`docs/release-notes-0.12.0.md`](docs/release-notes-0.12.0.md), and the
release-readiness audit is tracked in
[`docs/release-readiness-0.12.0.md`](docs/release-readiness-0.12.0.md).

The published `0.11.0` release scope is tracked in
[`docs/roadmap-0.11.0.md`](docs/roadmap-0.11.0.md), release notes are tracked
in [`docs/release-notes-0.11.0.md`](docs/release-notes-0.11.0.md), and the
release-readiness audit is tracked in
[`docs/release-readiness-0.11.0.md`](docs/release-readiness-0.11.0.md).

The published `0.10.0` release scope is tracked in
[`docs/roadmap-0.10.0.md`](docs/roadmap-0.10.0.md), and release notes are
tracked in [`docs/release-notes-0.10.0.md`](docs/release-notes-0.10.0.md).
The release-readiness audit is tracked in
[`docs/release-readiness-0.10.0.md`](docs/release-readiness-0.10.0.md).

The published `0.9.0` release scope is tracked in
[`docs/roadmap-0.9.0.md`](docs/roadmap-0.9.0.md), release notes are tracked in
[`docs/release-notes-0.9.0.md`](docs/release-notes-0.9.0.md), and the
release-readiness audit is tracked in
[`docs/release-readiness-0.9.0.md`](docs/release-readiness-0.9.0.md). The
published `0.8.0` release scope is tracked in
[`docs/roadmap-0.8.0.md`](docs/roadmap-0.8.0.md), release notes are tracked in
[`docs/release-notes-0.8.0.md`](docs/release-notes-0.8.0.md), and the
release-readiness audit is tracked in
[`docs/release-readiness-0.8.0.md`](docs/release-readiness-0.8.0.md). The
published `0.7.0` release scope is tracked in
[`docs/roadmap-0.7.0.md`](docs/roadmap-0.7.0.md), release notes are tracked in
[`docs/release-notes-0.7.0.md`](docs/release-notes-0.7.0.md), and the
release-readiness audit is tracked in
[`docs/release-readiness-0.7.0.md`](docs/release-readiness-0.7.0.md). The
published `0.6.0` release scope is tracked in
[`docs/roadmap-0.6.0.md`](docs/roadmap-0.6.0.md), and release notes are tracked
in [`docs/release-notes-0.6.0.md`](docs/release-notes-0.6.0.md). The previous
published `0.5.0` release scope is tracked in
[`docs/roadmap-0.5.0.md`](docs/roadmap-0.5.0.md), and release notes are tracked
in [`docs/release-notes-0.5.0.md`](docs/release-notes-0.5.0.md). The previous
published `0.4.0` release scope is tracked in
[`docs/roadmap-0.4.0.md`](docs/roadmap-0.4.0.md), and release notes are
tracked in [`docs/release-notes-0.4.0.md`](docs/release-notes-0.4.0.md).

## Maven Coordinates

The latest published runtime version is `0.16.0`. Runtime modules are JDK 21
artifacts. Applications should depend on the modules they use directly:

```xml
<dependency>
    <groupId>io.github.qbsstg</groupId>
    <artifactId>runtime-core</artifactId>
    <version>0.16.0</version>
</dependency>
```

```xml
<dependency>
    <groupId>io.github.qbsstg</groupId>
    <artifactId>runtime-protocol-iec104</artifactId>
    <version>0.16.0</version>
</dependency>
```

```xml
<dependency>
    <groupId>io.github.qbsstg</groupId>
    <artifactId>runtime-ingress-tcp-netty</artifactId>
    <version>0.16.0</version>
</dependency>
```

```xml
<dependency>
    <groupId>io.github.qbsstg</groupId>
    <artifactId>runtime-ingress-http</artifactId>
    <version>0.16.0</version>
</dependency>
```

```xml
<dependency>
    <groupId>io.github.qbsstg</groupId>
    <artifactId>runtime-ingress-kafka</artifactId>
    <version>0.16.0</version>
</dependency>
```

```xml
<dependency>
    <groupId>io.github.qbsstg</groupId>
    <artifactId>runtime-app</artifactId>
    <version>0.16.0</version>
</dependency>
```

`runtime-smoke-tests` is a repository test module. It is not intended as an
application dependency even if a historical release is visible in Maven Central.

## Module Plan

| Module | Status | Responsibility |
| --- | --- | --- |
| `runtime-core` | Bootstrap | Runtime-neutral contracts: source identity, ingress envelope, parser binding, parse results, record/failure sinks, backpressure, pipeline runner, and lifecycle boundary. |
| `runtime-protocol-iec104` | Bootstrap | First runtime protocol binding around `io.github.qbsstg:protocol-iec104:0.7.0`. |
| `runtime-protocol-iec101` | 0.4.0 baseline | Runtime binding around `io.github.qbsstg:protocol-iec101:0.7.0` with per-source stream decoder buffering and failure routing. |
| `runtime-protocol-iec103` | 0.4.0 baseline | Runtime binding around `io.github.qbsstg:protocol-iec103:0.7.0` with per-source stream decoder buffering and failure routing. |
| `runtime-protocol-modbus` | 0.4.0 baseline | Runtime binding around `io.github.qbsstg:protocol-modbus:0.7.0` with TCP stream and datagram parser modes. |
| `runtime-ingress-tcp-netty` | Baseline | Minimal Netty TCP ingress handler and server bootstrap that bind a TCP port, create one `RuntimePipelineRunner` per accepted connection, convert `ByteBuf` payloads to `IngressEnvelope`, apply backpressure decisions, and dispatch to sinks. |
| `runtime-ingress-http` | 0.6.0 baseline | JDK `HttpServer` based HTTP ingress that maps POST bodies to `IngressEnvelope`, supports configured/header/path `SourceId` mapping, applies request size limits, and turns backpressure decisions into HTTP responses. |
| `runtime-ingress-kafka` | 0.7.0 baseline | Kafka client based ingress adapter that maps `ConsumerRecord<byte[], byte[]>` payloads and Kafka metadata into runtime envelopes while keeping Kafka dependencies out of `runtime-core`. |
| `runtime-ingress-mqtt` | 0.8.0 baseline | Paho MQTT based ingress adapter that maps MQTT payloads and message metadata into runtime envelopes while keeping MQTT dependencies out of `runtime-core`. |
| `runtime-app` | 0.16.0 baseline | Standalone collector assembly with property-based configuration, app-level protocol selection, TCP/HTTP/Kafka/MQTT assembly, JDK logging/file/in-memory sinks, sink failure isolation, file sink status, sink-failure-triggered backpressure, app-local health/readiness snapshots, explainable status output, JDK HTTP management endpoints, management access control, request logging, management metrics, bounded health history, stable management error JSON, executable shaded jar, app-owned deployment governance, zip/tar distribution package governance, package metadata, version diagnostics, package integrity verification, runtime self-check, config hot-check without hot-reload, checksum sidecars, long-running smoke, release artifact smoke/regression smoke, and operator troubleshooting/runbooks. |
| `runtime-smoke-tests` | Test-only | Cross-module smoke tests that prove ingress, runtime-core, and protocol bindings work together without turning those combinations into production dependencies. |

Future modules may include pipelines, additional sinks, richer deployable
runtime applications, and dedicated app/adapter deployment helpers. Those
dependencies belong here, not in `protocol-sdk`.

## `0.17.0` Downstream Sink Productionization Baseline

`0.17.0-SNAPSHOT` starts after the published `0.16.0` production runtime
operations release. The baseline prepares the standalone collector for more
reliable downstream delivery before adding broker or HTTP producer
dependencies.

The first baseline scope includes:

- file sink schema v1 and record envelope output rules
- delivery failure classification for configuration, filesystem,
  serialization, write, flush, backpressure, retryable transient, permanent
  rejection, and unknown failures
- app-local failed-record isolation and bounded failed sample export
- sink backpressure status/readiness evidence for file sink failures,
  failed-record isolation failures, and future broker sinks
- retry and dead-letter boundary design without durable stores in this line
- Kafka, HTTP, and MQTT downstream sink adapter boundaries for future
  `runtime-sink-*` modules
- operator sink troubleshooting and smoke expectations

The line does not introduce Kafka producers, HTTP clients, MQTT publishers,
database writers, Redis queues, object storage sinks, external queues, or sink
adapter dependencies into `runtime-core`.

## `0.16.0` Production Runtime Operations Baseline

`0.16.0` starts after the published `0.15.0` distribution package
productionization release. The baseline focuses on making the standalone
collector easier to operate during long-running production use without adding a
runtime supervisor, service manager, database, Redis, external observability
exporter, or framework dependency.

The first baseline scope includes:

- `bin/protocol-runtime self-check`, which emits JSON evidence for
  configuration, Java version, package layout, writable directories, sink
  paths, listener bind readiness, management endpoint posture, and package
  integrity state
- `bin/protocol-runtime hot-check`, which detects config file changes,
  re-runs validation, and reports whether restart is required without
  hot-reloading a running collector
- stronger logging and status evidence for startup, shutdown, listener bind,
  sink health, parse failures, backpressure decisions, management requests, and
  package integrity state
- failure recovery runbooks for stale PID files, port conflicts, sink path
  failures, malformed config, management token errors, parse failures,
  backpressure, package verification failures, and interrupted upgrades
- long-running smoke and release artifact regression smoke coverage
- operator runbooks and production issue diagnostics flow for collecting
  version, config validation, self-check, status export, logs, PID state,
  management snapshots, and package verification evidence

Operational details are maintained in
[`docs/operations-runbook.md`](docs/operations-runbook.md).

The operations work must stay in `runtime-app`, examples, docs, CI/smoke, or a
future dedicated app/operations boundary. It must not add runtime-supervisor,
service-manager, external exporter, database, Redis, Spring, installer, package
manager, or reverse SDK dependencies.

## `0.13.0` Production Deployment Governance Baseline

`0.13.0` starts after the published `0.12.0` management productionization
baseline and keeps production deployment governance inside the app/adapter
boundary:

- `runtime-core` remains free of Spring, Netty, Kafka, MQTT, HTTP, database,
  Redis, observability exporter, access-control, request-logging, deployment
  wrapper, service-manager, and filesystem-layout dependencies
- deployment concerns stay in `runtime-app` or future dedicated app/adapter
  modules; they do not move into parser bindings or SDK modules
- configuration profiles define local, test, staging, and production-style
  runtime configuration posture without introducing a framework
- runtime directory conventions cover `conf`, `logs`, `data`, `run`, and
  temporary paths
- log file policy, PID and stop-script behavior, systemd/launchd examples,
  configuration validation CLI, startup dry-run, status export, and
  troubleshooting docs are app-owned operator surfaces
- smoke coverage proves validation, dry-run, PID file creation, status export,
  management HTTP checks, and graceful stop through the standalone jar

The detailed plan is maintained in
[`docs/roadmap-0.13.0.md`](docs/roadmap-0.13.0.md), and the deployment guide is
maintained in [`docs/deployment-governance.md`](docs/deployment-governance.md).

Example deployment commands:

```sh
java -jar runtime-app/target/runtime-app-0.13.0-standalone.jar \
  --validate --config examples/collector.properties

java -jar runtime-app/target/runtime-app-0.13.0-standalone.jar \
  --dry-run --config examples/collector.properties \
  --status-export target/runtime-status.json

java -jar runtime-app/target/runtime-app-0.13.0-standalone.jar \
  --config examples/collector.properties

java -jar runtime-app/target/runtime-app-0.13.0-standalone.jar \
  --stop --pid-file target/protocol-runtime/run/protocol-runtime.pid
```

## `0.15.0` Distribution Package Productionization Baseline

`0.15.0` published the first distribution package hardening line. The baseline
includes:

- package metadata through distribution-root `package.properties`
- `bin/protocol-runtime version` for runtime, artifact, Java, layout, app home,
  and standalone jar diagnostics
- `bin/protocol-runtime verify-package` for unpacked layout checks and
  SHA-256/SHA-512 archive checksum verification
- local `.sha256` and `.sha512` build outputs for standalone jar, distribution
  zip, and distribution tar.gz artifacts
- checksum/signature policy that reuses Maven Central sidecars without adding
  checksum/signing dependencies to `runtime-core`
- cross-platform script compatibility guidance for POSIX shells and
  operator-owned Windows usage
- configuration migration notes for package upgrades
- upgrade rollback strategy for failed package replacement
- offline deployment guidance for servers without direct Maven Central access
- release artifact smoke coverage for local or downloaded package outputs
- operator troubleshooting improvements for package install, upgrade, rollback,
  Java discovery, PID files, status checks, script permissions, integrity
  checks, offline artifacts, and version mismatch

The `0.15.0` line must keep package productionization in `runtime-app`, build
configuration, examples, docs, or a future dedicated app/distribution boundary.
It must not add package integrity, signing, installer, service-manager,
filesystem-layout, deployment-wrapper, database, Redis, framework, or exporter
dependencies to `runtime-core` or `protocol-sdk`.

## `0.14.0` Runtime Package Distribution Baseline

`0.14.0` published deployable package artifacts for the standalone
collector while keeping package governance in `runtime-app`, build
configuration, examples, and docs. Tag `v0.14.0` points at the release commit,
and the runtime artifacts, standalone classifier, and distribution zip/tar.gz
packages are available from Maven Central.

Build the standalone jar and distribution packages:

```sh
mvn -q -pl runtime-app -am package
```

The build creates:

- `runtime-app/target/runtime-app-0.14.0-standalone.jar`
- `runtime-app/target/runtime-app-0.14.0-distribution.zip`
- `runtime-app/target/runtime-app-0.14.0-distribution.tar.gz`

The distribution package unpacks to a predictable layout:

```text
protocol-runtime-0.14.0/
  bin/
  conf/
  lib/
  logs/
  data/
  run/
  tmp/
  docs/
  examples/
```

Basic package flow:

```sh
tar -xzf runtime-app/target/runtime-app-0.14.0-distribution.tar.gz -C target
cd target/protocol-runtime-0.14.0

bin/protocol-runtime java-check
bin/protocol-runtime validate
bin/protocol-runtime dry-run
bin/protocol-runtime start
bin/protocol-runtime status
bin/protocol-runtime stop
```

The package smoke verifies unpack, JDK 21 checks, config validation, dry-run,
status export, startup, management health/readiness/status, file sink output,
duplicate start handling, TCP port conflict handling, and graceful stop:

```sh
sh examples/smoke-distribution-package.sh
```

Install, upgrade, and operator guidance is maintained in
[`docs/distribution-package.md`](docs/distribution-package.md).

## `0.12.0` Management Productionization Baseline

`0.12.0` was published after the `0.11.0` management-plane baseline and keeps
the management implementation inside the app/adapter boundary:

- `runtime-core` remains free of Spring, Netty, Kafka, MQTT, HTTP, database,
  Redis, and observability exporter dependencies
- management HTTP stays separate from `runtime-ingress-http`, which remains the
  protocol-payload ingestion adapter
- management security is defined as an app boundary before adding broader
  remote-control behavior
- access control, request logging, JSON metrics, health history snapshots, and
  error response rules are added without moving those concerns into parser or
  core contracts
- examples and smoke tests cover healthy, degraded, unauthorized/forbidden, and
  malformed management request paths

The baseline keeps the management endpoint local by default. Operators can
choose:

- `collector.management.access=local`: only loopback clients are accepted
- `collector.management.access=open`: no management authentication, intended
  only for explicitly protected networks
- `collector.management.access=token`: requires `Authorization: Bearer <token>`
  or `X-Management-Token`

The management request log records method, path, status, duration, remote
address, and rejection reason. It does not log request headers, token values, or
payload bytes. `/status` now includes management request counters, status-code
counts, the latest rejection reason, and bounded health-history entries.

Example token-protected management configuration:

```properties
collector.management.enabled=true
collector.management.host=127.0.0.1
collector.management.port=8081
collector.management.access=token
collector.management.token=change-me
collector.management.requestLogging.enabled=true
collector.management.healthHistory.maxEntries=32
```

```sh
curl -s -H 'Authorization: Bearer change-me' http://127.0.0.1:8081/status
```

The detailed plan is maintained in
[`docs/roadmap-0.12.0.md`](docs/roadmap-0.12.0.md).

## `0.11.0` Management Plane Release

`0.11.0` published the first standalone collector management plane after the
published `0.10.0` health/readiness model:

- `runtime-core` remains free of Spring, Netty, Kafka, MQTT, HTTP, database,
  Redis, and observability exporter dependencies
- management HTTP is app-owned and implemented with JDK `HttpServer` inside
  `runtime-app`
- `runtime-ingress-http` remains the protocol-payload HTTP ingestion adapter;
  it is not the management API
- management configuration lives under `collector.management.*`
- `/health`, `/readiness`, and `/status` expose JSON snapshots with lifecycle,
  health, readiness, sources, listeners, sink, backpressure, metrics, and
  failure counters

Example management configuration:

```properties
collector.management.enabled=true
collector.management.host=127.0.0.1
collector.management.port=8081
collector.management.healthPath=/health
collector.management.readinessPath=/readiness
collector.management.statusPath=/status
```

Example checks:

```sh
curl -s http://127.0.0.1:8081/health
curl -s http://127.0.0.1:8081/readiness
curl -s http://127.0.0.1:8081/status
```

The detailed release record is maintained in
[`docs/roadmap-0.11.0.md`](docs/roadmap-0.11.0.md).

## `0.10.0` Health And Status Release

`0.10.0` published the health and status productionization line after `0.9.0`:

- keep `runtime-core` free of Spring, Netty, Kafka, MQTT, HTTP, database,
  Redis, and observability exporter dependencies
- make runtime-app health/readiness state explicit for configured, starting,
  running, degraded, failed, and stopped collectors
- expose operator-facing status output that distinguishes listener health,
  source health, sink health, parse failure pressure, and backpressure state
- keep any management HTTP, metrics exporter, or external monitoring
  dependency in dedicated app/adapter modules, not in `runtime-core`
- preserve published TCP, HTTP, Kafka, MQTT, and sink-hardening behavior while
  adding health evidence around those paths

The detailed release record is maintained in
[`docs/roadmap-0.10.0.md`](docs/roadmap-0.10.0.md).

## `0.9.0` Sink And Operations Release

`0.9.0` published the production-hardening line opened after `0.8.0`:

- keep `runtime-core` free of Spring, Netty, Kafka, MQTT, HTTP, database,
  Redis, and observability exporter dependencies
- define downstream sink boundaries outside ingress adapters, starting with
  file/logging delivery hardening before broker or storage sinks
- improve failure isolation, sink error routing, and retry/backpressure
  decisions without moving adapter policy into parser bindings
- add stronger operational examples for TCP, HTTP, Kafka, and MQTT collector
  configurations
- keep future Kafka/MQTT/HTTP sink or management dependencies in dedicated
  adapter/app modules, not in `runtime-core` or `protocol-sdk`

The detailed plan is maintained in
[`docs/roadmap-0.9.0.md`](docs/roadmap-0.9.0.md).

## `0.8.0` MQTT Ingress Release

`0.8.0` published the first MQTT ingress implementation line:

- `runtime-core` remains free of MQTT, Kafka, HTTP, Spring, database, Redis,
  and observability exporter dependencies
- `runtime-ingress-mqtt` owns the MQTT client dependency, topic/source
  mapping, payload-to-envelope mapping, QoS acknowledgement posture, retained
  message handling, duplicate delivery posture, reconnect/session ownership,
  and backpressure result mapping
- MQTT topic, QoS, retained flag, duplicate flag, packet id, source id mode, and
  selected protocol should remain envelope attributes
- `runtime-protocol-*` modules continue to parse protocol payloads without
  MQTT dependencies
- `runtime-app` owns MQTT client configuration and standalone collector
  assembly while keeping MQTT APIs out of `runtime-core`
- [`examples/collector-mqtt.properties`](examples/collector-mqtt.properties)
  shows the minimal IEC104-over-MQTT collector configuration

The detailed plan is maintained in
[`docs/roadmap-0.8.0.md`](docs/roadmap-0.8.0.md).

## `0.7.0` Kafka Ingress Release

`0.7.0` published the first Kafka ingress implementation line:

- `runtime-core` remains free of Kafka, MQTT, HTTP, Spring, database, Redis,
  and observability exporter dependencies
- `runtime-ingress-kafka` owns the Kafka client dependency, source mapping,
  record-to-envelope mapping, backpressure result mapping, and commit-mode
  decisions
- Kafka topic, partition, offset, timestamp, key, headers, source id mode, and
  selected protocol remain envelope attributes
- `runtime-protocol-*` modules continue to parse protocol payloads without
  Kafka dependencies
- `runtime-app` owns Kafka consumer configuration and standalone collector
  assembly while keeping Kafka APIs out of `runtime-core`
- [`examples/collector-kafka.properties`](examples/collector-kafka.properties)
  shows the minimal IEC104-over-Kafka collector configuration

The detailed plan is maintained in
[`docs/roadmap-0.7.0.md`](docs/roadmap-0.7.0.md).

## `0.6.0` HTTP Runtime-App Release

`0.6.0` published the HTTP productionization line. The JDK-only HTTP ingress is
usable from the standalone runtime app while preserving the current TCP
collector path:

- `runtime-core` remains free of HTTP, Kafka, MQTT, Spring, database, Redis,
  and observability exporter dependencies
- `runtime-ingress-http` owns HTTP request handling, source mapping, response
  policy, request limits, lifecycle, and adapter-specific tests
- `runtime-app` owns HTTP listener configuration and app assembly
- `runtime-protocol-*` modules continue to parse protocol payloads without
  transport or app dependencies
- Kafka and MQTT remain design-only until dedicated implementation modules are
  opened

The detailed plan is maintained in
[`docs/roadmap-0.6.0.md`](docs/roadmap-0.6.0.md).

## `0.5.0` Adapter Boundary Plan

`0.5.0` opens the adapter productionization line. The first target is
to define how HTTP, Kafka, and MQTT ingestion fit around existing runtime
contracts without polluting the core:

- `runtime-core` remains free of HTTP, Kafka, MQTT, Spring, database, Redis,
  and observability exporter dependencies
- HTTP request limits, response policy, and payload/source mapping are now
  implemented first in the JDK-only `runtime-ingress-http` baseline
- Kafka topic/partition/offset attributes, commit timing, and replay posture
  are documented for a future `runtime-ingress-kafka` module
- MQTT topic/source mapping, QoS posture, retained-message handling, and
  reconnect/session ownership are documented for a future
  `runtime-ingress-mqtt` module
- downstream delivery adapters, such as Kafka sinks, stay separate from
  ingress adapters
- `runtime-app` remains the deployable assembly boundary

The detailed plan is maintained in
[`docs/roadmap-0.5.0.md`](docs/roadmap-0.5.0.md).

## `0.4.0` Multi-Protocol Runtime Release

`0.4.0` moves the runtime from an IEC104-only app baseline toward a
multi-protocol collector runtime without changing dependency direction:

- publish the Maven reactor at `0.4.0`
- consume published `protocol-sdk:0.7.0` parser artifacts
- provide IEC101, IEC103, and Modbus runtime bindings as separate
  `runtime-protocol-*` modules
- keep protocol binding modules free of transport and app dependencies
- add app-level protocol selection while preserving existing IEC104
  `collector.properties` compatibility
- keep serial, UDP, Kafka, MQTT, HTTP, database, Redis, and observability
  dependencies outside `runtime-core` and `protocol-sdk`

The detailed plan is maintained in
[`docs/roadmap-0.4.0.md`](docs/roadmap-0.4.0.md).

## Runtime Core Contract

`runtime-core` is intentionally dependency-light. It defines the contracts that
transport adapters and protocol bindings share:

- `SourceId` identifies a data source across transports.
- `IngressEnvelope` carries source, transport, payload, timestamp, and
  attributes into the parser boundary.
- `RuntimeParserBinding` adapts protocol SDK parsers to runtime parse results.
- `ParsedRecord` and `ParseFailure` describe successful and failed parse output.
- `RecordSink` and `FailureSink` receive routed parse output.
- `BackpressureStrategy` returns an `ACCEPT`, `RETRY_LATER`, or `DROP`
  decision before parsing.
- `RuntimePipelineRunner` wires parser binding, sinks, backpressure, and
  lifecycle together.

`runtime-core` must not depend on Spring, Netty, Kafka, MQTT, HTTP clients or
servers, database drivers, Redis clients, or any deployable runtime adapter.
Those dependencies belong in adapter modules outside the core contract. The
current Netty dependency is isolated to `runtime-ingress-tcp-netty`.

## TCP Netty Ingress

`runtime-ingress-tcp-netty` currently provides the first TCP ingress baseline:

- `TcpNettyServer` binds a configured host/port and shuts down Netty event loop
  groups gracefully.
- `TcpNettyServerConfig` supports loopback or any-address binding, including
  port `0` for tests.
- `TcpNettyChannelInitializer` creates one `RuntimePipelineRunner` per accepted
  connection through `TcpNettyPipelineRunnerFactory`.
- `TcpConnectionSession` records the resolved `SourceId`, channel id, session
  id, local/remote socket addresses, connection timestamp, and stable envelope
  attributes for each accepted connection.
- `TcpConnectionRegistry` tracks active sessions and lets `TcpNettyServer`
  close active clients during graceful shutdown.
- `TcpNettyIngressHandler` copies inbound `ByteBuf` data into an immutable
  `IngressEnvelope` payload using the connection session attributes.
- `TcpSourceIdResolver` resolves a runtime `SourceId` from the remote address or
  channel id.
- `TcpConnectionAttributes` attaches `tcp.channel.id`, `tcp.session.id`,
  `tcp.source.namespace`, `tcp.source.value`, local address, remote address, and
  connection timestamp attributes when available.
- `TcpConnectionLifecycleEvent` publishes active, inactive, and exception events
  through the Netty pipeline for adapter-level observability.
- `RuntimePipelineRunner` receives each envelope and owns parser binding,
  backpressure, record sink, failure sink, and lifecycle routing.
- TCP channel exceptions are reported to the runtime failure sink and then close
  the channel.
- `RETRY_LATER` backpressure pauses Netty `autoRead`; `DROP` emits a
  `TcpNettyBackpressureEvent` without pausing the channel.

The module is still a baseline. It does not yet manage reconnects, expose
protocol-specific server builders, provide TLS, implement protocol heartbeats,
or provide durable retry queues.

## Minimal IEC104 TCP Runtime

The low-level APIs are intentionally explicit in `0.1.0`. Applications own
their sink, lifecycle, and deployment assembly:

```java
List<ParsedRecord<Iec104Frame>> records = new CopyOnWriteArrayList<>();
List<ParseFailure> failures = new CopyOnWriteArrayList<>();

TcpNettyServer<Iec104Frame> server = new TcpNettyServer<>(
        TcpNettyServerConfig.loopback(2404),
        channel -> new RuntimePipelineRunner<>(
                new Iec104RuntimeBinding(),
                records::add,
                failures::add,
                BackpressureStrategy.acceptAll()));

server.bind();
```

This starts a Netty TCP listener, creates one `RuntimePipelineRunner` per
connection, decodes IEC104 bytes through `protocol-iec104:0.7.0`, and routes
successful frames and failures to the configured sinks. Production applications
should add their own lifecycle owner, logging, persistence, reconnect policy,
TLS, and command/session policy around this baseline.

## Standalone Collector App

`runtime-app` assembles the runnable collector boundary introduced in `0.2.0`.
The standalone app preserves the TCP/Netty, JDK HTTP, Kafka, MQTT, and
management endpoint paths through the same app-owned pipeline:

```text
TcpNettyServer, HttpIngressServer, KafkaRecordSource, or MqttMessageSource
  -> RuntimePipelineRunner
  -> selected RuntimeParserBinding
  -> configured RecordSink / FailureSink
```

Build the executable jar:

```bash
mvn -q -pl runtime-app -am package
```

The same command builds the current development-line distribution package under
`runtime-app/target/`. Published `0.16.0` package artifacts are available from
Maven Central.

Run with the example property file:

```bash
java -jar runtime-app/target/runtime-app-0.17.0-SNAPSHOT-standalone.jar \
  --config examples/collector.properties
```

Then send one IEC104 single-point test frame from another terminal:

```bash
java examples/Iec104SendSinglePoint.java 127.0.0.1 2404
```

Inspect the newline-delimited file sink output:

```bash
tail -f target/runtime-records.ndjson
```

You can also run the full local smoke flow:

```bash
sh examples/smoke-standalone.sh
```

The distribution package smoke exercises the packaged `bin/`, `conf/`, `lib`,
runtime directory layout, version diagnostics, and archive checksum
verification:

```bash
sh examples/smoke-distribution-package.sh
```

The release artifact smoke verifies local or downloaded package artifacts and
runs `java-check`, `version`, `verify-package`, `validate`, `dry-run`, `start`,
`status`, and `stop`:

```bash
sh examples/smoke-release-artifact.sh
```

The sink failure smoke verifies file sink failure classification,
failed-record isolation, and sink-failure-triggered readiness evidence:

```bash
sh examples/smoke-sink-failure.sh
```

The HTTP collector smoke starts an HTTP-only app, posts a raw IEC104 APDU with
`curl`, and verifies file-sink output:

```bash
sh examples/smoke-standalone-http.sh
```

MQTT app assembly uses the same runtime pipeline. The example configuration
expects a broker at `tcp://localhost:1883`:

```bash
java -jar runtime-app/target/runtime-app-0.17.0-SNAPSHOT-standalone.jar \
  --config examples/collector-mqtt.properties
```

If your default `java` is older than JDK 21, set `JAVA_BIN` before running the
smoke script:

```bash
JAVA_BIN=/path/to/jdk-21-or-newer/bin/java sh examples/smoke-standalone.sh
JAVA_BIN=/path/to/jdk-21-or-newer/bin/java sh examples/smoke-standalone-http.sh
```

Minimal configuration keys:

```properties
collector.tcp.host=0.0.0.0
collector.tcp.port=2404
collector.protocol=iec104
collector.source.id=iec104:station-1
collector.backpressure=ACCEPT
collector.backpressure.maxPayloadBytes=0
collector.backpressure.oversizedPayloadDecision=DROP
collector.backpressure.sinkFailureThreshold=0
collector.backpressure.sinkFailureDecision=RETRY_LATER
collector.sink.type=file
collector.sink.file=target/runtime-records.ndjson
collector.sink.file.maxBytes=10485760
collector.sink.file.maxHistory=5
collector.sink.failedRecords.enabled=true
collector.sink.failedRecords.dir=target/failed-records
collector.sink.failedRecords.maxSamples=32
collector.iec104.strictAsduParsing=false
```

Supported sink types are `logging`, `file`, and `in-memory`. The app remains a
thin assembly layer; Spring, Kafka, MQTT, HTTP, database, and Redis dependencies
are still excluded from `runtime-core` and `protocol-sdk`.

### CLI And Configuration

`StandaloneCollectorMain` accepts either a property file or inline overrides:

```bash
java -jar runtime-app/target/runtime-app-0.17.0-SNAPSHOT-standalone.jar \
  --config examples/collector.properties \
  --collector.tcp.port=2405 \
  --collector.sink.type=logging
```

Inline `--key=value` arguments are applied after earlier `--config` files, so
they can override checked-in defaults for local runs.

| Key | Default | Description |
| --- | --- | --- |
| `collector.tcp.host` | `0.0.0.0` | TCP listen host. Use `127.0.0.1` for local-only testing. |
| `collector.tcp.port` | `2404` | TCP listen port. Port `0` requests an ephemeral port for smoke tests. |
| `collector.tcp.bossThreads` | `1` | Netty boss event loop threads. |
| `collector.tcp.workerThreads` | `1` | Netty worker event loop threads. |
| `collector.http.listeners` | unset | Comma-separated HTTP listener names. When this key is set without `collector.tcp.listeners`, the app starts HTTP listeners only. |
| `collector.http.listener.<name>.host` | `127.0.0.1` | HTTP listen host for a named HTTP listener. |
| `collector.http.listener.<name>.port` | required | HTTP listen port. Port `0` requests an ephemeral port for tests. |
| `collector.http.listener.<name>.path` | `/ingress` | HTTP context path. `PATH` source-id mode requires a path ending in `{sourceId}`. |
| `collector.http.listener.<name>.source` | required | Referenced `collector.sources` entry, or `default` for the legacy single-source path. |
| `collector.http.listener.<name>.sourceIdMode` | `CONFIGURED` | One of `CONFIGURED`, `HEADER`, or `PATH`. |
| `collector.http.listener.<name>.sourceIdHeader` | unset | Required when `sourceIdMode=HEADER`. |
| `collector.http.listener.<name>.maxPayloadBytes` | `0` | HTTP adapter request limit before the runtime pipeline is called. `0` disables the limit. |
| `collector.http.listener.<name>.responseMode` | `ACK_ON_ACCEPT` | One of `ACK_ON_ACCEPT` or `NO_BODY`. |
| `collector.http.listener.<name>.backlog` | `0` | JDK `HttpServer` backlog. |
| `collector.http.listener.<name>.workerThreads` | `1` | JDK `HttpServer` worker threads. |
| `collector.protocol` | `iec104` | Protocol binding for the legacy single-source configuration. Supported values are `iec104`, `iec101`, `iec103`, and `modbus`. |
| `collector.source.id` | `iec104:station-1` | Runtime source id in `namespace:value` form. |
| `collector.backpressure` | `ACCEPT` | One of `ACCEPT`, `RETRY_LATER`, or `DROP`. |
| `collector.backpressure.maxPayloadBytes` | `0` | Optional app-level payload-size threshold before parsing. `0` disables the threshold. |
| `collector.backpressure.oversizedPayloadDecision` | `DROP` | Decision for payloads larger than `collector.backpressure.maxPayloadBytes`; one of `DROP` or `RETRY_LATER`. |
| `collector.backpressure.sinkFailureThreshold` | `0` | Optional app-level sink-failure threshold before parsing. `0` disables the threshold. |
| `collector.backpressure.sinkFailureDecision` | `RETRY_LATER` | Decision after sink failures reach `collector.backpressure.sinkFailureThreshold`; one of `RETRY_LATER` or `DROP`. |
| `collector.sink.type` | `logging` | One of `logging`, `file`, or `in-memory`. |
| `collector.sink.file` | unset | Required when `collector.sink.type=file`. |
| `collector.sink.file.maxBytes` | `10485760` | Rotate the file sink before the active file grows beyond this byte limit. |
| `collector.sink.file.maxHistory` | `5` | Number of rotated file sink history files to keep. |
| `collector.sink.failedRecords.enabled` | `true` | Enables app-local failed-record sample isolation when the configured record/failure sink throws. |
| `collector.sink.failedRecords.dir` | `collector.runtime.dataDir/failed-records` | Directory for bounded failed-record JSON samples. Relative paths are resolved against `collector.runtime.dataDir`. |
| `collector.sink.failedRecords.maxSamples` | `32` | Maximum retained failed-record samples; `0` disables sample writes while keeping status fields. |
| `collector.iec104.strictAsduParsing` | `false` | Enables strict IEC104 ASDU parsing in the SDK binding. |
| `collector.management.enabled` | `false` | Enables the app-owned JDK `HttpServer` management endpoint. |
| `collector.management.host` | `127.0.0.1` | Management listen host. Keep loopback unless an explicit access-control boundary is configured. |
| `collector.management.port` | `8081` | Management listen port. Port `0` requests an ephemeral port for tests and smoke scripts. |
| `collector.management.healthPath` | `/health` | Health endpoint path. |
| `collector.management.readinessPath` | `/readiness` | Readiness endpoint path. |
| `collector.management.statusPath` | `/status` | Runtime status and management metrics endpoint path. |
| `collector.management.access` | `local` | Management access mode: `local`, `open`, or `token`. |
| `collector.management.token` | unset | Required when `collector.management.access=token`; never emitted in status JSON or request logs. |
| `collector.management.requestLogging.enabled` | `true` | Logs management method, path, status, duration, remote address, and rejection reason without headers or payload bytes. |
| `collector.management.healthHistory.maxEntries` | `32` | Bounded in-memory health-history entries exposed by `/status`. Use `0` to disable history capture. |

`0.3.0` introduces startup validation and an internal multi-source,
multi-listener configuration model while preserving the single-source keys
above. The application validates source ids, TCP ports, thread counts, sink
type, file sink paths, duplicate sources, and duplicate listener endpoints
before opening network ports.

Named sources and listeners use explicit lists:

```properties
collector.sources=station-a,station-b
collector.source.station-a.id=iec104:station-a
collector.source.station-a.protocol=iec104
collector.source.station-b.id=modbus:station-b
collector.source.station-b.protocol=modbus

collector.tcp.listeners=north,south
collector.tcp.listener.north.host=127.0.0.1
collector.tcp.listener.north.port=2404
collector.tcp.listener.north.source=station-a
collector.tcp.listener.south.host=127.0.0.1
collector.tcp.listener.south.port=2405
collector.tcp.listener.south.source=station-b

collector.backpressure.maxPayloadBytes=65536
collector.backpressure.oversizedPayloadDecision=DROP
collector.sink.type=file
collector.sink.file=target/runtime-records.ndjson
collector.sink.file.maxBytes=10485760
collector.sink.file.maxHistory=5
```

Named listeners inherit the protocol from their referenced source. `iec104`,
`iec101`, and `iec103` currently use the TCP byte-stream ingress as an app-level
baseline; serial adapters remain deferred. `modbus` selects the Modbus TCP
stream binding; Modbus UDP remains deferred to a future UDP ingress adapter.

`0.6.0` adds app-level HTTP listener assembly. Declaring
`collector.http.listeners` without `collector.tcp.listeners` starts an
HTTP-only collector and keeps the legacy TCP defaults disabled for that run:

```properties
collector.source.id=iec104:station-1
collector.protocol=iec104

collector.http.listeners=http-main
collector.http.listener.http-main.host=127.0.0.1
collector.http.listener.http-main.port=8080
collector.http.listener.http-main.path=/ingress
collector.http.listener.http-main.source=default
collector.http.listener.http-main.sourceIdMode=CONFIGURED
collector.http.listener.http-main.maxPayloadBytes=65536
collector.http.listener.http-main.responseMode=ACK_ON_ACCEPT
collector.http.listener.http-main.workerThreads=2

collector.sink.type=file
collector.sink.file=target/runtime-http-records.ndjson
```

For dynamic HTTP source mapping, set `sourceIdMode=HEADER` with
`sourceIdHeader`, or set `sourceIdMode=PATH` with a path ending in
`{sourceId}`. HTTP `ACCEPT`, `RETRY_LATER`, and `DROP` runtime decisions are
translated into adapter-owned HTTP responses, while malformed protocol payloads
are routed to the configured failure sink.

### Lifecycle And Status Snapshot

`0.3.0` adds a local lifecycle and status snapshot API for `runtime-app`.
`StandaloneCollector` starts in `CONFIGURED`, moves through `STARTING` and
`RUNNING`, and records `STOPPING`, `STOPPED`, or `FAILED` outcomes. Startup
failures keep the collector in `FAILED` after rollback so the reason remains
inspectable.

```java
StandaloneCollector collector = StandaloneCollector.create(appConfig);

CollectorStatusSnapshot configured = collector.statusSnapshot();
collector.start();
CollectorStatusSnapshot running = collector.statusSnapshot();
collector.stop();
CollectorStatusSnapshot stopped = collector.statusSnapshot();
```

`StandaloneCollectorMain` also writes a single-line status snapshot after a
successful start and again during shutdown. The line starts with
`Protocol Runtime collector status` and includes listener, sink, backpressure,
and counter summaries for local log inspection.

The snapshot includes:

- lifecycle state
- derived health state and readiness state
- health reasons for non-healthy or non-ready collectors
- startup failure reason and last exception type/message
- start and stop timestamps
- source summaries
- TCP and HTTP listener configured host/port and bound host/port
- HTTP listener path, source id mode, response mode, payload limit, backlog,
  and worker thread summary
- per-TCP-listener and total active connection counts
- parsed record and parse failure counters
- last parse failure source id, message, observed timestamp, cause type,
  payload size, payload preview hex, and TCP/session attributes
- backpressure retry/drop counters and last backpressure decision details
- sink failure counters and the latest sink failure target, source id,
  exception type, and message
- sink type, file sink output path/open state/active byte count/history count,
  file rotation policy, backpressure mode, payload threshold policy, and strict
  ASDU setting
- sink failure backpressure threshold and decision

`0.10.0` adds app-local health/readiness derivation on top of the
snapshot. `CollectorHealthSnapshot` reports `HEALTHY`, `DEGRADED`, `FAILED`,
`CONFIGURED`, `STARTING`, `STOPPING`, or `STOPPED`, plus `READY` or
`NOT_READY`. A running collector is `READY` only when at least one listener is
configured, all configured listeners are running, and a configured file sink is
open. Parse failures, sink failures, and backpressure decisions degrade health
while preserving readiness if the collector can still accept ingress.

The single-line status output now includes `health=...`, `readiness=...`, and
`healthReasons=[...]` so local logs can distinguish healthy, degraded, failed,
and stopped runtime states without an external management endpoint.

Operator-facing examples, a status matrix, reason catalog, and triage order are
documented in
[`docs/status-health-readiness.md`](docs/status-health-readiness.md).

### File Sink Format

The file sink writes one stable JSONL envelope per parsed record or parse
failure. `0.17.0` uses schema versions `protocol-runtime.record.v1`,
`protocol-runtime.parse-failure.v1`, and `protocol-runtime.failed-record.v1`.
It rotates before the active file grows beyond `collector.sink.file.maxBytes`
and keeps `collector.sink.file.maxHistory` history files. For an output path
such as `target/runtime-records.ndjson`, rotated files are named
`runtime-records.ndjson.1`, `runtime-records.ndjson.2`, and so on. Collector
status output reports the active file path, whether the file sink is currently
open, active file bytes, retained history file count, cumulative in-process
rotation count, configured rotation limits, active schema version,
failed-record isolation path, retained sample count, and last sink failure
classification.

A successful record includes:

- `schemaVersion`: `protocol-runtime.record.v1`
- `kind`: `record`
- `sourceId`
- `protocol`
- `receivedAt` when the ingress adapter provides receive metadata
- `parsedAt`
- `recordType`
- `observedAt`
- `rawPayloadHex`
- `quality.status`
- `payload.value`, `payload.rawHex`, and `payload.rawSize`
- `raw.metadata`
- `parser.diagnostics`
- `sink.delivery`
- `attributes`

Parse failures use `schemaVersion=protocol-runtime.parse-failure.v1` and
`kind=failure`, then include `message`, `rawPayloadHex`, raw metadata, parser
diagnostics, TCP/session attributes, and optional `cause`. The app's current
parse failure policy is continue: malformed frames are routed to the configured
failure sink and do not stop the collector or prevent later healthy frames from
parsing.

The app also isolates runtime sink failures at the app assembly boundary. If a
record or failure sink throws while handling a parsed result, the exception is
captured in the collector metrics and status output instead of being propagated
back into `runtime-core` or the ingress adapter. Failed samples are written as
`protocol-runtime.failed-record.v1` JSON under
`collector.sink.failedRecords.dir`, with `failureType`, exception type, message,
retryability, source id, protocol, raw payload hex, and safe record metadata.
Operators can optionally set `collector.backpressure.sinkFailureThreshold` and
`collector.backpressure.sinkFailureDecision` so subsequent ingress payloads are
rejected with `RETRY_LATER` or `DROP` after downstream sink failures reach the
configured threshold.

### Troubleshooting

- `UnsupportedClassVersionError`: run the standalone jar with JDK 21 or newer.
  The runtime artifacts are compiled with Java release 21.
- `JDK 21 or newer is required`: the packaged `bin/protocol-runtime` script is
  using an older default `java`. Set `JAVA_HOME` or `JAVA_BIN` to a JDK 21+
  installation.
- `collector already appears to be running`: the packaged start script found a
  live process in the configured PID file. Stop it first or choose a different
  runtime directory.
- `Address already in use`: change `collector.tcp.port` or stop the process
  currently using that port.
- No file output: confirm `collector.sink.type=file`, `collector.sink.file` is
  set, and the client actually sent bytes to the collector port.
- Failed records not exported: check `collector.sink.failedRecords.enabled`,
  `collector.sink.failedRecords.dir`, directory permissions, and
  `sink.failedRecords.isolationFailureCount` in status JSON.
- Sink failure backpressure is active: inspect
  `metrics.delivery.sinkFailureTypeCounts`,
  `metrics.lastSinkFailure.deliveryFailureType`, and
  `collector.backpressure.sinkFailureThreshold`.
- No parsed record for the example frame: confirm `collector.backpressure` is
  `ACCEPT`; `RETRY_LATER` intentionally prevents parsing.
- `collector.source.id must use namespace:value format`: use a value such as
  `iec104:station-1`.

## Smoke Tests

`runtime-smoke-tests` holds cross-module verification only. The smoke tests feed
TCP bytes through:

```text
EmbeddedChannel or real localhost Socket
  -> TcpNettyServer / TcpNettyIngressHandler
  -> RuntimePipelineRunner
  -> selected RuntimeParserBinding
  -> RecordSink / FailureSink
```

The IEC104 smoke test covers complete frames, split TCP reads, backpressure that
prevents parsing, malformed frames routed to the failure sink, a real TCP socket
path through the server bootstrap, and connection disconnect behavior that stops
the per-connection runner. The multi-protocol smoke test adds IEC101, IEC103,
and Modbus TCP byte-stream paths through the same ingress and runner boundary.

## Dependency Direction

Allowed:

```text
protocol-runtime -> protocol-sdk
```

Forbidden:

```text
protocol-sdk -> protocol-runtime
protocol-sdk -> Spring or Netty
protocol-sdk -> MQTT or Kafka clients
protocol-sdk -> HTTP server/client frameworks
protocol-sdk -> database or Redis clients
runtime-core -> Netty
runtime-core -> protocol-specific runtime bindings
```

## Build

JDK 21 is required.

```bash
mvn -q verify
```

## SDK Version

The bootstrap runtime consumes published SDK `0.7.0` artifacts. IEC104,
IEC101, IEC103, and Modbus runtime bindings are implemented:

- `io.github.qbsstg:protocol-core:0.7.0`
- `io.github.qbsstg:protocol-iec104:0.7.0`
- `io.github.qbsstg:protocol-iec101:0.7.0`
- `io.github.qbsstg:protocol-iec103:0.7.0`
- `io.github.qbsstg:protocol-modbus:0.7.0`

The runtime can move to newer SDK versions after they are published and
verified.

## Release Docs

- [`docs/module-plan.md`](docs/module-plan.md)
- [`docs/module-boundaries.md`](docs/module-boundaries.md)
- [`docs/distribution-package.md`](docs/distribution-package.md)
- [`docs/runtime-ingress-http-design.md`](docs/runtime-ingress-http-design.md)
- [`docs/runtime-ingress-kafka-design.md`](docs/runtime-ingress-kafka-design.md)
- [`docs/runtime-ingress-mqtt-design.md`](docs/runtime-ingress-mqtt-design.md)
- [`docs/roadmap-0.2.0.md`](docs/roadmap-0.2.0.md)
- [`docs/roadmap-0.3.0.md`](docs/roadmap-0.3.0.md)
- [`docs/roadmap-0.4.0.md`](docs/roadmap-0.4.0.md)
- [`docs/roadmap-0.5.0.md`](docs/roadmap-0.5.0.md)
- [`docs/roadmap-0.6.0.md`](docs/roadmap-0.6.0.md)
- [`docs/roadmap-0.7.0.md`](docs/roadmap-0.7.0.md)
- [`docs/roadmap-0.8.0.md`](docs/roadmap-0.8.0.md)
- [`docs/roadmap-0.9.0.md`](docs/roadmap-0.9.0.md)
- [`docs/roadmap-0.10.0.md`](docs/roadmap-0.10.0.md)
- [`docs/roadmap-0.11.0.md`](docs/roadmap-0.11.0.md)
- [`docs/roadmap-0.12.0.md`](docs/roadmap-0.12.0.md)
- [`docs/roadmap-0.13.0.md`](docs/roadmap-0.13.0.md)
- [`docs/roadmap-0.14.0.md`](docs/roadmap-0.14.0.md)
- [`docs/roadmap-0.15.0.md`](docs/roadmap-0.15.0.md)
- [`docs/roadmap-0.16.0.md`](docs/roadmap-0.16.0.md)
- [`docs/roadmap-0.17.0.md`](docs/roadmap-0.17.0.md)
- [`docs/release.md`](docs/release.md)
- [`docs/release-readiness-0.16.0.md`](docs/release-readiness-0.16.0.md)
- [`docs/release-readiness-0.15.0.md`](docs/release-readiness-0.15.0.md)
- [`docs/release-readiness-0.14.0.md`](docs/release-readiness-0.14.0.md)
- [`docs/release-readiness-0.13.0.md`](docs/release-readiness-0.13.0.md)
- [`docs/release-readiness-0.12.0.md`](docs/release-readiness-0.12.0.md)
- [`docs/release-readiness-0.11.0.md`](docs/release-readiness-0.11.0.md)
- [`docs/release-readiness-0.10.0.md`](docs/release-readiness-0.10.0.md)
- [`docs/release-readiness-0.8.0.md`](docs/release-readiness-0.8.0.md)
- [`docs/release-readiness-0.1.0.md`](docs/release-readiness-0.1.0.md)
- [`docs/release-readiness-0.2.0.md`](docs/release-readiness-0.2.0.md)
- [`docs/release-readiness-0.3.0.md`](docs/release-readiness-0.3.0.md)
- [`docs/release-readiness-0.4.0.md`](docs/release-readiness-0.4.0.md)
- [`docs/release-readiness-0.5.0.md`](docs/release-readiness-0.5.0.md)
- [`docs/release-readiness-0.6.0.md`](docs/release-readiness-0.6.0.md)
- [`docs/release-readiness-0.7.0.md`](docs/release-readiness-0.7.0.md)
- [`docs/release-readiness-0.9.0.md`](docs/release-readiness-0.9.0.md)
- [`docs/release-notes-0.1.0.md`](docs/release-notes-0.1.0.md)
- [`docs/release-notes-0.2.0.md`](docs/release-notes-0.2.0.md)
- [`docs/release-notes-0.3.0.md`](docs/release-notes-0.3.0.md)
- [`docs/release-notes-0.4.0.md`](docs/release-notes-0.4.0.md)
- [`docs/release-notes-0.5.0.md`](docs/release-notes-0.5.0.md)
- [`docs/release-notes-0.6.0.md`](docs/release-notes-0.6.0.md)
- [`docs/release-notes-0.7.0.md`](docs/release-notes-0.7.0.md)
- [`docs/release-notes-0.8.0.md`](docs/release-notes-0.8.0.md)
- [`docs/release-notes-0.9.0.md`](docs/release-notes-0.9.0.md)
- [`docs/release-notes-0.10.0.md`](docs/release-notes-0.10.0.md)
- [`docs/release-notes-0.11.0.md`](docs/release-notes-0.11.0.md)
- [`docs/release-notes-0.12.0.md`](docs/release-notes-0.12.0.md)
- [`docs/release-notes-0.13.0.md`](docs/release-notes-0.13.0.md)
- [`docs/release-notes-0.14.0.md`](docs/release-notes-0.14.0.md)
- [`docs/release-notes-0.15.0.md`](docs/release-notes-0.15.0.md)
- [`docs/release-notes-0.16.0.md`](docs/release-notes-0.16.0.md)
- [`docs/release-notes-0.17.0.md`](docs/release-notes-0.17.0.md)
