# Runtime App 健康状态和就绪状态说明

本文说明 standalone collector 输出的本地 `runtime-app` 状态行。这个能力只属于
app 边界：`0.11.0` 管理端会把同一套 health/readiness/status 证据以 JSON
暴露出来，但不会把应用框架、metrics exporter、数据库、broker 依赖或管理端
职责加入 `runtime-core`。

`StandaloneCollectorMain` 会在启动成功后输出一次状态行，并在 shutdown 时再输出
一次。查日志时先找这一行前缀：

```text
Protocol Runtime collector status
```

当 `collector.management.enabled=true` 时，standalone collector 还会通过配置的
管理端路径暴露 app-local snapshot：

```text
/health
/readiness
/status
```

最重要的字段如下：

| 字段 | 含义 |
| --- | --- |
| `state` | collector 生命周期状态：`CONFIGURED`、`STARTING`、`RUNNING`、`STOPPING`、`STOPPED` 或 `FAILED`。 |
| `health` | app 派生健康状态：`HEALTHY`、`DEGRADED`、`FAILED`，以及和生命周期一致的非运行态。 |
| `readiness` | 当前是否可以接收 ingress：`READY` 或 `NOT_READY`。 |
| `healthReasons` | 健康时为空；否则说明为什么降级或为什么不可接收 ingress。 |
| `tcpListeners`、`httpListeners`、`kafkaConsumers`、`mqttClients` | 已配置的 ingress 状态，包括 running 标记和实际 bind 地址。 |
| `parsedRecords`、`parseFailures`、`backpressureRetryLater`、`backpressureDrop`、`sinkFailures` | 运行时计数器，用于判断数据质量、背压和 sink 行为。 |
| `fileSink` | file sink 输出路径、open 状态、当前文件字节数、历史文件数、轮转次数和轮转限制。 |

## 状态矩阵

| 生命周期和证据 | Health | Readiness | 常见 reason |
| --- | --- | --- | --- |
| 已创建但未启动 | `CONFIGURED` | `NOT_READY` | `lifecycle=CONFIGURED` |
| 正在启动 | `STARTING` | `NOT_READY` | `lifecycle=STARTING` |
| 运行中，listener 正常，配置的 file sink 已 open，且没有压力计数 | `HEALTHY` | `READY` | `[]` |
| 运行中且仍可接收 ingress，但存在解析失败、sink 失败或背压计数 | `DEGRADED` | `READY` | `parseFailures=N`、`sinkFailures=N`、`backpressureRetryLater=N`、`backpressureDrop=N` |
| 运行中，但没有可用 listener、某个 listener 未运行，或配置的 file sink 未 open | `DEGRADED` | `NOT_READY` | `listeners=0`、`listenerNotRunning=...`、`fileSink=missing`、`fileSinkNotOpen=...` |
| 启动失败，或 collector 记录了致命异常 | `FAILED` | `NOT_READY` | `startupFailure=...`、`lastException=...` |
| 正在停止或已经停止 | `STOPPING` / `STOPPED` | `NOT_READY` | `lifecycle=STOPPING`、`lifecycle=STOPPED` |

## 状态行示例

下面示例只保留运维排查时最先需要看的字段。

健康的 TCP collector：

```text
Protocol Runtime collector status state=RUNNING health=HEALTHY readiness=READY healthReasons=[] listeners=1 parsedRecords=12 parseFailures=0 sinkFailures=0 sink=in-memory tcpListeners=[default@127.0.0.1:2404->127.0.0.1:2404/running=true/active=1/protocol=iec104]
```

协议 payload 异常导致降级，但仍可接收 ingress：

```text
Protocol Runtime collector status state=RUNNING health=DEGRADED readiness=READY healthReasons=[parseFailures=3] listeners=1 parsedRecords=42 parseFailures=3 sinkFailures=0 backpressureRetryLater=0 backpressureDrop=0
```

背压决策导致降级，但仍可接收 ingress：

```text
Protocol Runtime collector status state=RUNNING health=DEGRADED readiness=READY healthReasons=[backpressureRetryLater=5] listeners=1 parsedRecords=100 backpressureRetryLater=5 backpressure=RETRY_LATER
```

配置了 file sink 但文件未 open，因此不可接收 ingress：

```text
Protocol Runtime collector status state=RUNNING health=DEGRADED readiness=NOT_READY healthReasons=[fileSinkNotOpen=target/runtime-records.ndjson] listeners=1 sink=file fileSink=target/runtime-records.ndjson/open=false/activeBytes=4096/history=1/rotations=1/maxBytes=1048576/maxHistory=3
```

