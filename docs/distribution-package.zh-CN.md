# Protocol Runtime 运行包分发说明

`0.15.0` 对 standalone collector 运行包做第一轮生产化增强，重点覆盖安装校验、
完整性验证、升级、回滚、离线部署和支持排障。相关能力仍放在 `runtime-app`、构建配置、
examples 和 docs 中，不把打包、checksum/signing、服务管理、文件系统布局或 installer
职责放进 `runtime-core` 或协议解析模块。

## 构建

```sh
mvn -q -pl runtime-app -am package
```

构建后会生成这些 release artifact：

- `runtime-app-<version>-standalone.jar`
- `runtime-app-<version>-distribution.zip`
- `runtime-app-<version>-distribution.tar.gz`
- standalone jar、zip、tar.gz 对应的 `.sha256` 和 `.sha512`

正式发布到 Maven Central 后，Central 也会提供仓库侧 checksum 和 signature sidecar。
包内脚本负责验证 checksum 文件，但不持有 GPG key，也不把 signing 依赖引入运行时核心。

两个运行包的目录结构一致：

```text
protocol-runtime-<version>/
  package.properties
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

`package.properties` 记录 runtime 版本、artifact id/version、standalone jar 路径、包
布局名称、包布局版本和构建 Java 版本。

## 安装

把运行包解压到运维自主管理的目录：

```sh
mkdir -p /opt/protocol-runtime
tar -xzf runtime-app-0.15.0-distribution.tar.gz -C /opt
cd /opt/protocol-runtime-0.15.0
```

如果需要固定路径，可以由部署流程维护软链接：

```sh
ln -sfn /opt/protocol-runtime-0.15.0 /opt/protocol-runtime
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

脚本按 POSIX `sh` 编写。Windows 使用路径由运维自行决定：可以使用 WSL、Git Bash、
Cygwin，也可以直接用 JDK 21 调 standalone jar。

## 版本诊断

排障、确认部署版本或回滚目标时执行：

```sh
bin/protocol-runtime version
```

输出包含：

- `runtime.version`
- `artifact`
- `java.version`
- `java.bin`
- `package.layout`
- `package.layout.version`
- `app.home`
- `standalone.jar`

## 包完整性校验

验证已解压包的基础布局：

```sh
bin/protocol-runtime verify-package
```

使用 checksum sidecar 验证 release archive：

```sh
bin/protocol-runtime verify-package \
  --artifact runtime-app-0.15.0-distribution.tar.gz \
  --checksum runtime-app-0.15.0-distribution.tar.gz.sha256

bin/protocol-runtime verify-package \
  --artifact runtime-app-0.15.0-distribution.zip \
  --checksum runtime-app-0.15.0-distribution.zip.sha512
```

checksum 文件可以是纯 hex，也可以是常见的 `hex filename` 格式。脚本通过
`sha256sum`、`sha512sum`、`shasum` 或 `openssl` 支持 SHA-256 和 SHA-512。

signature 仍属于发布流程策略：正式发布时复用 Maven Central `.asc` 文件，并按组织的
GPG trust 策略验证。不把 checksum/signing 依赖引入 `runtime-core`。

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

## 配置迁移

首次安装后的 `conf/` 应视为运维自有内容。

推荐升级流程：

1. 新版本解压到旧版本旁边。
2. 新包 smoke 通过前，保留旧 `conf/`、`logs/`、`data/`、`run/` 和 `tmp/`。
3. 对比新包里的 `conf/collector.properties` 和
   `conf/collector-production.properties` 模板。
4. 只把明确需要的新 key 或默认值变化合并到运维自有配置。
5. 执行 `java-check`、`version`、`verify-package`、`validate` 和 `dry-run`。
6. 验证成功后再切换固定软链接。

不要直接覆盖生产配置。management token、source id、监听端口、sink 路径、runtime
目录、PID 文件和 profile 覆盖都应保持由运维控制。

## 回滚

用版本目录和软链接让回滚路径清晰：

```text
/opt/protocol-runtime-0.14.0/
/opt/protocol-runtime-0.15.0/
/opt/protocol-runtime -> /opt/protocol-runtime-0.15.0
```

回滚流程：

1. 停止当前 collector，并确认 PID 文件不存在或已失效。
2. 把 `/opt/protocol-runtime` 软链接切回旧版本目录。
3. 按部署流程继续保留运维自有 `conf/`、`logs/`、`data/`、`run/` 和 `tmp/` 策略。
4. 执行 `bin/protocol-runtime java-check`、`version`、`verify-package`、`validate` 和
   `dry-run`。
