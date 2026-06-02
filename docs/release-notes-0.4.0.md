# Protocol Runtime 0.4.0 Release Notes

Draft release notes for the `0.4.0` runtime development line.

## Planned Highlights

- Open the Maven reactor development line at `0.4.0-SNAPSHOT`.
- Start multi-protocol runtime expansion after the published `0.3.0`
  runtime-app hardening release.
- Plan runtime protocol binding modules for the published
  `protocol-sdk:0.7.0` parser artifacts:
  - `runtime-protocol-iec101` -> `protocol-iec101`
  - `runtime-protocol-iec103` -> `protocol-iec103`
  - `runtime-protocol-modbus` -> `protocol-modbus`
- Add the first IEC101 runtime binding baseline with per-source stream decoder
  buffering, runtime record mapping, parse failure routing, and reset support.
- Keep `runtime-core` dependency-light and free of SDK protocol modules,
  transport adapters, application frameworks, and downstream integrations.
- Preserve the existing IEC104 standalone collector compatibility path while
  planning app-level protocol selection.

## Scope

`0.4.0` is intended to move runtime from a single IEC104 app baseline toward a
multi-protocol collector runtime. The release should add protocol binding
capability without turning `runtime-core` into an adapter or application module.

The first implemented step is `runtime-protocol-iec101`. It adapts
`Iec101StreamDecoder` results into `RuntimeParseResult<Iec101Frame>` values and
keeps serial transport policy outside the binding module.

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
- app or smoke tests only after the app protocol-selection boundary is stable

## Release Readiness Status

Release-readiness audit work has not started yet. The `0.4.0-SNAPSHOT` Maven
line, roadmap, and IEC101 runtime binding baseline are in place. The next
planned protocol binding baselines are IEC103 and Modbus.
