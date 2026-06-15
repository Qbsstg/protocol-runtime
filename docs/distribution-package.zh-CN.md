# Protocol Runtime 运行包分发说明

`0.14.0` 为 standalone collector 增加面向运维交付的运行包。运行包由
`runtime-app` 的构建配置装配，不把打包、服务管理、文件系统布局或 Java 发现逻辑放进
`runtime-core` 或协议解析模块。

## 构建

```sh
mvn -q -pl runtime-app -am package
```

构建后会生成两个运行包：

- `runtime-app-<version>-distribution.zip`
- `runtime-app-<version>-distribution.tar.gz`

两个包的目录结构一致：

```text
protocol-runtime-<version>/
  bin/
    protocol-runtime
    protocol-runtime-stop
  conf/
    collector.properties
    collector-production.properties
  lib/
    runtime-app-<version>-standalone.jar
  logs/
  data/
  run/
  tmp/
  docs/
  examples/
```

## 安装

把运行包解压到运维自主管理的目录：

```sh
mkdir -p /opt/protocol-runtime
tar -xzf runtime-app-0.14.0-distribution.tar.gz -C /opt
cd /opt/protocol-runtime-0.14.0
```

如果需要固定路径，可以由部署流程维护软链接：

```sh
ln -sfn /opt/protocol-runtime-0.14.0 /opt/protocol-runtime
cd /opt/protocol-runtime
```

运行包不会自动安装系统服务。`systemd` 和 `launchd` 模板放在 `examples/`，由运维按实际
环境复制和调整。

## Java 运行时

collector 要求 JDK 21 或更高版本。包内脚本会在执行应用命令前检查 Java：

```sh
bin/protocol-runtime java-check
```

如果系统默认 `java` 版本过低，显式设置其中一个变量：

```sh
JAVA_HOME=/path/to/jdk-21 bin/protocol-runtime java-check
JAVA_BIN=/path/to/jdk-21/bin/java bin/protocol-runtime java-check
```

## 配置

默认配置是 `conf/collector.properties`。它默认把 management 绑定到 `127.0.0.1`，
把采集记录写入 `data/records.ndjson`，并把状态快照导出到 `run/status.json`。

生产风格 profile 覆盖文件是 `conf/collector-production.properties`：

```sh
PROFILE=production bin/protocol-runtime validate
```

生产使用前至少检查这些字段：

- `collector.tcp.host`
- `collector.tcp.port`
- `collector.source.id`
- `collector.sink.file`
- `collector.management.access`
- `collector.management.token`

## 命令

只校验配置，不绑定端口：

```sh
bin/protocol-runtime validate
```

执行启动前 dry-run，并导出配置态状态快照：

```sh
bin/protocol-runtime dry-run
bin/protocol-runtime status
```

前台启动：

```sh
bin/protocol-runtime start
```

在另一个终端通过 PID 文件停止：

```sh
bin/protocol-runtime stop
```

`bin/protocol-runtime-stop` 是兼容快捷入口。重复 stop、PID 文件缺失或 PID 已失效时，
应用会按已停止处理。

## 升级

运维自有状态不要放进应用 jar：

- 保留 `conf/`，除非 release notes 明确要求调整配置。
- 保留 `logs/`、`data/`、`run/` 和 `tmp/`。
- 新版本解压到旧版本旁边。
- 先对比新配置模板，再迁移已有配置。
- 先执行 `java-check`、`validate` 和 `dry-run`。
- 验证通过后再切换固定软链接。
- 新包启动 smoke 通过前保留旧包。

## Smoke

在仓库 checkout 中运行分发包 smoke：

```sh
JAVA_BIN=/path/to/jdk-21/bin/java sh examples/smoke-distribution-package.sh
```

smoke 会构建运行包、解压 tar 包、确认 zip 存在、验证 Java 发现、校验配置、执行
dry-run 和状态导出、启动 collector、检查 management health/readiness/status、发送
IEC104 测试帧、验证 file sink 输出、覆盖重复启动和 TCP 端口冲突，并完成优雅停止。

## 边界

运行包分发治理只能放在 `runtime-app`、构建配置、examples、docs 或未来 dedicated
app/distribution 模块。它不能给 `runtime-core`、`runtime-protocol-*` 或 `protocol-sdk`
增加框架、服务管理、文件系统布局、部署 wrapper 或打包依赖。