5. 启动旧包，并检查 `status`、启用时的 management health/readiness、file sink 输出和
   listener bind 日志。

如果回滚发生在启动失败之后，只能在确认进程不存在后清理 stale PID。两个版本的日志都要
保留，便于复盘。

## 离线部署

无法直连 Maven Central 的服务器，需要通过内部批准的 artifact 通道搬运这些文件：

- `runtime-app-<version>-distribution.tar.gz`
- `runtime-app-<version>-distribution.tar.gz.sha256`
- `runtime-app-<version>-distribution.tar.gz.sha512`
- `runtime-app-<version>-distribution.tar.gz.asc`
- `runtime-app-<version>-distribution.zip`
- `runtime-app-<version>-distribution.zip.sha256`
- `runtime-app-<version>-distribution.zip.sha512`
- `runtime-app-<version>-distribution.zip.asc`
- `runtime-app-<version>-standalone.jar` 及其 checksum/signature sidecar
- release notes、运行包文档和已审核配置模板

搬运前后都要验证 checksum。signature 验证按组织的 GPG trust 流程执行。

## Smoke

在仓库 checkout 中运行分发包 smoke：

```sh
JAVA_BIN=/path/to/jdk-21/bin/java sh examples/smoke-distribution-package.sh
```

distribution smoke 会构建运行包、解压 tar 包、确认 zip 存在、检查生成的 checksum
sidecar、验证 Java 发现、version 输出、包布局、checksum 校验、配置校验、dry-run、
状态导出、collector 启动、management health/readiness/status、IEC104 测试帧、file
sink 输出、重复启动、TCP 端口冲突、checksum 缺失、checksum 错误和优雅停止。

本地构建产物或已下载 release artifact 可以运行：

```sh
JAVA_BIN=/path/to/jdk-21/bin/java sh examples/smoke-release-artifact.sh
```

验证已下载 artifact 时显式指定路径：

```sh
DIST_TAR=/path/runtime-app-0.15.0-distribution.tar.gz \
DIST_ZIP=/path/runtime-app-0.15.0-distribution.zip \
JAVA_BIN=/path/to/jdk-21/bin/java \
sh examples/smoke-release-artifact.sh
```

release artifact smoke 会验证 tar/zip checksum sidecar，解包后执行 `java-check`、
`version`、`verify-package`、`validate`、`dry-run`、`start`、`status` 和 `stop`。

## 排障

| 现象 | 检查项 |
| --- | --- |
| checksum mismatch | 从同一个 release/version 重新下载 artifact 和 checksum sidecar，确认传输过程没有改行尾或截断。 |
| signature 缺失 | 从 Maven Central 或离线包补齐 `.asc`；signature 验证属于发布和运维策略。 |
| 解压不完整 | 先重新做 checksum 校验，再解压到空目录，确认 `package.properties`、`bin/`、`conf/`、`lib/`、`logs/`、`data/`、`run/`、`tmp/`、`docs/`、`examples/` 都存在。 |
| Java 版本错误 | 执行 `bin/protocol-runtime java-check`，用 `JAVA_HOME` 或 `JAVA_BIN` 指向 JDK 21+。 |
| 脚本没有执行权限 | 执行 `chmod +x bin/protocol-runtime bin/protocol-runtime-stop`，或用 `sh bin/protocol-runtime ...` 调用。 |
| stale PID | 先确认 PID 对应进程不存在，必要时保留旧 PID 文件用于排查，再只删除失效 PID 文件。 |
| 端口冲突 | 执行 `validate`，检查 listener 端口和本机占用后再启动。 |
| 配置迁移错误 | 用旧运维配置和新模板逐项比较，只合并明确需要的 key。 |
| 离线 artifact 缺失 | 确认离线包包含 zip/tar.gz、standalone jar、checksum、signature、docs 和审核过的配置模板。 |
| 版本不一致 | 对比 `bin/protocol-runtime version`、`package.properties`、artifact 文件名和 Maven Central 坐标。 |

## 边界

运行包分发治理只能放在 `runtime-app`、构建配置、examples、docs 或未来 dedicated
app/distribution 模块。它不能给 `runtime-core`、`runtime-protocol-*` 或 `protocol-sdk`
增加框架、服务管理、文件系统布局、部署 wrapper、installer、checksum/signing 或打包依赖。
