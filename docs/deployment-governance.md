# Standalone Collector Deployment Governance

This guide describes the `0.13.0` app-owned deployment governance baseline for
`runtime-app`. It applies to the shaded standalone collector jar and does not
change `runtime-core`, protocol bindings, or `protocol-sdk`.

## CLI Commands

The standalone jar accepts the existing config arguments plus deployment
commands:

```sh
java -jar runtime-app-0.13.0-standalone.jar --validate --config conf/collector.properties
java -jar runtime-app-0.13.0-standalone.jar --dry-run --config conf/collector.properties --status-export run/status.json
java -jar runtime-app-0.13.0-standalone.jar --config conf/collector.properties
java -jar runtime-app-0.13.0-standalone.jar --stop --pid-file run/protocol-runtime.pid
```

- `--validate` checks configuration and exits without creating a collector.
- `--dry-run` validates and builds the configured collector model, then exits
  without binding ports or connecting to Kafka/MQTT.
- `--status-export <file>` writes the JSON status snapshot used by scripts.
- `--stop --pid-file <file>` sends a stop signal to the process recorded in the
  PID file. A missing or stale PID file is treated as already stopped.

## Profiles And Override Order

Profiles use the `collector.profile` property or `--profile <name>`.
Profile names may contain letters, digits, dash, underscore, and dot.

For each explicit `--config` file, the loader applies this order:

1. Runtime defaults inside `runtime-app`.
2. Each `--config` file in command-line order.
3. Optional sibling profile file for each config file.
   `conf/collector.properties --profile production` loads
   `conf/collector-production.properties` when it exists.
4. Command-line `--key=value` overrides.

This keeps the old single `collector.properties` path compatible while allowing
operators to keep local, test, staging, and production overrides small.

## Runtime Directories

`collector.runtime.dir` is the base directory for deployment-owned files.
Relative child paths are resolved from it.

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

The app creates these directories only when
`collector.runtime.createDirectories=true`. Dry-run never binds ports or connects
to external systems, but it may write a requested `--status-export` file.

## Log File Policy

The runtime prints operational status to stdout/stderr and uses JDK logging for
management request logs. Production supervisors should redirect stdout/stderr to
`collector.runtime.logFile` or an equivalent service-manager log target.

Log policy:

- Do not log management tokens, credentials, or payload bytes.
- Use supervisor-level rotation where possible, such as `logrotate`, systemd
  `StandardOutput=append:...` with external rotation, or launchd log rotation.
- Keep parsed records in the configured sink, not in the process log.
- Keep `collector.management.access=local` unless remote management access has
  an explicit network and token policy.

## PID, Stop, And Service Templates

Set `collector.runtime.pidFile` when the collector is run by scripts or service
managers. The app writes the current JVM PID after startup succeeds and removes
it during graceful shutdown.

Templates:

- [`../examples/protocol-runtime-stop.sh`](../examples/protocol-runtime-stop.sh)
- [`../examples/protocol-runtime.service`](../examples/protocol-runtime.service)
- [`../examples/com.qbsstg.protocol-runtime.plist`](../examples/com.qbsstg.protocol-runtime.plist)

These files are examples only. Tests do not install system services.

## Status Export

`collector.runtime.statusFile` writes the same JSON shape exposed by the
management `/status` endpoint. It includes lifecycle, health, readiness,
sources, listeners, sink, backpressure, failure counters, management status,
management metrics, and health history.

Use it for local scripts that need status without making an HTTP management
request. It is not an external observability exporter.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Config validation fails | Run `--validate`, inspect every printed error, then rerun `--dry-run`. |
| Port conflict on startup | Check TCP/HTTP listener ports and management port. Port `0` is useful for tests only. |
| File sink path fails | Ensure the sink parent directory exists or set `collector.runtime.createDirectories=true` for runtime directories. |
| Management returns 401 | Verify `collector.management.access=token` and pass `Authorization: Bearer <token>` or `X-Management-Token`. |
| Management returns 403 | `collector.management.access=local` rejected a non-loopback client. Bind locally or switch to reviewed token access. |
| Parse failures appear | Inspect `/status` or status export `metrics.lastParseFailure` and the raw payload preview. |
| Backpressure appears | Inspect `backpressureRetryLaterCount`, `backpressureDropCount`, and the configured payload/sink failure thresholds. |
| Stop does nothing | Verify the PID file path, owner, process user, and whether the PID is stale. Repeated stop on a missing PID file is expected to exit successfully. |

## Boundary Rules

Deployment governance stays in `runtime-app` or future dedicated app/adapter
modules. `runtime-core` must not gain Spring, Netty, Kafka, MQTT, HTTP,
database, Redis, observability exporter, service-manager, filesystem-layout,
access-control, or request-logging dependencies. `protocol-sdk` remains
parser-only.
