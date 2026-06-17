# Protocol Runtime Distribution Package

`0.15.0` hardened the standalone collector distribution package for operator
install, verification, upgrade, rollback, offline deployment, and support
diagnostics. `0.16.0` adds app-owned runtime operations checks on top of that
package: `self-check`, `hot-check`, long-running smoke, release artifact
regression smoke, and operator recovery runbooks. The work stays in
`runtime-app`, build configuration, examples, and docs. It does not move
packaging, checksum/signing, service management, filesystem layout,
operations-agent, runtime-supervisor, or installer concerns into `runtime-core`
or protocol parser modules.

## Build

```sh
mvn -q -pl runtime-app -am package
```

The build attaches these release artifacts:

- `runtime-app-<version>-standalone.jar`
- `runtime-app-<version>-distribution.zip`
- `runtime-app-<version>-distribution.tar.gz`
- `.sha256` and `.sha512` sidecars for the standalone jar, zip, and tar.gz

Maven Central also publishes repository-managed checksum and signature sidecars
for released artifacts. The package scripts verify checksum files but do not
own GPG keys or signing dependencies.

Both distribution archives contain the same top-level layout:

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

`package.properties` records the runtime version, artifact id/version,
standalone jar path, package layout name, package layout version, and build
Java version.

## Install

Unpack the archive into an operator-owned directory:

```sh
mkdir -p /opt/protocol-runtime
tar -xzf runtime-app-0.15.0-distribution.tar.gz -C /opt
cd /opt/protocol-runtime-0.15.0
```

For a stable path, use a symlink owned by your deployment process:

```sh
ln -sfn /opt/protocol-runtime-0.15.0 /opt/protocol-runtime
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

The scripts are POSIX `sh` scripts. Windows use is operator-owned: use WSL,
Git Bash, Cygwin, or invoke the standalone jar directly with an explicit JDK 21
runtime.

## Version Diagnostics

Use `version` when opening support tickets, comparing deployed packages, or
confirming a rollback target:

```sh
bin/protocol-runtime version
```

The output includes:

- `runtime.version`
- `artifact`
- `java.version`
- `java.bin`
- `package.layout`
- `package.layout.version`
- `app.home`
- `standalone.jar`

## Package Integrity

Verify an unpacked package layout:

```sh
bin/protocol-runtime verify-package
```

Verify a release archive with a checksum sidecar:

```sh
bin/protocol-runtime verify-package \
  --artifact runtime-app-0.15.0-distribution.tar.gz \
  --checksum runtime-app-0.15.0-distribution.tar.gz.sha256

bin/protocol-runtime verify-package \
  --artifact runtime-app-0.15.0-distribution.zip \
  --checksum runtime-app-0.15.0-distribution.zip.sha512
```

The checksum file may contain raw hex or the common `hex filename` format. The
script supports SHA-256 and SHA-512 through `sha256sum`, `sha512sum`, `shasum`,
or `openssl`.

Signature policy remains release-owned: use Maven Central `.asc` artifacts and
your organization key trust policy for signature verification. No signing or
checksum dependency is introduced into `runtime-core`.

## Runtime Operations Checks

Run `self-check` before first start, after upgrades, or during production
triage:

```sh
bin/protocol-runtime self-check
```

The command prints JSON evidence for Java version, package metadata, package
layout, config validation, runtime directory readability/writability, listener
bind readiness, source mappings, sink paths, management configuration,
backpressure policy, and package integrity state. It does not bind listener
ports.

Run `hot-check` when you need to know whether an operator-owned config file
changed while the collector is running:

```sh
bin/protocol-runtime hot-check
```

`hot-check` hashes the configured properties file, re-runs validation, compares
against `run/config.hotcheck.properties`, and reports whether restart is
required. It never hot-reloads the running collector. Override the comparison
file with `--hot-check-baseline FILE` or `HOT_CHECK_BASELINE`.

The operational recovery flow is documented in
[`operations-runbook.md`](operations-runbook.md).

## Configuration

The default package config is `conf/collector.properties`. It keeps management
bound to `127.0.0.1`, writes records to `data/records.ndjson`, writes bounded
failed-record samples to `data/failed-records`, and exports status to
`run/status.json`.

The production profile override is `conf/collector-production.properties`:

```sh
PROFILE=production bin/protocol-runtime validate
```

Review these values before production use:

- `collector.tcp.host`
- `collector.tcp.port`
- `collector.source.id`
- `collector.sink.file`
- `collector.sink.failedRecords.dir`
- `collector.sink.failedRecords.maxSamples`
- `collector.management.access`
- `collector.management.token`

The file sink emits `protocol-runtime.record.v1` JSONL envelopes. If the
configured record or failure sink throws, runtime-app writes
`protocol-runtime.failed-record.v1` samples under
`collector.sink.failedRecords.dir` and exposes delivery failure classification
through `status`, management `/status`, `self-check`, and smoke logs.

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

Run production operations diagnostics:

```sh
bin/protocol-runtime self-check
bin/protocol-runtime hot-check
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

## Configuration Migration

Treat `conf/` as operator-owned after first install.

Recommended upgrade flow:

1. Unpack the new package beside the old package.
2. Keep the old `conf/`, `logs/`, `data/`, `run/`, and `tmp/` directories
   until the new version passes smoke.
3. Compare the new `conf/collector.properties` and
   `conf/collector-production.properties` templates with the deployed config.
4. Copy only intentional new keys or changed defaults into the operator-owned
   config.
