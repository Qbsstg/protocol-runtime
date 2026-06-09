# Protocol Runtime 0.6.0 Release Notes

Draft release notes for the `0.6.0` runtime release line.

## Planned Highlights

- Open the Maven reactor at `0.6.0` after the published `0.5.0`
  adapter-boundary release.
- Productionize the JDK `HttpServer` based `runtime-ingress-http` baseline from
  the standalone app boundary.
- Add runtime-app HTTP listener configuration and validation while preserving
  the existing TCP collector defaults. HTTP-only app configurations do not
  implicitly start the legacy TCP listener.
- Prove HTTP POST payloads can flow through `HttpIngressServer`,
  `RuntimePipelineRunner`, selected `runtime-protocol-*` binding, and the
  configured sinks.
- Cover HTTP success, parse failure, backpressure, payload-size rejection,
  lifecycle, and port conflict behavior.
- Add HTTP listener status snapshot and one-line formatter output alongside
  existing TCP listener status.
- Add `examples/collector-http.properties` and
  `examples/smoke-standalone-http.sh` as runnable HTTP collector examples.
- Keep Kafka and MQTT as design-only adapter boundaries until their client
  dependencies are introduced in dedicated modules.

## Scope

`0.6.0` focuses on making HTTP ingress usable as a runtime-app collector path,
not just a library-level adapter. The release should keep the dependency shape
established in `0.5.0`: `runtime-core` remains adapter-free, protocol bindings
remain parser-only, and app assembly owns transport composition.

The current `0.6.0` line supports named HTTP listeners with host,
port, path, source reference, configured/header/path source id mapping, payload
limit, response mode, backlog, and worker thread settings.

## Dependency Policy

`runtime-core` must remain free of HTTP, Kafka, MQTT, Spring, database, Redis,
object storage, and observability exporter dependencies.

`runtime-ingress-http` should remain JDK-only in this line. If a future HTTP
framework is required, it should be introduced in a dedicated adapter module or
explicitly documented release scope.

Kafka and MQTT client dependencies remain out of the reactor for this planned
release.

## Verification Target

Before release branch work, the readiness branch should pass:

- `git diff --check`
- `mvn -q verify`
- Central profile smoke with publishing disabled and signing skipped
- standalone TCP collector smoke through `examples/smoke-standalone.sh`
- HTTP collector smoke through `examples/smoke-standalone-http.sh`
- dependency boundary checks for `runtime-core`, `runtime-ingress-http`,
  `runtime-app`, and `runtime-smoke-tests`