启动失败，常见原因是端口冲突：

```text
Protocol Runtime collector status state=FAILED health=FAILED readiness=NOT_READY healthReasons=[startupFailure=java.net.BindException:Address already in use] listeners=0 activeConnections=0
```

## 排查顺序

1. 先看 `readiness`。`NOT_READY` 表示当前不应该继续向这个 collector 发送 ingress。
2. 再看 `healthReasons`。这些值会先指出阻塞或降级的子系统。
3. 如果 reason 以 `listenerNotRunning` 开头，查看对应的 `tcpListeners`、
   `httpListeners`、`kafkaConsumers` 或 `mqttClients`。
4. 如果 reason 以 `fileSink` 开头，检查文件权限、父目录、磁盘空间，以及重启后
   collector 是否能重新打开输出路径。
5. 如果 `parseFailures` 增长但 readiness 仍是 `READY`，优先隔离产生异常
   payload 的客户端或 source。解析失败会进入 failure sink，不会停止 collector。
6. 如果 `sinkFailures` 增长，检查 `lastSinkFailure`、sink 日志、磁盘状态和
   sink failure backpressure 配置。
7. 如果 `backpressureRetryLater` 或 `backpressureDrop` 增长，检查
   `backpressure`、`maxPayloadBytes`、`oversizedPayloadDecision`、
   `sinkFailureThreshold` 和 `sinkFailureDecision`。

## Reason 对照表

| Reason | 含义 | 优先检查 |
| --- | --- | --- |
| `lifecycle=CONFIGURED` | collector 已创建但尚未启动。 | 启动 collector 或检查启动编排。 |
| `lifecycle=STARTING` | 正在启动。 | 等待片刻后重新确认是否进入 `RUNNING` 或 `FAILED`。 |
| `lifecycle=STOPPING` | 正在停止。 | 确认进程是否按预期关闭。 |
| `lifecycle=STOPPED` | collector 已停止。 | 如果非预期停止，再执行重启。 |
| `listeners=0` | snapshot 中没有可用 ingress source。 | 检查 source/listener 配置。 |
| `listenerNotRunning=tcp:<name>` | TCP listener 存在但未运行。 | 检查 bind host、端口、Netty server 启动和端口冲突。 |
| `listenerNotRunning=http:<name>` | HTTP listener 存在但未运行。 | 检查 bind host、端口、path 和 JDK HTTP server 启动。 |
| `listenerNotRunning=kafka:<name>` | Kafka consumer 存在但未运行。 | 检查 bootstrap servers、topic、group id、凭据和 consumer 生命周期日志。 |
| `listenerNotRunning=mqtt:<name>` | MQTT client 存在但未运行。 | 检查 broker URI、client id、topic、凭据和 client 生命周期日志。 |
| `fileSink=missing` | sink 类型是 `file`，但没有 file sink 状态。 | 检查 app sink 装配和配置。 |
| `fileSinkNotOpen=<path>` | file sink 存在但处于关闭状态。 | 检查路径权限、磁盘空间、父目录和之前的 sink 异常。 |
| `parseFailures=N` | 协议解析器拒绝了 `N` 个 payload。 | 检查 failure sink 和异常 source id。 |
| `sinkFailures=N` | record 或 failure sink 抛出了 `N` 次异常。 | 检查 `lastSinkFailure` 和 sink 相关日志。 |
| `backpressureRetryLater=N` | runtime 要求 ingress 稍后重试 `N` 次。 | 检查 payload 大小、sink failure 阈值和压力策略。 |
| `backpressureDrop=N` | runtime 按配置丢弃了 `N` 个 payload。 | 确认该 source 是否有意使用 drop 策略。 |
| `startupFailure=...` | 启动失败，rollback 后保留了失败原因。 | 查看异常消息，常见是 bind 失败或外部端点无效。 |
| `lastException=...` | collector 生命周期记录了致命异常。 | 根据异常类名和时间点查看 app 日志。 |

## 边界

这套状态模型只属于 `runtime-app`。`0.11.0` 管理端在 app 边界内使用 JDK
`HttpServer`，并且和 `runtime-ingress-http` 分离；后者继续只负责协议 payload
的 HTTP 采集接入。未来可以在专门的 app 或 adapter 模块里实现 metrics
exporter、dashboard、数据库、Redis-backed health history 或 broker-publishing
集成，但这些依赖不能进入 `runtime-core` 或 `runtime-protocol-*`。
