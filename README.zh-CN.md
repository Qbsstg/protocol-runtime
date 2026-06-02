# Protocol Runtime

[English](README.md)

Protocol Runtime 是面向采集平台的 JDK 21 运行时项目，用来承载
[`protocol-sdk`](https://github.com/Qbsstg/protocol-sdk) 提供的 Java 8
兼容协议解析能力。

`protocol-sdk` 只负责协议解析；`protocol-runtime` 负责采集运行时相关的
能力，例如接入、连接会话、解析绑定、背压、批处理和下游投递。

## 当前状态

当前仓库仍处于 bootstrap 阶段。第一个发布线是 `0.1.0`，它包含：

- 一个轻量的 `runtime-core` 合同层。
- 一个基于已发布 `protocol-iec104:0.7.0` 的 IEC104 运行时绑定。
- 第一个 TCP/Netty 接入基线。

当前开发线是 `0.2.0-SNAPSHOT`。这一阶段的第一个目标是提供一个最小
standalone IEC104 TCP collector，把已经发布的 runtime 合同组装成一个可启动的
JDK 21 进程。

## Maven 坐标

第一个运行时发布版本是 `0.1.0`。Runtime 模块是 JDK 21 artifact。应用侧应
按需直接依赖具体模块：

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

`runtime-smoke-tests` 是仓库内部的测试模块，不建议作为应用依赖使用。

## 模块规划

| 模块 | 状态 | 职责 |
| --- | --- | --- |
| `runtime-core` | Bootstrap | 运行时无关合同：数据源标识、接入 envelope、解析绑定、解析结果、记录/失败 sink、背压、pipeline runner、生命周期边界。 |
| `runtime-protocol-iec104` | Bootstrap | 基于 `io.github.qbsstg:protocol-iec104:0.7.0` 的第一个运行时协议绑定。 |
| `runtime-ingress-tcp-netty` | Baseline | 最小 TCP/Netty 接入处理器和 server bootstrap：监听 TCP 端口、为每个连接创建一个 `RuntimePipelineRunner`、把 `ByteBuf` 转为 `IngressEnvelope`、处理背压并投递到 sink。 |
| `runtime-app` | 0.2.0 baseline | IEC104 over TCP standalone collector 装配层，支持 properties 配置、JDK logging/file/in-memory sink，以及可执行 shaded jar。 |
| `runtime-smoke-tests` | Test-only | 跨模块 smoke test，验证 ingress、runtime-core、protocol binding 可以组合工作，同时避免把这些组合变成 production 依赖。 |

未来可能补充 MQTT、Kafka、HTTP ingress、pipeline、更多 sink 和更完整的可部署
运行时应用。这些依赖都属于 runtime 仓库，不应反向进入 `protocol-sdk`。

## Runtime Core 合同

`runtime-core` 刻意保持轻依赖。它定义 transport adapter 与 protocol binding
之间共享的合同：

- `SourceId`：跨 transport 标识数据源。
- `IngressEnvelope`：携带 source、transport、payload、接收时间和属性进入解析边界。
- `RuntimeParserBinding`：把协议 SDK parser 适配成 runtime parse result。
- `ParsedRecord` 和 `ParseFailure`：表达解析成功和失败输出。
- `RecordSink` 和 `FailureSink`：接收已路由的解析结果。
- `BackpressureStrategy`：解析前返回 `ACCEPT`、`RETRY_LATER` 或 `DROP`。
- `RuntimePipelineRunner`：串联 parser binding、sink、背压和生命周期。

`runtime-core` 不能依赖 Spring、Netty、Kafka、MQTT、HTTP client/server、
数据库驱动、Redis client 或任何可部署 runtime adapter。当前 Netty 依赖只允许
存在于 `runtime-ingress-tcp-netty`。

## TCP/Netty 接入

`runtime-ingress-tcp-netty` 当前提供第一个 TCP ingress baseline：

- `TcpNettyServer` 绑定 host/port，并优雅关闭 Netty event loop group。
- `TcpNettyServerConfig` 支持 loopback、any-address 和测试用端口 `0`。
- `TcpNettyChannelInitializer` 通过 `TcpNettyPipelineRunnerFactory` 为每个
  连接创建一个 `RuntimePipelineRunner`。
- `TcpConnectionSession` 记录连接解析后的 `SourceId`、channel id、session
  id、本地/远端地址、连接时间和稳定 envelope 属性。
- `TcpConnectionRegistry` 跟踪活跃 session，并让 `TcpNettyServer` 在优雅
  shutdown 时关闭活跃客户端连接。
- `TcpNettyIngressHandler` 把入站 `ByteBuf` 拷贝成不可变 payload，并使用
  当前连接 session 属性构造 `IngressEnvelope`。
- `TcpConnectionLifecycleEvent` 通过 Netty pipeline 发布 active、inactive 和
  exception 事件。
- `RETRY_LATER` 背压会暂停 Netty `autoRead`；`DROP` 会发布
  `TcpNettyBackpressureEvent`，但不会暂停 channel。

这仍然是 baseline。它暂不包含 reconnect、协议专用 server builder、TLS、
IEC104 heartbeat 或 durable retry queue。

## 最小 IEC104 TCP Runtime 示例

`0.1.0` 的 API 仍然是低层、显式的。应用侧自己负责 sink、生命周期和部署装配：

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

这会启动一个 Netty TCP listener，为每个连接创建一个
`RuntimePipelineRunner`，通过 `protocol-iec104:0.7.0` 解码 IEC104 字节，并
把成功帧和失败结果路由到配置的 sink。生产应用还需要在这个 baseline 之外补充
生命周期 owner、日志、持久化、重连策略、TLS 和命令/session 策略。

## Standalone Collector App

`runtime-app` 提供 `0.2.0` 的第一个可运行采集器边界：

```text
TcpNettyServer
  -> RuntimePipelineRunner
  -> Iec104RuntimeBinding
  -> configured RecordSink / FailureSink
```

构建可执行 jar：

```bash
mvn -q -pl runtime-app -am package
```

使用示例 properties 文件启动：

```bash
java -jar runtime-app/target/runtime-app-0.2.0-SNAPSHOT-standalone.jar \
  --config examples/collector.properties
```

然后在另一个终端发送一帧 IEC104 单点遥信测试帧：

```bash
java examples/Iec104SendSinglePoint.java 127.0.0.1 2404
```

查看 file sink 输出：

```bash
tail -f target/runtime-records.ndjson
```

也可以直接运行完整本地 smoke：

```bash
sh examples/smoke-standalone.sh
```

如果默认 `java` 低于 JDK 21，运行 smoke 前设置 `JAVA_BIN`：

```bash
JAVA_BIN=/path/to/jdk-21-or-newer/bin/java sh examples/smoke-standalone.sh
```

最小配置项：

```properties
collector.tcp.host=0.0.0.0
collector.tcp.port=2404
collector.source.id=iec104:station-1
collector.backpressure=ACCEPT
collector.sink.type=file
collector.sink.file=target/runtime-records.ndjson
collector.iec104.strictAsduParsing=false
```

当前支持的 sink 类型是 `logging`、`file` 和 `in-memory`。`runtime-app` 只是
很薄的应用装配层；Spring、Kafka、MQTT、HTTP、数据库、Redis 仍然不能进入
`runtime-core` 或 `protocol-sdk`。

### CLI 和配置

`StandaloneCollectorMain` 支持 properties 文件，也支持命令行覆盖：

```bash
java -jar runtime-app/target/runtime-app-0.2.0-SNAPSHOT-standalone.jar \
  --config examples/collector.properties \
  --collector.tcp.port=2405 \
  --collector.sink.type=logging
```

内联 `--key=value` 参数会覆盖前面 `--config` 读取到的默认值，便于本地临时运行。

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `collector.tcp.host` | `0.0.0.0` | TCP 监听地址。本地测试建议使用 `127.0.0.1`。 |
| `collector.tcp.port` | `2404` | TCP 监听端口。端口 `0` 会让系统分配临时端口，适合 smoke test。 |
| `collector.tcp.bossThreads` | `1` | Netty boss event loop 线程数。 |
| `collector.tcp.workerThreads` | `1` | Netty worker event loop 线程数。 |
| `collector.source.id` | `iec104:station-1` | 运行时数据源标识，格式必须是 `namespace:value`。 |
| `collector.backpressure` | `ACCEPT` | 可选 `ACCEPT`、`RETRY_LATER` 或 `DROP`。 |
| `collector.sink.type` | `logging` | 可选 `logging`、`file` 或 `in-memory`。 |
| `collector.sink.file` | 未设置 | 当 `collector.sink.type=file` 时必须配置。 |
| `collector.iec104.strictAsduParsing` | `false` | 是否启用 IEC104 SDK binding 的严格 ASDU 解析。 |

### File Sink 输出格式

file sink 每行输出一条类似 JSON 的记录。成功解析记录包含：

- `kind`: `record`
- `sourceId`
- `protocol`
- `recordType`
- `observedAt`
- `rawPayloadHex`
- `value`
- `attributes`

解析失败使用 `kind=failure`，并包含 `message`、`rawPayloadHex` 和可选 `cause`。

### 常见问题

- `UnsupportedClassVersionError`：请使用 JDK 21 或更高版本运行 standalone
  jar。runtime artifact 使用 Java release 21 编译。
- `Address already in use`：修改 `collector.tcp.port`，或者停止占用该端口的进程。
- 没有文件输出：确认 `collector.sink.type=file`、`collector.sink.file` 已设置，
  并且客户端确实向 collector 端口发送了字节。
- 示例帧没有解析记录：确认 `collector.backpressure=ACCEPT`；`RETRY_LATER` 会按
  设计阻止解析。
- `collector.source.id must use namespace:value format`：使用类似
  `iec104:station-1` 的值。

## Smoke Tests

`runtime-smoke-tests` 只做跨模块验证。当前 IEC104 TCP smoke test 路径如下：

```text
EmbeddedChannel or real localhost Socket
  -> TcpNettyServer / TcpNettyIngressHandler
  -> RuntimePipelineRunner
  -> Iec104RuntimeBinding
  -> RecordSink / FailureSink
```

它覆盖完整 IEC104 frame、TCP 半包、背压阻止解析、异常帧进入 failure sink、
真实 TCP socket server bootstrap，以及客户端断开后停止每连接 runner。

## 依赖方向

允许：

```text
protocol-runtime -> protocol-sdk
```

禁止：

```text
protocol-sdk -> protocol-runtime
protocol-sdk -> Spring or Netty
protocol-sdk -> MQTT or Kafka clients
protocol-sdk -> HTTP server/client frameworks
protocol-sdk -> database or Redis clients
runtime-core -> Netty
runtime-core -> protocol-specific runtime bindings
```

## 构建

需要 JDK 21。

```bash
mvn -q verify
```

## SDK 版本

bootstrap runtime 当前消费已发布的 SDK `0.7.0` artifacts：

- `io.github.qbsstg:protocol-core:0.7.0`
- `io.github.qbsstg:protocol-iec104:0.7.0`

后续可以在新的 SDK 版本发布并验证后升级。

## 发布文档

- [`docs/module-plan.md`](docs/module-plan.md)
- [`docs/module-boundaries.md`](docs/module-boundaries.md)
- [`docs/roadmap-0.2.0.md`](docs/roadmap-0.2.0.md)
- [`docs/release.md`](docs/release.md)
- [`docs/release-readiness-0.1.0.md`](docs/release-readiness-0.1.0.md)
- [`docs/release-notes-0.1.0.md`](docs/release-notes-0.1.0.md)
- [`docs/release-notes-0.2.0.md`](docs/release-notes-0.2.0.md)
