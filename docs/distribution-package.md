# Protocol Runtime Distribution Package

`0.14.0` adds an operator-facing distribution package for the standalone
collector. The package is assembled by `runtime-app` build configuration and
does not move packaging, service management, filesystem layout, or Java
discovery concerns into `runtime-core` or protocol parser modules.

## Build

```sh
mvn -q -pl runtime-app -am package
```

The build attaches both artifacts:

- `runtime-app-<version>-distribution.zip`
- `runtime-app-<version>-distribution.tar.gz`

Both packages contain the same top-level layout:

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

## Install

Unpack the archive into an operator-owned directory:

```sh
mkdir -p /opt/protocol-runtime
tar -xzf runtime-app-0.14.0-distribution.tar.gz -C /opt
cd /opt/protocol-runtime-0.14.0
```

For a stable path, use a symlink owned by your deployment process:

```sh
ln -sfn /opt/protocol-runtime-0.14.0 /opt/protocol-runtime
cd /opt/protocol-runtime
```

The package is not a service installer. `systemd` and `launchd` templates are
included as operator-owned examples under `examples/`.

## Java Runtime

The collector requires JDK 21 or newer. The packaged script checks Java before
running commands that execute the app:

```sh
bin/protocol-runtime java-check
```

Set one of these when the system default `java` is too old:

```sh
JAVA_HOME=/path/to/jdk-21 bin/protocol-runtime java-check
JAVA_BIN=/path/to/jdk-21/bin/java bin/protocol-runtime java-check
```

## Configuration

The default package config is `conf/collector.properties`. It keeps management
bound to `127.0.0.1`, writes records to `data/records.ndjson`, and exports
status to `run/status.json`.

The production profile override is `conf/collector-production.properties`:

```sh
PROFILE=production bin/protocol-runtime validate
```

Review these values before production use:

- `collector.tcp.host`
- `collector.tcp.port`
- `collector.source.id`
- `collector.sink.file`
- `collector.management.access`
- `collector.management.token`

## Commands

Validate without binding ports:

```sh
bin/protocol-runtime validate
```

Run startup dry-run and export a configured status snapshot:

```sh
bin/protocol-runtime dry-run
bin/protocol-runtime status
```

Start in the foreground:

```sh
bin/protocol-runtime start
```

Stop by PID file from another terminal:

```sh
bin/protocol-runtime stop
```

`bin/protocol-runtime-stop` is a compatibility shortcut for the same stop path.
Repeated stop against a missing or stale PID file is treated as already
stopped by the application.

## Upgrade

Keep operator-owned state outside the application jar:

- preserve `conf/` unless a release note explicitly requires config changes
- preserve `logs/`, `data/`, `run/`, and `tmp/`
- unpack the new package beside the old package
- review new config templates before copying settings forward
- run `java-check`, `validate`, and `dry-run`
- switch the stable symlink only after validation succeeds
- keep the old package until the new package passes startup smoke

## Smoke

Run the package smoke from the repository checkout:

```sh
JAVA_BIN=/path/to/jdk-21/bin/java sh examples/smoke-distribution-package.sh
```

The smoke builds the package, unpacks the tarball, verifies the zip exists,
runs Java discovery checks, validates config, performs dry-run and status
export, starts the collector, checks management health/readiness/status, sends
an IEC104 test frame, verifies file-sink output, checks duplicate start and TCP
port conflict behavior, and stops cleanly.

## Boundary

Distribution package governance is allowed only in `runtime-app`, build
configuration, examples, docs, or a future dedicated app/distribution module.
It must not add framework, service-manager, filesystem-layout, deployment
wrapper, or packaging dependencies to `runtime-core`, `runtime-protocol-*`, or
`protocol-sdk`.