5. Run `java-check`, `version`, `verify-package`, `validate`, and `dry-run`.
6. Switch the stable symlink only after validation succeeds.

Do not blindly overwrite production config templates. Keep any local
management token, source id, listener port, sink path, runtime directory, PID
file, and profile overrides under operator control.

## Rollback

Use package directories and symlinks to make rollback predictable:

```text
/opt/protocol-runtime-0.14.0/
/opt/protocol-runtime-0.15.0/
/opt/protocol-runtime -> /opt/protocol-runtime-0.15.0
```

Rollback flow:

1. Stop the current collector and confirm the PID file is gone or stale.
2. Switch `/opt/protocol-runtime` back to the previous package directory.
3. Reuse the preserved operator-owned `conf/`, `logs/`, `data/`, `run/`, and
   `tmp/` policy from your deployment process.
4. Run `bin/protocol-runtime java-check`, `version`, `verify-package`,
   `validate`, and `dry-run`.
5. Start the old package and verify `status`, management health/readiness when
   enabled, file sink output, and listener bind logs.

If rollback follows a failed start, clear only stale PID files after confirming
the process is no longer running. Preserve logs from both package versions.

## Offline Deployment

For servers without Maven Central access, download and move these files through
your approved artifact channel:

- `runtime-app-<version>-distribution.tar.gz`
- `runtime-app-<version>-distribution.tar.gz.sha256`
- `runtime-app-<version>-distribution.tar.gz.sha512`
- `runtime-app-<version>-distribution.tar.gz.asc`
- `runtime-app-<version>-distribution.zip`
- `runtime-app-<version>-distribution.zip.sha256`
- `runtime-app-<version>-distribution.zip.sha512`
- `runtime-app-<version>-distribution.zip.asc`
- `runtime-app-<version>-standalone.jar` and its checksum/signature sidecars
- release notes, distribution package docs, and approved config templates

Verify checksums before and after transfer. Verify signatures according to your
organization's GPG trust process before installing on restricted servers.

## Smoke

Run the package smoke from the repository checkout:

```sh
JAVA_BIN=/path/to/jdk-21/bin/java sh examples/smoke-distribution-package.sh
```

The distribution smoke builds the package, unpacks the tarball, verifies the
zip exists, checks generated checksum sidecars, runs Java discovery, version,
layout verification, checksum verification, config validation, `self-check`,
`hot-check`, dry-run, status export, collector startup, management
health/readiness/status, IEC104 test frame ingestion, file-sink output,
duplicate start, TCP port conflict, missing checksum, bad checksum, and clean
stop behavior.

Run the release artifact smoke for local build outputs or downloaded release
artifacts:

```sh
JAVA_BIN=/path/to/jdk-21/bin/java sh examples/smoke-release-artifact.sh
```

To validate downloaded artifacts instead of local build output:

```sh
DIST_TAR=/path/runtime-app-0.15.0-distribution.tar.gz \
DIST_ZIP=/path/runtime-app-0.15.0-distribution.zip \
JAVA_BIN=/path/to/jdk-21/bin/java \
sh examples/smoke-release-artifact.sh
```

The release artifact smoke verifies tar/zip checksum sidecars, unpacks the
package, and runs `java-check`, `version`, `verify-package`, `validate`,
`self-check`, `hot-check`, `dry-run`, `start`, `status`, and `stop`.

Run long-running package smoke with a short default window:

```sh
JAVA_BIN=/path/to/jdk-21/bin/java sh examples/smoke-long-running.sh
```

Use `RUN_SECONDS=300` or a larger value for manual soak checks.

Run release artifact regression smoke for standalone jar plus distribution
artifacts:

```sh
JAVA_BIN=/path/to/jdk-21/bin/java sh examples/smoke-release-artifact-regression.sh
```

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Checksum mismatch | Re-download the artifact and checksum sidecar from the same release/version; confirm text transfer did not alter line endings. |
| Signature missing | Fetch the `.asc` sidecar from Maven Central or the approved offline bundle; signature verification is release-owned. |
| Package extraction incomplete | Re-run checksum verification, then unpack into an empty directory and confirm `package.properties`, `bin/`, `conf/`, `lib/`, `logs/`, `data/`, `run/`, `tmp/`, `docs/`, and `examples/` exist. |
| Wrong Java version | Run `bin/protocol-runtime java-check`; set `JAVA_HOME` or `JAVA_BIN` to JDK 21+. |
| Script permission denied | Restore executable bits with `chmod +x bin/protocol-runtime bin/protocol-runtime-stop`, or invoke with `sh bin/protocol-runtime ...`. |
| Stale PID | Confirm the PID is not running, preserve the old PID file for investigation if needed, then remove only the stale file. |
| Port conflict | Run `validate`, inspect listener ports, and check local processes before restarting. |
| Config migration error | Compare old operator config against the new template and copy only intentional keys. |
| Offline artifact missing | Verify the offline bundle includes zip/tar.gz, standalone jar, checksum, signature, docs, and approved config templates. |
| Version mismatch | Compare `bin/protocol-runtime version`, `package.properties`, the artifact filename, and Maven Central coordinates. |

## Boundary

Distribution package governance is allowed only in `runtime-app`, build
configuration, examples, docs, or a future dedicated app/distribution module.
It must not add framework, service-manager, filesystem-layout, deployment
wrapper, installer, checksum/signing, or packaging dependencies to
`runtime-core`, `runtime-protocol-*`, or `protocol-sdk`.
