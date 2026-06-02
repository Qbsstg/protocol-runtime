# Protocol Runtime 0.3.0 Release Notes

Draft release notes for the next `0.3.0` runtime release line.

## Planned Highlights

- Harden `runtime-app` after the published `0.2.0` standalone collector
  baseline.
- Add stronger configuration validation for source ids, TCP listeners, thread
  counts, sink settings, and file sink paths.
- Introduce a multi-source and multi-listener app configuration model without
  adding app configuration dependencies to `runtime-core`.
- Preserve the existing single-source `collector.properties` shape while
  adding named `collector.sources` and `collector.tcp.listeners` lists.
- Validate startup configuration before opening TCP listeners.
- Expose collector lifecycle state and a minimal runtime status snapshot with
  configured/running/stopped/failed state, timestamps, startup failure reason,
  listener bind information, active connection counts, parsed record and parse
  failure counters, last parse failure payload/session diagnostics,
  backpressure retry/drop counters, sink type, backpressure mode, payload
  threshold policy, and strict ASDU setting.
- Improve JDK logging and define app-level metrics counters/gauges before
  selecting any metrics exporter.
- Add configurable file sink rotation with default byte and history limits so
  local file output does not grow without bounds.
- Isolate parse failures so malformed frames route to failure handling with
  payload preview and TCP/session context without stopping healthy traffic.
- Expand backpressure policy beyond fixed smoke modes with an app-level payload
  threshold while keeping transport behavior in transport modules.
- Document the adapter boundary for future Kafka, MQTT, HTTP, database, Redis,
  and observability modules.

## Planned Scope

`0.3.0` is a production-hardening release for the standalone collector
boundary. It should make the app easier to configure, run, observe, and
diagnose while preserving the dependency boundaries established by `0.1.0` and
`0.2.0`.

Out of scope:

- Making Spring Boot the default application runtime.
- Shipping Kafka, MQTT, HTTP, database, Redis, or object storage adapters.
- Adding metrics exporter dependencies to `runtime-core`.
- Implementing IEC104 command/session state policy.
- Adding TLS certificate management or distributed scheduling.

## Dependency Policy

`runtime-core` must remain adapter-free. Netty stays in
`runtime-ingress-tcp-netty`, app assembly stays in `runtime-app`, and future
Kafka/MQTT/HTTP/storage dependencies must live in dedicated adapter or app
modules.

`protocol-sdk` remains parser-only and must not depend on `protocol-runtime`.

## Verification Target

Before release readiness, the branch should pass:

- `git diff --check`
- `mvn -q verify`
- standalone jar smoke verification for `runtime-app`
- dependency boundary checks proving `runtime-core` remains adapter-free
- tests for configuration validation, multi-source/listener configuration,
  lifecycle/status behavior, file sink rotation, parse failure isolation, and
  backpressure policy

## Release Readiness Status

The release is in planning. No `0.3.0` release branch, tag, or Maven Central
deployment exists yet.
