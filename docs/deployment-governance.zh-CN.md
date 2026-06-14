# Standalone Collector 部署治理

本文记录 `0.13.0` 为 `runtime-app` 增加的第一轮 app-owned 部署治理能力。
这些能力只面向 shaded standalone collector jar，不改变 `runtime-core`、协议绑定
模块或 `protocol-sdk`。

## CLI 命令

standalone jar 支持原有配置参数，也支持部署治理命令：

```sh
java -jar runtime-app-0.13.0-SNAPSHOT-standalone.jar --validate --config conf/collector.properties
java -jar runtime-app-0.13.0-SNAPSHOT-standalone.jar --dry-run --config conf/collector.properties --status-export run/status.json
java -jar runtime-app-0.13.0-SNAPSHOT-standalone.jar --config conf/collector.properties
java -jar runtime-app-0.13.0-SNAPSHOT-standalone.jar --stop --pid-file run/protocol-runtime.pid
```

- `--validate`：只做配置校验，不创建 collector。
- `--dry-run`：校验配置并构造 collector 模型，但不绑定端口，也不连接 Kafka/MQTT。
- `--status-export <file>`：输出脚本可读取的 JSON 状态快照。
- `--stop --pid-file <file>`：读取 PID 文件并向对应进程发送停止信号。PID 文件缺失或
  指向已退出进程时，按“已经停止”处理。

## Profile 和覆盖顺序

profile 通过 `collector.profile` 或 `--profile <name>` 指定。profile 名称只允许
字母、数字、横线、下划线和点号。

每个显式 `--config` 文件的加载顺序如下：

1. `runtime-app` 内部默认值。
2. 命令行中每个 `--config` 文件，按声明顺序加载。
3. 每个配置文件旁边的可选 profile 文件。
   `conf/collector.properties --profile production` 会在存在时加载
   `conf/collector-production.properties`。
4. 命令行 `--key=value` 覆盖。

这保持旧的单文件 `collector.properties` 兼容，同时允许把 local、test、staging、
production 的差异拆到小的 profile 文件里。

## 运行目录

`collector.runtime.dir` 是部署目录基准。相对的子路径都会按这个目录解析。

```properties
collector.runtime.dir=/opt/protocol-runtime
collector.runtime.confDir=conf
collector.runtime.logsDir=logs
collector.runtime.dataDir=data
collector.runtime.runDir=run
collector.runtime.tmpDir=tmp
collector.runtime.pidFile=run/protocol-runtime.pid
collector.runtime.statusFile=run/status.json
collector.runtime.logFile=logs/protocol-runtime.log
collector.runtime.createDirectories=true
```

只有设置 `collector.runtime.createDirectories=true` 时，app 才会在启动前创建这些
目录。dry-run 不绑定端口、不连接外部系统，但会按要求写出 `--status-export` 文件。

## 日志文件策略

runtime 把运行状态输出到 stdout/stderr，并使用 JDK logging 输出 management request
日志。生产环境建议由 systemd、launchd、容器或脚本把 stdout/stderr 重定向到
`collector.runtime.logFile` 或等价位置。

日志策略：

- 不记录 management token、凭据或 payload bytes。
- 优先使用运维侧轮转，例如 `logrotate`、systemd stdout/stderr 追加文件配合外部轮转、
  或 launchd 日志轮转。
- parsed record 应进入配置的 sink，不进入进程日志。
- 除非已经明确网络边界和 token 策略，否则保持 `collector.management.access=local`。

## PID、停止和服务模板

脚本或服务管理器运行 collector 时，应设置 `collector.runtime.pidFile`。app 在启动成功后
写入当前 JVM PID，并在优雅关闭时删除。

模板：

- [`../examples/protocol-runtime-stop.sh`](../examples/protocol-runtime-stop.sh)
- [`../examples/protocol-runtime.service`](../examples/protocol-runtime.service)
- [`../examples/com.qbsstg.protocol-runtime.plist`](../examples/com.qbsstg.protocol-runtime.plist)

这些文件只是 operator-owned 示例，测试不会安装系统服务。

## 状态导出

`collector.runtime.statusFile` 输出和 management `/status` 相同的 JSON 结构，包含
lifecycle、health、readiness、sources、listeners、sink、backpressure、failure
counters、management 状态、management metrics 和 health history。

它适合本机脚本读取状态，不是外部 observability exporter。

## 故障排查

| 现象 | 检查方向 |
| --- | --- |
| 配置校验失败 | 先运行 `--validate`，逐条修正错误，再运行 `--dry-run`。 |
| 启动时报端口冲突 | 检查 TCP/HTTP listener 端口和 management 端口。端口 `0` 只适合测试。 |
| file sink 路径失败 | 确认 sink 父目录存在，或为运行目录设置 `collector.runtime.createDirectories=true`。 |
| management 返回 401 | 检查 `collector.management.access=token`，并传 `Authorization: Bearer <token>` 或 `X-Management-Token`。 |
| management 返回 403 | `collector.management.access=local` 拒绝了非本机访问。保持本机访问，或在评审后切到 token 模式。 |
| 出现解析失败 | 查看 `/status` 或 status export 中的 `metrics.lastParseFailure` 和 payload preview。 |
| 出现背压 | 查看 `backpressureRetryLaterCount`、`backpressureDropCount` 以及 payload/sink failure 阈值。 |
| 无法停止 | 检查 PID 文件路径、文件 owner、进程用户和 PID 是否已经过期。PID 文件缺失时重复停止成功是预期行为。 |

## 边界规则

部署治理只属于 `runtime-app` 或未来 dedicated app/adapter 模块。`runtime-core` 不能引入
Spring、Netty、Kafka、MQTT、HTTP、数据库、Redis、observability exporter、
service-manager、filesystem-layout、access-control 或 request-logging 依赖。
`protocol-sdk` 继续保持 parser-only。
