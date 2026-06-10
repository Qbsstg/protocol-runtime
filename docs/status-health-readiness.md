# Runtime App Health And Readiness Status

This guide explains the local `runtime-app` status line emitted by the
standalone collector. It is intentionally app-owned documentation: the
`0.11.0` management endpoint exposes the same health/readiness/status evidence
as JSON, but it does not add a framework dependency, metrics exporter,
database, broker dependency, or management concern to `runtime-core`.

`StandaloneCollectorMain` writes a status line after a successful start and
again during shutdown. Look for lines starting with:

```text
Protocol Runtime collector status
```

When `collector.management.enabled=true`, the standalone collector also exposes
the app-local snapshot through the configured management paths:

```text
/health
/readiness
/status
```

The most important fields are:

| Field | Meaning |
| --- | --- |
| `state` | The collector lifecycle state: `CONFIGURED`, `STARTING`, `RUNNING`, `STOPPING`, `STOPPED`, or `FAILED`. |
| `health` | The derived app health state: `HEALTHY`, `DEGRADED`, `FAILED`, or lifecycle-aligned non-running states. |
| `readiness` | Whether the collector can accept ingress now: `READY` or `NOT_READY`. |
| `healthReasons` | Empty for healthy collectors; otherwise explains why health is degraded or readiness is blocked. |
| `tcpListeners`, `httpListeners`, `kafkaConsumers`, `mqttClients` | Configured ingress status, including running flags and bound addresses where applicable. |
| `parsedRecords`, `parseFailures`, `backpressureRetryLater`, `backpressureDrop`, `sinkFailures` | Runtime counters that explain pressure, data quality, and sink behavior. |
| `fileSink` | File sink output path, open state, active byte count, retained history count, rotation count, and rotation limits. |

## Status Matrix

| Lifecycle and evidence | Health | Readiness | Typical reasons |
| --- | --- | --- | --- |
| Created but not started | `CONFIGURED` | `NOT_READY` | `lifecycle=CONFIGURED` |
| Start in progress | `STARTING` | `NOT_READY` | `lifecycle=STARTING` |
| Running, listeners running, configured file sink open, no pressure counters | `HEALTHY` | `READY` | `[]` |
| Running and still accepting ingress, but parse failures, sink failures, or backpressure counters exist | `DEGRADED` | `READY` | `parseFailures=N`, `sinkFailures=N`, `backpressureRetryLater=N`, `backpressureDrop=N` |
| Running but no listener is available, a listener is not running, or a configured file sink is not open | `DEGRADED` | `NOT_READY` | `listeners=0`, `listenerNotRunning=...`, `fileSink=missing`, `fileSinkNotOpen=...` |
| Startup failed or a fatal collector exception was recorded | `FAILED` | `NOT_READY` | `startupFailure=...`, `lastException=...` |
| Stop in progress or stopped | `STOPPING` / `STOPPED` | `NOT_READY` | `lifecycle=STOPPING`, `lifecycle=STOPPED` |

## Example Status Lines

The examples are trimmed to the fields operators usually need first.

Healthy TCP collector:

```text
Protocol Runtime collector status state=RUNNING health=HEALTHY readiness=READY healthReasons=[] listeners=1 parsedRecords=12 parseFailures=0 sinkFailures=0 sink=in-memory tcpListeners=[default@127.0.0.1:2404->127.0.0.1:2404/running=true/active=1/protocol=iec104]
```

Degraded but ready after malformed protocol payloads:

```text
Protocol Runtime collector status state=RUNNING health=DEGRADED readiness=READY healthReasons=[parseFailures=3] listeners=1 parsedRecords=42 parseFailures=3 sinkFailures=0 backpressureRetryLater=0 backpressureDrop=0
```

Degraded but ready after backpressure decisions:

```text
Protocol Runtime collector status state=RUNNING health=DEGRADED readiness=READY healthReasons=[backpressureRetryLater=5] listeners=1 parsedRecords=100 backpressureRetryLater=5 backpressure=RETRY_LATER
```

Not ready because a configured file sink is not open:

```text
Protocol Runtime collector status state=RUNNING health=DEGRADED readiness=NOT_READY healthReasons=[fileSinkNotOpen=target/runtime-records.ndjson] listeners=1 sink=file fileSink=target/runtime-records.ndjson/open=false/activeBytes=4096/history=1/rotations=1/maxBytes=1048576/maxHistory=3
```

