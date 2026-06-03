# Protocol Runtime 0.4.0 Release Notes

Draft release notes for the `0.4.0` runtime release-candidate line.

## Planned Highlights

- Fix the Maven reactor release line at `0.4.0` after readiness passed.
- Start multi-protocol runtime expansion after the published `0.3.0`
  runtime-app hardening release.
- Plan runtime protocol binding modules for the published
  `protocol-sdk:0.7.0` parser artifacts:
  - `runtime-protocol-iec101` -> `protocol-iec101`
  - `runtime-protocol-iec103` -> `protocol-iec103`
  - `runtime-protocol-modbus` -> `protocol-modbus`
- Add the first IEC101 runtime binding baseline with per-source stream decoder
  buffering, runtime record mapping, parse failure routing, and reset support.
- Add the IEC103 runtime binding baseline with the same per-source stream
  decoder buffering, runtime record mapping, parse failure routing, and reset
  support.
- Add the Modbus runtime binding baseline for TCP stream and datagram parser
  modes without introducing TCP/UDP transport dependencies.
- Add app-level protocol selection for `iec104`, `iec101`, `iec103`, and
  `modbus` sources/listeners while preserving the IEC104 default
  `collector.properties` path.
- Add cross-module smoke coverage for IEC101, IEC103, and Modbus over the TCP
  ingress and `RuntimePipelineRunner` boundary.
- Keep `runtime-core` dependency-light and free of SDK protocol modules,
  transport adapters, application frameworks, and downstream integrations.
- Preserve the existing IEC104 standalone collector compatibility path.

## Scope

`0.4.0` is intended to move runtime from a single IEC104 app baseline toward a
multi-protocol collector runtime. The release should add protocol binding
capability without turning `runtime-core` into an adapter or application module.

The first implemented steps are `runtime-protocol-iec101`,
`runtime-protocol-iec103`, and `runtime-protocol-modbus`. They adapt SDK parser
results into `RuntimeParseResult` values and keep serial/TCP/UDP transport
policy outside the binding modules. `runtime-app` can now choose those
bindings per configured source/listener and route TCP ingress bytes into the
selected parser binding. `runtime-smoke-tests` now proves the additional
protocol bindings behind the TCP ingress and runner boundary without promoting
smoke-test dependencies into production modules.

## Dependency Policy

`runtime-core` must remain adapter-free. Protocol-specific runtime bindings
consume released SDK parser modules only. Netty stays in
`runtime-ingress-tcp-netty` or app/test assembly. Kafka, MQTT, HTTP, database,
Redis, object storage, and observability exporters remain deferred to dedicated
adapter modules.

`protocol-sdk` remains parser-only and must not depend on `protocol-runtime`.

## Verification Target

Before release readiness, the branch should pass:

- `git diff --check`
- `mvn -q verify`
- dependency boundary checks for `runtime-core` and all `runtime-protocol-*`
  modules
- unit tests for each new runtime protocol binding
- app and smoke tests for the app protocol-selection boundary

## Release Readiness Status

Release-readiness audit work is complete. The `0.4.0` release branch fixes
Maven reactor versions from `0.4.0-SNAPSHOT` to `0.4.0` and reruns the release
checks. No tag or real Maven Central upload is part of the release branch PR.

The next planned step after the release PR merges is to create the `v0.4.0` tag,
run the final signed dry run, and perform the manually reviewed Maven Central
upload.
