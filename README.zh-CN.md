# Protocol Runtime

[English](README.md)

Protocol Runtime 是面向采集平台的 JDK 21 运行时项目，用来承载
[`protocol-sdk`](https://github.com/Qbsstg/protocol-sdk) 提供的 Java 8
兼容协议解析能力。

`protocol-sdk` 只负责协议解析；`protocol-runtime` 负责采集运行时相关的
能力，例如接入、连接会话、解析绑定、背压、批处理和下游投递。

## 当前状态

当前仓库仍处于 bootstrap 阶段。`0.1.0` 已发布第一组运行时合同、IEC104
绑定和 TCP/Netty 接入基线。`0.2.0` 已发布第一个可运行的 standalone IEC104
TCP collector app。`0.3.0` 已发布 runtime-app 生产化加固：配置校验、多
source/listener app 配置、lifecycle/status 快照、状态输出、计数器、file sink
轮转、解析失败隔离和 payload 大小背压策略。`0.4.0` 已发布多协议 runtime
扩展：围绕已发布的 `protocol-sdk:0.7.0` parser artifacts，提供 IEC101、
IEC103 和 Modbus runtime protocol binding，并在 app 层支持协议选择，同时
保留现有 IEC104 app 路径。`0.5.0` 已发布第一个 adapter 边界版本，包含
JDK-only HTTP ingress baseline，以及 HTTP、Kafka、MQTT adapter 设计文档，
同时仍不把 Kafka/MQTT client 依赖引入 runtime。`0.6.0` 已发布 HTTP ingress
生产化路线和 runtime-app HTTP collector 装配。`0.7.0` 已发布 Kafka ingress
baseline 和 runtime-app Kafka collector 装配。`0.8.0` 已发布 MQTT ingress
baseline 和 runtime-app MQTT collector 装配。`0.9.0` 已发布 downstream sink
和运行期生产化加固，包括 app 级 sink 失败隔离、file sink 状态和
sink-failure-triggered backpressure。

当前 release 分支固定为 `0.10.0`，重点是在已发布的 TCP、HTTP、Kafka、MQTT
和 sink-hardening baseline 之后补齐健康检查和运行状态生产化能力。`0.10.0`
roadmap 记录在 [`docs/roadmap-0.10.0.md`](docs/roadmap-0.10.0.md)，release
notes 记录在 [`docs/release-notes-0.10.0.md`](docs/release-notes-0.10.0.md)。

已发布的 `0.9.0` 范围记录在
[`docs/roadmap-0.9.0.md`](docs/roadmap-0.9.0.md)，release notes 记录在
[`docs/release-notes-0.9.0.md`](docs/release-notes-0.9.0.md)，
release-readiness audit 记录在
[`docs/release-readiness-0.9.0.md`](docs/release-readiness-0.9.0.md)。已发布的
`0.8.0` 范围记录在
[`docs/roadmap-0.8.0.md`](docs/roadmap-0.8.0.md)，release notes 记录在
[`docs/release-notes-0.8.0.md`](docs/release-notes-0.8.0.md)，
release-readiness audit 记录在
[`docs/release-readiness-0.8.0.md`](docs/release-readiness-0.8.0.md)。已发布的 `0.7.0`
范围记录在 [`docs/roadmap-0.7.0.md`](docs/roadmap-0.7.0.md)，release notes
记录在 [`docs/release-notes-0.7.0.md`](docs/release-notes-0.7.0.md)，
release-readiness audit 记录在
[`docs/release-readiness-0.7.0.md`](docs/release-readiness-0.7.0.md)。已发布的
`0.6.0` 范围记录在
[`docs/roadmap-0.6.0.md`](docs/roadmap-0.6.0.md)，release notes 记录在
[`docs/release-notes-0.6.0.md`](docs/release-notes-0.6.0.md)。上一个已发布的
`0.5.0` 范围记录在 [`docs/roadmap-0.5.0.md`](docs/roadmap-0.5.0.md)，
release notes 记录在
[`docs/release-notes-0.5.0.md`](docs/release-notes-0.5.0.md)。上一个已发布的
`0.4.0` 范围记录在 [`docs/roadmap-0.4.0.md`](docs/roadmap-0.4.0.md)，
release notes 记录在
[`docs/release-notes-0.4.0.md`](docs/release-notes-0.4.0.md)。

## Maven 坐标

最新运行时发布版本是 `0.9.0`。Runtime 模块是 JDK 21 artifact。应用侧应
按需直接依赖具体模块：

```xml
<dependency>
    <groupId>io.github.qbsstg</groupId>
    <artifactId>runtime-core</artifactId>
    <version>0.9.0</version>
</dependency>
```

```xml
<dependency>
    <groupId>io.github.qbsstg</groupId>
    <artifactId>runtime-protocol-iec104</artifactId>
    <version>0.9.0</version>
</dependency>
```

```xml
<dependency>
    <groupId>io.github.qbsstg</groupId>
    <artifactId>runtime-ingress-tcp-netty</artifactId>
    <version>0.9.0</version>
</dependency>
```

```xml
<dependency>
    <groupId>io.github.qbsstg</groupId>
    <artifactId>runtime-ingress-http</artifactId>
    <version>0.9.0</version>
</dependency>
```

```xml
<dependency>
    <groupId>io.github.qbsstg</groupId>
    <artifactId>runtime-ingress-kafka</artifactId>
    <version>0.9.0</version>
</dependency>
```

```xml
<dependency>
    <groupId>io.github.qbsstg</groupId>
    <artifactId>runtime-app</artifactId>
    <version>0.9.0</version>
</dependency>
```

`runtime-smoke-tests` 是仓库内部的测试模块。即使历史版本在 Maven Central
可见，也不建议作为应用依赖使用。

## 模块规划

| 模块 | 状态 | 职责 |
| --- | --- | --- |
| `runtime-core` | Bootstrap | 运行时无关合同：数据源标识、接入 envelope、解析绑定、解析结果、记录/失败 sink、背压、pipeline runner、生命周期边界。 |
| `runtime-protocol-iec104` | Bootstrap | 基于 `io.github.qbsstg:protocol-iec104:0.7.0` 的第一个运行时协议绑定。 |
| `runtime-protocol-iec101` | 0.4.0 baseline | 基于 `io.github.qbsstg:protocol-iec101:0.7.0` 的 runtime binding，支持按 source 缓冲 stream decoder 和失败路由。 |
| `runtime-protocol-iec103` | 0.4.0 baseline | 基于 `io.github.qbsstg:protocol-iec103:0.7.0` 的 runtime binding，支持按 source 缓冲 stream decoder 和失败路由。 |
| `runtime-protocol-modbus` | 0.4.0 baseline | 基于 `io.github.qbsstg:protocol-modbus:0.7.0` 的 runtime binding，支持 TCP stream 和 datagram parser 模式。 |
| `runtime-ingress-tcp-netty` | Baseline | 最小 TCP/Netty 接入处理器和 server bootstrap：监听 TCP 端口、为每个连接创建一个 `RuntimePipelineRunner`、把 `ByteBuf` 转为 `IngressEnvelope`、处理背压并投递到 sink。 |
| `runtime-ingress-http` | 0.6.0 baseline | 基于 JDK `HttpServer` 的 HTTP ingress：把 POST body 映射为 `IngressEnvelope`，支持 configured/header/path 三种 `SourceId` 来源、请求大小限制和按背压结果返回 HTTP 响应。 |
| `runtime-ingress-kafka` | 0.7.0 baseline | 基于 Kafka client 的 ingress adapter，把 `ConsumerRecord<byte[], byte[]>` payload 和 Kafka metadata 映射为 runtime envelope，同时保持 Kafka 依赖不进入 `runtime-core`。 |
| `runtime-ingress-mqtt` | 0.8.0 baseline | 基于 Paho MQTT 的 ingress adapter，把 MQTT payload 和 message metadata 映射为 runtime envelope，同时保持 MQTT 依赖不进入 `runtime-core`。 |
| `runtime-app` | 0.9.0 baseline | Standalone collector 装配层，支持 properties 配置、app 级协议选择、TCP/HTTP/Kafka/MQTT 装配、JDK logging/file/in-memory sink、sink 失败隔离、file sink 状态、sink-failure-triggered backpressure，以及可执行 shaded jar。默认 IEC104 配置路径保持兼容。 |
| `runtime-smoke-tests` | Test-only | 跨模块 smoke test，验证 ingress、runtime-core、protocol binding 可以组合工作，同时避免把这些组合变成 production 依赖。 |

未来可能补充 pipeline、更多 sink 和更完整的可部署运行时应用。这些依赖都属于
runtime 仓库，不应反向进入 `protocol-sdk`。

## `0.10.0` 健康检查与状态生产化规划

`0.10.0` 在 `0.9.0` 之后打开下一条生产化路线：

- `runtime-core` 继续不引入 Spring、Netty、Kafka、MQTT、HTTP、数据库、Redis
  和 observability exporter 依赖。
- 明确 runtime-app 的 health/readiness 状态，覆盖 configured、starting、
  running、degraded、failed 和 stopped collector。
- 运行状态输出需要区分 listener health、source health、sink health、parse
  failure 压力和 backpressure 状态。
- 管理 HTTP、metrics exporter 或外部监控依赖只能进入 dedicated app/adapter
  模块，不能进入 `runtime-core`。
- 保持已发布的 TCP、HTTP、Kafka、MQTT 和 sink-hardening 行为，同时补齐这些
  路径的健康状态证据。

详细规划维护在 [`docs/roadmap-0.10.0.md`](docs/roadmap-0.10.0.md)。

## `0.9.0` Sink 与运行期生产化发布

`0.9.0` 已发布并固化 `0.8.0` 之后打开的生产化路线：

- `runtime-core` 继续不引入 Spring、Netty、Kafka、MQTT、HTTP、数据库、Redis
  和 observability exporter 依赖。
- downstream sink 边界放在 ingress adapter 之外，优先加固 file/logging
  交付，再扩展 broker 或存储类 sink。
- 增强失败隔离、sink error routing、retry/backpressure 决策，但不把 adapter
  策略移动到 parser binding。
- 补充 TCP、HTTP、Kafka 和 MQTT collector 的运行示例。
- 后续 Kafka/MQTT/HTTP sink 或管理端依赖只能进入 dedicated adapter/app
  模块，不能进入 `runtime-core` 或 `protocol-sdk`。

详细规划维护在 [`docs/roadmap-0.9.0.md`](docs/roadmap-0.9.0.md)。

## `0.8.0` MQTT Ingress 发布

`0.8.0` 已发布第一条 MQTT ingress 实现线：

- `runtime-core` 继续不引入 MQTT、Kafka、HTTP、Spring、数据库、Redis 和
  observability exporter 依赖。
- `runtime-ingress-mqtt` 负责 MQTT client 依赖、topic/source 映射、
  payload-to-envelope 映射、QoS ack 策略、retained message 处理、duplicate
  delivery 策略、reconnect/session 归属和背压结果映射。
- MQTT topic、QoS、retained 标记、duplicate 标记、packet id、source id mode
  和选定协议应继续作为 envelope attributes。
- `runtime-protocol-*` 继续只解析协议 payload，不引入 MQTT 依赖。
- `runtime-app` 负责 MQTT client 配置和 standalone collector 装配，同时保持
  MQTT API 不进入 `runtime-core`。
- [`examples/collector-mqtt.properties`](examples/collector-mqtt.properties)
  提供最小 IEC104 over MQTT collector 配置示例。

详细规划维护在 [`docs/roadmap-0.8.0.md`](docs/roadmap-0.8.0.md)。

## `0.7.0` Kafka Ingress 发布

`0.7.0` 已发布第一条 Kafka ingress 实现线：

- `runtime-core` 继续不引入 Kafka、MQTT、HTTP、Spring、数据库、Redis 和
  observability exporter 依赖。
- `runtime-ingress-kafka` 负责 Kafka client 依赖、source 映射、
  record-to-envelope 映射、背压结果映射和 commit mode 决策。
- Kafka topic、partition、offset、timestamp、key、headers、source id mode
  和选定协议继续作为 envelope attributes。
- `runtime-protocol-*` 继续只解析协议 payload，不引入 Kafka 依赖。
- `runtime-app` 负责 Kafka consumer 配置和 standalone collector 装配，同时
  保持 Kafka API 不进入 `runtime-core`。
- [`examples/collector-kafka.properties`](examples/collector-kafka.properties)
  提供最小 IEC104 over Kafka collector 配置示例。

详细规划维护在 [`docs/roadmap-0.7.0.md`](docs/roadmap-0.7.0.md)。

## `0.6.0` HTTP Runtime-App 发布

`0.6.0` 已发布 HTTP 生产化路线。在保留当前 TCP collector 路径的前提下，
JDK-only HTTP ingress 已可以从 standalone runtime app 中使用：

- `runtime-core` 继续不引入 HTTP、Kafka、MQTT、Spring、数据库、Redis 和
  observability exporter 依赖。
- `runtime-ingress-http` 负责 HTTP 请求处理、source 映射、响应策略、请求大小
  限制、生命周期和 adapter 自身测试。
- `runtime-app` 负责 HTTP listener 配置和应用装配。
- `runtime-protocol-*` 继续只负责协议 payload 解析，不引入 transport 或 app
  依赖。
- Kafka 和 MQTT 在 dedicated implementation module 打开前继续保持 design-only。

详细规划维护在 [`docs/roadmap-0.6.0.md`](docs/roadmap-0.6.0.md)。

## `0.5.0` Adapter 边界规划

`0.5.0` 打开 adapter 生产化路线。第一目标是先定义 HTTP、Kafka、
MQTT 接入如何围绕现有 runtime 合同工作，同时避免污染 core：

- `runtime-core` 继续不引入 HTTP、Kafka、MQTT、Spring、数据库、Redis 和
  observability exporter 依赖。
- HTTP 请求大小限制、响应策略、payload/source 映射已经先落在 JDK-only
  `runtime-ingress-http` baseline 中。
- Kafka topic/partition/offset 属性、commit 时机和 replay 策略已经先为未来的
  `runtime-ingress-kafka` 模块完成设计文档。
- MQTT topic/source 映射、QoS 策略、retained message 处理和 reconnect/session
  归属已经先为未来的 `runtime-ingress-mqtt` 模块完成设计文档。
- Kafka sink 等下游投递 adapter 与 ingress adapter 分开。
- `runtime-app` 继续作为可部署装配边界。

详细规划维护在 [`docs/roadmap-0.5.0.md`](docs/roadmap-0.5.0.md)。

## `0.4.0` 多协议 Runtime 发布

`0.4.0` 已经在不改变依赖方向的前提下，把 runtime 从 IEC104-only app
baseline 推进到多协议 collector runtime：

- Maven reactor 已发布为 `0.4.0`。
- 消费已发布的 `protocol-sdk:0.7.0` parser artifacts。
- 以独立 `runtime-protocol-*` 模块提供 IEC101、IEC103 和 Modbus runtime
  binding。
- protocol binding 模块不引入 transport 或 app 依赖。
- 增加 app 级协议选择，同时保留现有 IEC104 `collector.properties` 兼容路径。
- 保持串口、UDP、Kafka、MQTT、HTTP、数据库、Redis 和 observability 依赖不进入
  `runtime-core` 和 `protocol-sdk`。

详细规划维护在 [`docs/roadmap-0.4.0.md`](docs/roadmap-0.4.0.md)。

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

`runtime-app` 提供 `0.2.0` 引入的可运行采集器边界。当前
`0.10.0` release 线可以把 TCP/Netty、JDK HTTP、Kafka 或 MQTT ingress
接到同一个 app-owned pipeline：

```text
TcpNettyServer, HttpIngressServer, KafkaRecordSource, or MqttMessageSource
  -> RuntimePipelineRunner
  -> selected RuntimeParserBinding
  -> configured RecordSink / FailureSink
```

构建可执行 jar：

```bash
mvn -q -pl runtime-app -am package
```

使用示例 properties 文件启动：

```bash
java -jar runtime-app/target/runtime-app-0.10.0-standalone.jar \
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

HTTP collector smoke 会启动 HTTP-only app，通过 `curl` POST 一段 IEC104 原始
APDU，并验证 file sink 输出：

```bash
sh examples/smoke-standalone-http.sh
```

MQTT app 装配复用同一条 runtime pipeline。示例配置默认连接
`tcp://localhost:1883` 的 broker：

```bash
java -jar runtime-app/target/runtime-app-0.10.0-standalone.jar \
  --config examples/collector-mqtt.properties
```

如果默认 `java` 低于 JDK 21，运行 smoke 前设置 `JAVA_BIN`：

```bash
JAVA_BIN=/path/to/jdk-21-or-newer/bin/java sh examples/smoke-standalone.sh
JAVA_BIN=/path/to/jdk-21-or-newer/bin/java sh examples/smoke-standalone-http.sh
```

最小配置项：

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
collector.iec104.strictAsduParsing=false
```

当前支持的 sink 类型是 `logging`、`file` 和 `in-memory`。`runtime-app` 只是
很薄的应用装配层；Spring、Kafka、MQTT、HTTP、数据库、Redis 仍然不能进入
`runtime-core` 或 `protocol-sdk`。

### CLI 和配置

`StandaloneCollectorMain` 支持 properties 文件，也支持命令行覆盖：

```bash
java -jar runtime-app/target/runtime-app-0.10.0-standalone.jar \
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
| `collector.http.listeners` | 未设置 | HTTP listener 名称列表。配置该项且不配置 `collector.tcp.listeners` 时，应用只启动 HTTP listener。 |
| `collector.http.listener.<name>.host` | `127.0.0.1` | 指定 HTTP listener 的监听地址。 |
| `collector.http.listener.<name>.port` | 必填 | 指定 HTTP listener 的监听端口。端口 `0` 会让系统分配临时端口，适合测试。 |
| `collector.http.listener.<name>.path` | `/ingress` | HTTP context path。`PATH` source-id 模式要求 path 以 `{sourceId}` 结尾。 |
| `collector.http.listener.<name>.source` | 必填 | 引用的 `collector.sources` 条目；旧单 source 配置下使用 `default`。 |
| `collector.http.listener.<name>.sourceIdMode` | `CONFIGURED` | 可选 `CONFIGURED`、`HEADER` 或 `PATH`。 |
| `collector.http.listener.<name>.sourceIdHeader` | 未设置 | 当 `sourceIdMode=HEADER` 时必填。 |
| `collector.http.listener.<name>.maxPayloadBytes` | `0` | HTTP adapter 在进入 runtime pipeline 前的请求大小限制。`0` 表示不限制。 |
| `collector.http.listener.<name>.responseMode` | `ACK_ON_ACCEPT` | 可选 `ACK_ON_ACCEPT` 或 `NO_BODY`。 |
| `collector.http.listener.<name>.backlog` | `0` | JDK `HttpServer` backlog。 |
| `collector.http.listener.<name>.workerThreads` | `1` | JDK `HttpServer` worker 线程数。 |
| `collector.protocol` | `iec104` | 旧单 source 配置使用的协议 binding。可选 `iec104`、`iec101`、`iec103` 或 `modbus`。 |
| `collector.source.id` | `iec104:station-1` | 运行时数据源标识，格式必须是 `namespace:value`。 |
| `collector.backpressure` | `ACCEPT` | 可选 `ACCEPT`、`RETRY_LATER` 或 `DROP`。 |
| `collector.backpressure.maxPayloadBytes` | `0` | 解析前 payload 大小阈值。`0` 表示关闭该阈值策略。 |
| `collector.backpressure.oversizedPayloadDecision` | `DROP` | payload 超过阈值后的决策，可选 `DROP` 或 `RETRY_LATER`。 |
| `collector.backpressure.sinkFailureThreshold` | `0` | 解析前 sink failure 计数阈值。`0` 表示关闭该阈值策略。 |
| `collector.backpressure.sinkFailureDecision` | `RETRY_LATER` | sink failure 达到阈值后的决策，可选 `RETRY_LATER` 或 `DROP`。 |
| `collector.sink.type` | `logging` | 可选 `logging`、`file` 或 `in-memory`。 |
| `collector.sink.file` | 未设置 | 当 `collector.sink.type=file` 时必须配置。 |
| `collector.sink.file.maxBytes` | `10485760` | 当前 file sink 输出文件超过该字节数前触发轮转。 |
| `collector.sink.file.maxHistory` | `5` | 保留的轮转历史文件数量。 |
| `collector.iec104.strictAsduParsing` | `false` | 是否启用 IEC104 SDK binding 的严格 ASDU 解析。 |

`0.3.0` 引入启动前配置校验和内部多 source、多 listener 配置模型，同时保留
上面的单 source 配置项。应用会在打开网络端口前校验 source id、TCP 端口、
线程数、sink 类型、file sink 路径、重复 source 和重复 listener endpoint。

命名 source 和 listener 使用显式列表：

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

命名 listener 会继承所引用 source 的协议。`iec104`、`iec101` 和 `iec103`
当前通过 TCP byte-stream ingress 做 app 层 baseline；串口 adapter 留到后续阶段。
`modbus` 当前选择 Modbus TCP stream binding；Modbus UDP 留到未来 UDP ingress
adapter。

`0.6.0` 增加 app 层 HTTP listener 装配。只声明 `collector.http.listeners`、
不声明 `collector.tcp.listeners` 时，应用会启动 HTTP-only collector，不会再隐式
打开旧的默认 TCP listener：

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

如果需要动态 source 映射，可以配置 `sourceIdMode=HEADER` 并设置
`sourceIdHeader`，或者配置 `sourceIdMode=PATH` 并让 path 以 `{sourceId}`
结尾。HTTP adapter 会把 runtime 的 `ACCEPT`、`RETRY_LATER`、`DROP` 决策映射为
HTTP 响应；协议 payload 解析失败会进入已配置的 failure sink。

### Lifecycle 和状态快照

`0.3.0` 为 `runtime-app` 增加本地 lifecycle/status snapshot API。
`StandaloneCollector` 初始状态是 `CONFIGURED`，启动时经过 `STARTING` 和
`RUNNING`，停止或失败时记录 `STOPPING`、`STOPPED` 或 `FAILED`。启动失败会在
回滚后保留 `FAILED` 状态，方便查看失败原因。

```java
StandaloneCollector collector = StandaloneCollector.create(appConfig);

CollectorStatusSnapshot configured = collector.statusSnapshot();
collector.start();
CollectorStatusSnapshot running = collector.statusSnapshot();
collector.stop();
CollectorStatusSnapshot stopped = collector.statusSnapshot();
```

`StandaloneCollectorMain` 会在启动成功后输出一行状态快照，并在 shutdown 时再
输出一次。该行以 `Protocol Runtime collector status` 开头，包含 listener、
sink、backpressure 和 counter 摘要，方便直接从本地日志观察运行状态。

状态快照包含：

- lifecycle 状态
- 派生 health 状态和 readiness 状态
- 非 healthy 或非 ready collector 的 health reason
- 启动失败原因和最后一次异常类型/消息
- 启动时间和停止时间
- source 摘要
- TCP 和 HTTP listener 的配置 host/port 与实际 bind host/port
- HTTP listener 的 path、source id mode、response mode、payload limit、backlog
  和 worker thread 摘要
- 每个 TCP listener 以及整体 active connection count
- parsed record 和 parse failure 计数
- 最后一次 parse failure 的 source id、消息、发生时间、cause 类型、payload
  大小、payload hex 预览和 TCP/session 属性
- backpressure retry/drop 计数和最后一次 backpressure 决策详情
- sink failure 计数，以及最后一次 sink failure 的目标、source id、异常类型和消息
- sink 类型、file sink 输出路径/open 状态/当前活跃文件字节数/历史文件数、
  file 轮转策略、backpressure 模式、payload 阈值策略和 strict ASDU 配置
- sink failure backpressure 阈值和决策

`0.10.0` 在 snapshot 之上增加 app-local health/readiness 派生能力。
`CollectorHealthSnapshot` 会报告 `HEALTHY`、`DEGRADED`、`FAILED`、
`CONFIGURED`、`STARTING`、`STOPPING` 或 `STOPPED`，同时报告 `READY` 或
`NOT_READY`。运行中的 collector 只有在至少配置了一个 listener、所有 listener
都处于 running，并且 file sink 已 open 时才是 `READY`。parse failure、sink
failure 和 backpressure 决策会让 health 降级，但如果 collector 仍能接收 ingress，
readiness 可以保持 `READY`。

单行 status 输出现在包含 `health=...`、`readiness=...` 和
`healthReasons=[...]`，方便在没有外部管理端点的情况下，从本地日志区分 healthy、
degraded、failed 和 stopped runtime 状态。

面向运维的状态示例、状态矩阵、reason 对照表和排查顺序记录在
[`docs/status-health-readiness.zh-CN.md`](docs/status-health-readiness.zh-CN.md)。

### File Sink 输出格式

file sink 每行输出一条类似 JSON 的记录。当前输出文件超过
`collector.sink.file.maxBytes` 前会触发轮转，并保留
`collector.sink.file.maxHistory` 个历史文件。比如输出路径是
`target/runtime-records.ndjson` 时，历史文件命名为
`runtime-records.ndjson.1`、`runtime-records.ndjson.2`，依次类推。
collector status 输出会展示当前输出文件路径、file sink 是否 open、当前活跃文件
字节数、保留的历史文件数量、进程内累计轮转次数，以及配置的轮转限制。

成功解析记录包含：

- `kind`: `record`
- `sourceId`
- `protocol`
- `recordType`
- `observedAt`
- `rawPayloadHex`
- `value`
- `attributes`

解析失败使用 `kind=failure`，并包含 `message`、`rawPayloadHex`、TCP/session
`attributes` 和可选 `cause`。当前 app 的 parse failure 策略是 continue：
异常帧会进入配置的 failure sink，但不会停止 collector，也不会阻止后续健康帧解析。

runtime-app 还会在应用装配边界隔离 sink 写入异常。如果 record sink 或 failure
sink 在处理解析结果时抛出异常，该异常会记录到 collector metrics 和 status 输出，
不会反向传播进 `runtime-core` 或 ingress adapter。
如果需要在下游 sink 连续失败后保护入口，运维侧可以配置
`collector.backpressure.sinkFailureThreshold` 和
`collector.backpressure.sinkFailureDecision`，让后续 ingress payload 在解析前返回
`RETRY_LATER` 或 `DROP`。

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

`runtime-smoke-tests` 只做跨模块验证。当前 smoke test 会把 TCP 字节送入：

```text
EmbeddedChannel or real localhost Socket
  -> TcpNettyServer / TcpNettyIngressHandler
  -> RuntimePipelineRunner
  -> selected RuntimeParserBinding
  -> RecordSink / FailureSink
```

IEC104 smoke test 覆盖完整 frame、TCP 半包、背压阻止解析、异常帧进入
failure sink、真实 TCP socket server bootstrap，以及客户端断开后停止每连接
runner。多协议 smoke test 进一步覆盖 IEC101、IEC103 和 Modbus TCP byte-stream
通过相同 ingress 与 runner 边界进入对应 binding 的路径。

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

bootstrap runtime 当前消费已发布的 SDK `0.7.0` artifacts。IEC104、IEC101、
IEC103 和 Modbus runtime binding 已实现：

- `io.github.qbsstg:protocol-core:0.7.0`
- `io.github.qbsstg:protocol-iec104:0.7.0`
- `io.github.qbsstg:protocol-iec101:0.7.0`
- `io.github.qbsstg:protocol-iec103:0.7.0`
- `io.github.qbsstg:protocol-modbus:0.7.0`

后续可以在新的 SDK 版本发布并验证后升级。

## 发布文档

- [`docs/module-plan.md`](docs/module-plan.md)
- [`docs/module-boundaries.md`](docs/module-boundaries.md)
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
- [`docs/release.md`](docs/release.md)
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
