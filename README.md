# Protocol Runtime

[简体中文](README.zh-CN.md)

Protocol Runtime is the JDK 21 collector runtime for the Java 8 compatible
[`protocol-sdk`](https://github.com/Qbsstg/protocol-sdk) parser modules.

The runtime owns ingestion, session lifecycle, parser binding, backpressure,
batching, and downstream delivery concerns. The SDK remains parser-only and
must not depend on this repository.

## Status

This repository is in bootstrap. The first release line is `0.1.0`: a small
runtime-core contract surface, an IEC104 binding that consumes the published
`protocol-sdk` `0.7.0` artifacts from Maven Central, and the first TCP/Netty
ingress baseline.

The active development line is `0.2.0-SNAPSHOT`. Its first goal is a minimal
standalone IEC104 TCP collector app that assembles the published runtime
contracts into a JDK 21 process.

## Maven Coordinates

The first runtime release version is `0.1.0`. Runtime modules are JDK 21
artifacts. Applications should depend on the modules they use directly after
the release is published:

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

`runtime-smoke-tests` is a repository test module and is not intended as a
published application dependency.

## Module Plan

| Module | Status | Responsibility |
| --- | --- | --- |
| `runtime-core` | Bootstrap | Runtime-neutral contracts: source identity, ingress envelope, parser binding, parse results, record/failure sinks, backpressure, pipeline runner, and lifecycle boundary. |
| `runtime-protocol-iec104` | Bootstrap | First runtime protocol binding around `io.github.qbsstg:protocol-iec104:0.7.0`. |
| `runtime-ingress-tcp-netty` | Baseline | Minimal Netty TCP ingress handler and server bootstrap that bind a TCP port, create one `RuntimePipelineRunner` per accepted connection, convert `ByteBuf` payloads to `IngressEnvelope`, apply backpressure decisions, and dispatch to sinks. |
| `runtime-app` | 0.2.0 baseline | Standalone collector assembly for IEC104 over TCP with property-based configuration, JDK logging/file/in-memory sinks, and an executable shaded jar. |
| `runtime-smoke-tests` | Test-only | Cross-module smoke tests that prove ingress, runtime-core, and protocol bindings work together without turning those combinations into production dependencies. |

Future modules may include MQTT, Kafka, HTTP ingress, pipelines, additional
sinks, and richer deployable runtime applications. Those dependencies belong
here, not in `protocol-sdk`.

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

`runtime-app` assembles the first runnable collector boundary for `0.2.0`:

```text
TcpNettyServer
  -> RuntimePipelineRunner
  -> Iec104RuntimeBinding
  -> configured RecordSink / FailureSink
```

Build the executable jar:

```bash
mvn -q -pl runtime-app -am package
```

Run with the example property file:

```bash
java -jar runtime-app/target/runtime-app-0.2.0-SNAPSHOT-standalone.jar \
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

If your default `java` is older than JDK 21, set `JAVA_BIN` before running the
smoke script:

```bash
JAVA_BIN=/path/to/jdk-21-or-newer/bin/java sh examples/smoke-standalone.sh
```

Minimal configuration keys:

```properties
collector.tcp.host=0.0.0.0
collector.tcp.port=2404
collector.source.id=iec104:station-1
collector.backpressure=ACCEPT
collector.sink.type=file
collector.sink.file=target/runtime-records.ndjson
collector.iec104.strictAsduParsing=false
```

Supported sink types are `logging`, `file`, and `in-memory`. The app remains a
thin assembly layer; Spring, Kafka, MQTT, HTTP, database, and Redis dependencies
are still excluded from `runtime-core` and `protocol-sdk`.

### CLI And Configuration

`StandaloneCollectorMain` accepts either a property file or inline overrides:

```bash
java -jar runtime-app/target/runtime-app-0.2.0-SNAPSHOT-standalone.jar \
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
| `collector.source.id` | `iec104:station-1` | Runtime source id in `namespace:value` form. |
| `collector.backpressure` | `ACCEPT` | One of `ACCEPT`, `RETRY_LATER`, or `DROP`. |
| `collector.sink.type` | `logging` | One of `logging`, `file`, or `in-memory`. |
| `collector.sink.file` | unset | Required when `collector.sink.type=file`. |
| `collector.iec104.strictAsduParsing` | `false` | Enables strict IEC104 ASDU parsing in the SDK binding. |

### File Sink Format

The file sink writes one JSON-like line per parsed record or parse failure. A
successful record includes:

- `kind`: `record`
- `sourceId`
- `protocol`
- `recordType`
- `observedAt`
- `rawPayloadHex`
- `value`
- `attributes`

Parse failures use `kind=failure` and include `message`, `rawPayloadHex`, and
optional `cause`.

### Troubleshooting

- `UnsupportedClassVersionError`: run the standalone jar with JDK 21 or newer.
  The runtime artifacts are compiled with Java release 21.
- `Address already in use`: change `collector.tcp.port` or stop the process
  currently using that port.
- No file output: confirm `collector.sink.type=file`, `collector.sink.file` is
  set, and the client actually sent bytes to the collector port.
- No parsed record for the example frame: confirm `collector.backpressure` is
  `ACCEPT`; `RETRY_LATER` intentionally prevents parsing.
- `collector.source.id must use namespace:value format`: use a value such as
  `iec104:station-1`.

## Smoke Tests

`runtime-smoke-tests` holds cross-module verification only. The first smoke test
feeds IEC104 TCP bytes through:

```text
EmbeddedChannel or real localhost Socket
  -> TcpNettyServer / TcpNettyIngressHandler
  -> RuntimePipelineRunner
  -> Iec104RuntimeBinding
  -> RecordSink / FailureSink
```

It covers complete IEC104 frames, split TCP reads, backpressure that prevents
parsing, malformed IEC104 frames routed to the failure sink, a real TCP socket
path through the server bootstrap, and connection disconnect behavior that stops
the per-connection runner.

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

The bootstrap runtime consumes published SDK `0.7.0` artifacts:

- `io.github.qbsstg:protocol-core:0.7.0`
- `io.github.qbsstg:protocol-iec104:0.7.0`

The runtime can move to newer SDK versions after they are published and
verified.

## Release Docs

- [`docs/module-plan.md`](docs/module-plan.md)
- [`docs/module-boundaries.md`](docs/module-boundaries.md)
- [`docs/roadmap-0.2.0.md`](docs/roadmap-0.2.0.md)
- [`docs/release.md`](docs/release.md)
- [`docs/release-readiness-0.1.0.md`](docs/release-readiness-0.1.0.md)
- [`docs/release-notes-0.1.0.md`](docs/release-notes-0.1.0.md)
- [`docs/release-notes-0.2.0.md`](docs/release-notes-0.2.0.md)
