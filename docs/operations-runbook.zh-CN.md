# Protocol Runtime 运维手册

本文覆盖 `0.16.0` 线新增的 standalone collector 生产运行运维表面。相关能力属于
app-owned 边界：优先使用运行包命令、状态导出、management 快照和日志完成自检、
排障和恢复，不把外部 supervisor 或运维代理引入核心模块。

## 首先收集的证据

在已解压的运行包目录中，用生产相同的 JDK 和配置执行：

```sh
bin/protocol-runtime java-check
bin/protocol-runtime version
bin/protocol-runtime verify-package
bin/protocol-runtime validate
bin/protocol-runtime self-check
bin/protocol-runtime hot-check
bin/protocol-runtime status
```

如果启用了 management，还要收集：

```sh
curl -fsS http://127.0.0.1:8081/health
curl -fsS http://127.0.0.1:8081/readiness
curl -fsS http://127.0.0.1:8081/status
```

如果启用了 token access，请带上对应授权头。不要把 management token 贴到 issue、
工单或日志里。

## Self-Check

`self-check` 不绑定采集端口，也不修改运行中的 collector。它输出 JSON 证据，覆盖：

- 当前 Java runtime 和版本
- 包内元数据、包布局路径和 package integrity 状态
- 配置文件、profile、校验错误和配置 checksum
- runtime 目录可读写状态
- listener 配置和 bind-readiness 姿态
- sink 类型、file sink 路径和轮转配置
- management host、port、path、access mode、token 是否配置、request logging 和
  health history 容量
- backpressure 策略

首次启动前、升级后和生产问题排查时都应执行。

## Hot-Check 但不 Hot-Reload

`hot-check` 会计算配置文件 hash、重新执行配置校验、和本地 baseline 文件比较，并输出
是否需要重启。它不会修改运行中的 collector 配置，也不会热加载。

常见状态：

| 状态 | 含义 |
| --- | --- |
| `BASELINE_CREATED` | baseline 不存在，当前有效配置成为比较基准。 |
| `UNCHANGED` | 当前配置 hash 与 baseline 一致。 |
| `CHANGED_RESTART_REQUIRED` | 配置已变化且校验通过，需要重启后才会生效。 |
| `INVALID_CONFIG` | 当前配置无效；修复校验错误后再考虑重启。 |
| `CONFIG_UNAVAILABLE` | 配置文件无法读取或无法计算 hash。 |

运行包默认 baseline 文件是 `run/config.hotcheck.properties`。可用
`--hot-check-baseline FILE` 或 `HOT_CHECK_BASELINE` 覆盖。

## 故障恢复

| 场景 | 恢复路径 |
| --- | --- |
| stale PID | 先确认 PID 对应进程不存在，必要时保留旧 PID 文件用于审计，然后只删除 stale PID 文件并重启。 |
| 端口冲突 | 执行 `validate`，查看 `self-check` listener 端口，检查本机进程占用，再换端口或停止冲突进程。 |
| sink 路径失败 | 执行 `self-check`，确认 file sink 父目录存在且可写，修复权限或路径后先 dry-run 再重启。 |
| 配置错误 | 执行 `validate` 和 `hot-check`，修复报错 key，重新执行两者，通过后再重启。 |
| management token 错误 | 从 `self-check` 确认 access mode 和 token 是否配置；收集 401/403 状态，但不要记录 token 明文。 |
| 解析失败 | 收集 `status`、management `/status`、file sink 记录和日志，查看 `lastParseFailure` 和 payload preview。 |
| backpressure | 收集 `status`、management `/status` 和日志，查看 backpressure counter、decision 和 sink failure counter。 |
| checksum mismatch | 从同一版本重新下载 artifact 和 checksum，重跑 `verify-package`，通过前不要启动。 |
| 包解压不完整 | 先校验 archive checksum，再解压到空目录，重跑 `verify-package` 和 `self-check`。 |
| 升级中断 | 停止所有相关进程，保留新旧日志，把软链接切回上一稳定包，执行 `version`、`verify-package`、`validate`、`dry-run`、`self-check` 后再启动。 |
| 回滚验证 | 回滚后执行 `java-check`、`version`、`verify-package`、`validate`、`dry-run`、`self-check`、`status`，启用 management 时还要检查 health/readiness。 |

## 生产问题诊断流程

1. 记录 artifact 坐标、包文件名、`package.properties` 和 `bin/protocol-runtime version`。
2. 用 `java-check` 确认 Java。
3. 用 `verify-package` 确认 archive 和已解压包。
4. 执行 `validate`、`dry-run`、`self-check` 和 `hot-check`。
5. 从配置的 status 文件读取或导出 `status`。
6. 启用 management 时查询 health/readiness/status。
7. 收集 `logs/protocol-runtime.log`、配置的 PID 文件，以及启动 wrapper 的 stdout/stderr。
8. 解析或 sink 问题需要收集最近 file sink 记录和 status metrics；未经批准不要外发密钥或完整 payload。
9. 升级问题要保留新旧两个包目录，并先比较配置模板，再修改运维自有配置。

## 验证命令

仓库维护者应保持这些 smoke 通过：

```sh
JAVA_BIN=/path/to/jdk-21/bin/java sh examples/smoke-distribution-package.sh
JAVA_BIN=/path/to/jdk-21/bin/java sh examples/smoke-release-artifact.sh
JAVA_BIN=/path/to/jdk-21/bin/java sh examples/smoke-long-running.sh
JAVA_BIN=/path/to/jdk-21/bin/java sh examples/smoke-release-artifact-regression.sh
```

`RUN_SECONDS` 可以延长 long-running smoke 的运行窗口。默认值保持较短，方便日常开发
和 CI 风格检查。
