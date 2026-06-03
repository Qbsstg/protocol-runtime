# Protocol Runtime 0.4.0 Release Notes

`0.4.0` has been tagged as `v0.4.0` and published to Maven Central.

## Highlights

- Adds runtime protocol binding modules for IEC101, IEC103, and Modbus while
  keeping `runtime-core` adapter-free.
- Keeps protocol parsing in the released `protocol-sdk:0.7.0` artifacts and
  adapts parser results into runtime records through `runtime-protocol-*`
  modules.
- Adds app-level protocol selection for `iec104`, `iec101`, `iec103`, and
  `modbus` sources/listeners while preserving the existing IEC104 default
  `collector.properties` path.
- Adds TCP ingress smoke coverage for IEC101, IEC103, and Modbus across
  `runtime-ingress-tcp-netty`, `RuntimePipelineRunner`, and the runtime
  protocol binding boundary.
- Preserves the runtime dependency boundary: Netty remains in TCP ingress,
  app, and test assembly, while Spring, Kafka, MQTT, HTTP, database, Redis, and
  observability exporters remain out of `runtime-core`.

## Scope

`0.4.0` moves runtime from a single IEC104 app baseline toward a multi-protocol
collector runtime. It adds protocol binding capability without turning
`runtime-core` into an adapter or application module.

The release adds `runtime-protocol-iec101`, `runtime-protocol-iec103`, and
`runtime-protocol-modbus`. They adapt SDK parser results into
`RuntimeParseResult` values and keep serial/TCP/UDP transport policy outside
the binding modules. `runtime-app` can choose those bindings per configured
source/listener and route TCP ingress bytes into the selected parser binding.
`runtime-smoke-tests` proves the additional protocol bindings behind the TCP
ingress and runner boundary without promoting smoke-test dependencies into
production modules.

## Dependency Policy

`runtime-core` remains adapter-free. Protocol-specific runtime bindings consume
released SDK parser modules only. Netty stays in `runtime-ingress-tcp-netty` or
app/test assembly. Kafka, MQTT, HTTP, database, Redis, object storage, and
observability exporters remain deferred to dedicated adapter modules.

`protocol-sdk` remains parser-only and must not depend on `protocol-runtime`.

## Published Artifacts

- `io.github.qbsstg:protocol-runtime:0.4.0`
- `io.github.qbsstg:runtime-core:0.4.0`
- `io.github.qbsstg:runtime-protocol-iec104:0.4.0`
- `io.github.qbsstg:runtime-protocol-iec101:0.4.0`
- `io.github.qbsstg:runtime-protocol-iec103:0.4.0`
- `io.github.qbsstg:runtime-protocol-modbus:0.4.0`
- `io.github.qbsstg:runtime-ingress-tcp-netty:0.4.0`
- `io.github.qbsstg:runtime-app:0.4.0`
- `io.github.qbsstg:runtime-app:0.4.0:standalone`

`runtime-smoke-tests` remains a test-only repository module and is
intentionally skipped for Central publishing in this release.

## Release Verification

- Tag: `v0.4.0`
- Release commit: `7d81a39111553b2b3970858329b7bee76e060c48`
- Central deployment: `921e97e5-e002-4498-865f-a3106ed06042`
- Deployment state: `PUBLISHED`
- `mvn -q verify` passed before upload.
- Signed Central dry run passed with `central.skipPublishing=true`.
- Maven Central resolution was verified with an isolated local Maven repository
  for all published runtime artifacts and the `runtime-app` `standalone`
  classifier.
