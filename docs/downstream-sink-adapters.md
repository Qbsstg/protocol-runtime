# Downstream Sink Adapter Troubleshooting

This note documents the `0.18.0` downstream sink adapter SPI baseline. It is
operator-facing guidance for diagnosing app-local sink delivery and future
adapter integration without requiring live Kafka, HTTP, MQTT, database, Redis,
or external queue services.

## What Exists In 0.18.0

- `runtime-core` provides protocol-neutral SPI contracts:
  `DownstreamSink`, `DownstreamDeliveryRequest`, `DownstreamDeliveryResult`,
  `DownstreamDeliveryOutcome`, `DownstreamSinkIdentity`, and
  `DownstreamSinkStatus`.
- `runtime-app` bridges the existing logging, file, and in-memory sinks through
  that SPI.
- `runtime-app` owns adapter configuration parsing, failed-record isolation,
  delivery counters, status JSON, management status, self-check, and hot-check.
- Tests include a fake/no-network adapter path to validate delivery results,
  backpressure contribution, failed-record correlation, and secret-safe
  diagnostics.

## What Does Not Exist Yet

`0.18.0` does not implement Kafka producer delivery, HTTP client delivery, MQTT
publisher delivery, durable retry queues, external dead-letter stores,
databases, Redis, or object storage sinks. Those dependencies must go into
future dedicated `runtime-sink-*` modules or explicit app/adapter boundaries.

`runtime-ingress-http` remains protocol payload ingestion only. It is not a
downstream HTTP sink, management API, deployment API, package API, operations
API, or diagnostics API.

## Configuration Evidence

The adapter draft configuration keys are:

```properties
collector.sink.adapter.type=app-local
collector.sink.adapter.endpoint=
collector.sink.adapter.topic=
collector.sink.adapter.authRef=
collector.sink.adapter.timeoutMillis=5000
collector.sink.adapter.batching=none
collector.sink.adapter.retry=app-local
collector.sink.adapter.deadLetter=failed-records
```

`0.18.0` accepts `app-local` and `fake-no-network`. Kafka, HTTP, and MQTT
adapter types are intentionally rejected until real dedicated adapter modules
exist.

`collector.sink.adapter.authRef` is a secret reference name, not the secret
value. Runtime status, management JSON, self-check, hot-check, and logs only
report whether an auth reference is configured.

## Status Evidence

Use CLI status, exported status JSON, or management `/status` to inspect:

- `sink.adapter.identity.qualifiedName`
- `sink.adapter.running`
- `sink.adapter.healthy`
- `sink.adapter.ready`
- `sink.adapter.backpressureDecision`
- `sink.adapter.deliveredCount`
- `sink.adapter.failureCount`
- `sink.adapter.lastResult`
- `metrics.delivery.outcomeCounts`
- `metrics.delivery.sinkFailureTypeCounts`
- `sink.failedRecords.lastSampleFile`

If `sink.adapter.ready=false`, inspect `sink.adapter.lastResult.outcome` and
`sink.adapter.backpressureDecision` first. If the outcome is
`BACKPRESSURE_REJECTED`, the collector readiness should explain the same
backpressure contribution.

## Failed-Record Correlation

When a downstream delivery fails or a fake/no-network adapter returns a failure
result, runtime-app records the failure in metrics and writes bounded samples
under `collector.sink.failedRecords.dir` when enabled.

Each failed-record sample includes:

- `schemaVersion=protocol-runtime.failed-record.v1`
- source id and protocol
- record kind and record type when available
- raw payload hex and safe metadata
- sink `failureType`, exception type, message, retryability, and
  `adapterContract=downstream-sink-spi.v1`

Use the source id, failure type, observed timestamp, and raw payload preview to
correlate the status snapshot, logs, and failed-record sample.

## Common Symptoms

- Adapter type rejected: `0.18.0` only accepts `app-local` and
  `fake-no-network`. Use `runtime-sink-*` work in a future line for real Kafka,
  HTTP, or MQTT delivery.
- Auth reference missing in status: this is expected if
  `collector.sink.adapter.authRef` is unset. Raw auth references are never
  emitted.
- Adapter not ready after failure: inspect `sink.adapter.lastResult`,
  `metrics.delivery.outcomeCounts`, and the newest failed-record sample.
- Backpressure active: inspect `sink.adapter.backpressureDecision`,
  `metrics.backpressureRetryLaterCount`, and
  `collector.backpressure.sinkFailureThreshold`.
- No failed-record sample: verify `collector.sink.failedRecords.enabled`,
  `collector.sink.failedRecords.dir`, directory permissions, and
  `sink.failedRecords.isolationFailureCount`.

## Dependency Boundary Checks

When changing adapter SPI code, verify:

- `runtime-core` has no Spring, Netty, Kafka, MQTT, HTTP client/server,
  database, Redis, external queue, storage, exporter, installer, deployment, or
  concrete sink adapter dependency.
- `protocol-sdk` remains parser-only and does not depend on `protocol-runtime`.
- ingress modules continue to map external inputs to `IngressEnvelope`; they do
  not become downstream sink adapters.
