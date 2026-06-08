# Protocol Runtime 0.5.0 Release Notes

Draft release notes for the `0.5.0` runtime release-candidate line.

## Planned Highlights

- Open the Maven reactor at `0.5.0-SNAPSHOT` after the published `0.4.0`
  multi-protocol runtime release.
- Plan HTTP, Kafka, and MQTT ingestion adapter boundaries without moving those
  dependencies into `runtime-core` or `protocol-sdk`.
- Add the first HTTP ingress design note covering endpoint shape,
  configuration, envelope mapping, response policy, backpressure behavior,
  parse-failure routing, request limits, and tests.
- Separate ingress adapter responsibilities from downstream sink adapter
  responsibilities.
- Preserve the existing TCP/Netty standalone collector path and app-level
  protocol selection from `0.4.0`.
- Keep `runtime-protocol-*` modules parser-binding only.
- Define adapter configuration, source mapping, failure routing, and
  backpressure behavior before adding heavy adapter dependencies.

## Scope

`0.5.0` starts the adapter productionization line. It should make future HTTP,
Kafka, and MQTT collection work reviewable by first locking the module
boundaries, dependency rules, configuration shape, and test strategy.

The release may include the first narrow adapter implementation only after the
boundary work is reviewed. Until then, adapter dependencies remain deferred to
dedicated modules.

## Dependency Policy

`runtime-core` must remain adapter-free. HTTP, Kafka, MQTT, database, Redis,
object storage, application framework, and observability exporter dependencies
belong in dedicated adapter modules, sink modules, or app assembly.

`protocol-sdk` remains parser-only and must not depend on `protocol-runtime`.

`runtime-protocol-*` modules continue to depend only on `runtime-core`, their
published SDK parser module, and tests.

## Verification Target

Before release readiness, the branch should pass:

- `git diff --check`
- `mvn -q verify`
- dependency boundary checks for `runtime-core` and `runtime-protocol-*`
- documentation checks proving adapter dependencies are assigned to adapter
  modules only
- existing runtime-app and smoke-test coverage for the TCP/Netty and
  multi-protocol paths

## Release Readiness Status

This is a planning draft. No tag or real Maven Central upload is part of the
initial `0.5.0` planning work.

HTTP ingress design is tracked in
[`runtime-ingress-http-design.md`](runtime-ingress-http-design.md).