Failed startup, commonly caused by a port conflict:

```text
Protocol Runtime collector status state=FAILED health=FAILED readiness=NOT_READY healthReasons=[startupFailure=java.net.BindException:Address already in use] listeners=0 activeConnections=0
```

## Triage Order

1. Check `readiness` first. `NOT_READY` means ingress is not currently safe to
   send to this collector.
2. Read `healthReasons`. These values point to the blocking or degraded
   subsystem before you inspect the longer listener and sink fields.
3. If the reason starts with `listenerNotRunning`, inspect the matching
   `tcpListeners`, `httpListeners`, `kafkaConsumers`, or `mqttClients` entry.
4. If the reason starts with `fileSink`, check file permissions, missing parent
   directories, disk capacity, and whether the collector can reopen the output
   path on restart.
5. If `parseFailures` increases while readiness is still `READY`, isolate the
   client or source producing malformed payloads. Parser failures are routed to
   the failure sink and do not stop the collector.
6. If `sinkFailures` increases, inspect `lastSinkFailure`, sink logs, disk
   health, and sink failure backpressure settings.
7. If `backpressureRetryLater` or `backpressureDrop` increases, inspect
   `backpressure`, `maxPayloadBytes`, `oversizedPayloadDecision`,
   `sinkFailureThreshold`, and `sinkFailureDecision`.

## Reason Catalog

| Reason | Meaning | First check |
| --- | --- | --- |
| `lifecycle=CONFIGURED` | Collector was created but not started. | Start the collector or inspect startup orchestration. |
| `lifecycle=STARTING` | Startup is in progress. | Wait briefly, then re-check for `RUNNING` or `FAILED`. |
| `lifecycle=STOPPING` | Shutdown is in progress. | Confirm the process is intentionally stopping. |
| `lifecycle=STOPPED` | Collector is stopped. | Restart only if this is unexpected. |
| `listeners=0` | No ingress source is configured or visible in the snapshot. | Check source/listener configuration. |
| `listenerNotRunning=tcp:<name>` | A TCP listener exists but is not running. | Check bind host, port, Netty server startup, and port conflicts. |
| `listenerNotRunning=http:<name>` | An HTTP listener exists but is not running. | Check bind host, port, path, and JDK HTTP server startup. |
| `listenerNotRunning=kafka:<name>` | A Kafka consumer exists but is not running. | Check bootstrap servers, topic, group id, credentials, and consumer lifecycle logs. |
| `listenerNotRunning=mqtt:<name>` | An MQTT client exists but is not running. | Check broker URI, client id, topic, credentials, and client lifecycle logs. |
| `fileSink=missing` | Sink type is `file`, but no file sink status is available. | Check app sink assembly and configuration. |
| `fileSinkNotOpen=<path>` | File sink exists but is closed. | Check path permissions, disk space, parent directory, and previous sink exceptions. |
| `parseFailures=N` | Protocol parser rejected `N` payloads. | Inspect the failure sink and offending source id. |
| `sinkFailures=N` | Record or failure sink threw `N` exceptions. | Inspect `lastSinkFailure` and sink-specific logs. |
| `backpressureRetryLater=N` | Runtime asked ingress to retry later `N` times. | Check payload size, sink failure thresholds, and pressure policy. |
| `backpressureDrop=N` | Runtime dropped `N` payloads by configured policy. | Check whether drop mode is intentional for this source. |
| `startupFailure=...` | Startup failed and rollback preserved the reason. | Inspect the exception message, commonly bind failures or invalid external endpoints. |
| `lastException=...` | A fatal exception was recorded by the collector lifecycle. | Inspect the exception class and app logs around the timestamp. |

## Boundary

This status model is local to `runtime-app`. The `0.11.0` management endpoint
uses JDK `HttpServer` in the app boundary and is separate from
`runtime-ingress-http`, which remains the protocol-payload ingestion adapter.
It is safe to build future metrics exporters, dashboards, databases,
Redis-backed health history, or broker-publishing integrations in dedicated app
or adapter modules, but those dependencies must not move into `runtime-core` or
`runtime-protocol-*`.
