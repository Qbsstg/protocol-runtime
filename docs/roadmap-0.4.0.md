# Protocol Runtime 0.4.0 Roadmap

`0.4.0` starts after the published `0.3.0` runtime-app hardening release. The
development line opens with Maven reactor version `0.4.0-SNAPSHOT`.

The focus is multi-protocol runtime expansion. `protocol-sdk` already publishes
IEC104, IEC101, IEC103, and Modbus parser artifacts at `0.7.0`; runtime should
adapt those parser modules without moving transport, app, or deployment
dependencies back into the SDK.

## Primary Goal

Make `protocol-runtime` capable of hosting more than one protocol binding at
the app assembly boundary while keeping `runtime-core` dependency-light and
keeping each protocol parser inside its SDK module.

## Included Scope

- Maven development line:
  - move the reactor from released `0.3.0` to `0.4.0-SNAPSHOT`
  - keep `protocol-sdk.version` at the published `0.7.0` line until a newer SDK
    release is intentionally selected
- Protocol binding modules:
  - add `runtime-protocol-iec101` against `protocol-iec101:0.7.0`
  - add `runtime-protocol-iec103` against `protocol-iec103:0.7.0`
  - add `runtime-protocol-modbus` against `protocol-modbus:0.7.0`
  - keep each runtime protocol module free of Netty, Spring, Kafka, MQTT, HTTP,
    database, Redis, and deployable app dependencies
- Runtime app protocol selection:
  - extend source/listener configuration with an explicit protocol selector
  - preserve the existing IEC104-only `collector.properties` shape as the
    default compatibility path
  - route each configured listener/source to the correct runtime protocol
    binding
- Transport boundary:
  - continue using `runtime-ingress-tcp-netty` only for TCP byte ingress
  - keep serial transport, UDP transport, TLS, reconnect scheduling, and
    protocol command/session policy out of `runtime-core`
  - model IEC101/IEC103 serial needs as future ingress work unless tests use
    byte-stream fixtures at the runtime protocol boundary
- Verification:
  - add protocol-binding unit tests before app-level integration tests
  - add runtime-smoke-tests coverage only after the binding shape is stable
  - keep dependency boundary checks proving adapter dependencies stay out of
    `runtime-core` and `runtime-protocol-*`

## Deferred Scope

- Spring Boot or another application framework.
- Kafka, MQTT, HTTP, database, Redis, object storage, or durable sink adapters.
- HTTP health endpoints or metrics exporters.
- Serial port implementation for IEC101/IEC103.
- UDP ingress for Modbus UDP.
- IEC104/IEC101/IEC103 command session policy beyond parser adaptation.
- New parser behavior inside `protocol-sdk`.

## Development Sequence

1. Open the `0.4.0-SNAPSHOT` Maven line and document the multi-protocol plan.
2. Add the first additional runtime protocol binding module with focused unit
   tests and dependency boundary checks.
3. Add the second protection/telecontrol protocol binding after the first
   binding pattern is stable.
4. Add Modbus runtime binding planning or implementation as a separate PR so it
   can be reviewed independently from IEC101/IEC103.
5. Extend `runtime-app` configuration with protocol selection and preserve the
   existing IEC104 default path.
6. Add cross-module smoke tests for each supported runtime protocol path.
7. Run release-readiness checks and decide which protocol bindings are ready to
   publish in `0.4.0`.

## Development Progress

- Maven `0.4.0-SNAPSHOT` line: complete.
- Multi-protocol roadmap and release-note draft: complete.
- `runtime-protocol-iec101` parser binding baseline: complete.
- `runtime-protocol-iec103` parser binding baseline: complete.
- `runtime-protocol-modbus` parser binding baseline: complete.
- App-level protocol selection: complete.
- Cross-module smoke tests for additional protocols: pending.

## Acceptance Criteria

Before `0.4.0` release readiness:

- README and Chinese README describe the `0.4.0` development line.
- `docs/module-plan.md` and `docs/module-boundaries.md` document
  `runtime-protocol-*` module rules.
- At least one additional protocol binding has tests proving SDK parser output
  is adapted to runtime records or failures.
- `runtime-app` can select `iec104`, `iec101`, `iec103`, or `modbus` per
  configured source/listener while keeping the legacy IEC104 default path.
- `runtime-core` still has no Netty, SDK protocol, Spring, Kafka, MQTT, HTTP,
  database, Redis, or observability exporter dependencies.
- `runtime-protocol-*` modules do not depend on transport or app modules.
- `runtime-app` remains the only deployable assembly boundary.
- `runtime-smoke-tests` remains repository-only and skipped for Central
  publishing.
