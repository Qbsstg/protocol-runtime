# Protocol Runtime

Protocol Runtime is the JDK 21 collector runtime for the Java 8 compatible
[`protocol-sdk`](https://github.com/Qbsstg/protocol-sdk) parser modules.

The runtime owns ingestion, session lifecycle, parser binding, backpressure,
batching, and downstream delivery concerns. The SDK remains parser-only and
must not depend on this repository.

## Status

This repository is in bootstrap. The first target is a small runtime contract
surface and an IEC104 binding that consumes the published `protocol-sdk`
`0.7.0` artifacts from Maven Central.

## Module Plan

| Module | Status | Responsibility |
| --- | --- | --- |
| `runtime-core` | Bootstrap | Runtime-neutral contracts: source identity, ingress envelope, parser binding, parsed record, parse failure, and backpressure decision. |
| `runtime-protocol-iec104` | Bootstrap | First runtime protocol binding around `io.github.qbsstg:protocol-iec104:0.7.0`. |
| `runtime-ingress-tcp-netty` | Placeholder | Future Netty TCP ingress module for IEC104 and Modbus TCP sessions. No Netty dependency is added until the adapter design is implemented. |

Future modules may include MQTT, Kafka, HTTP ingress, pipelines, sinks, and a
deployable runtime application. Those dependencies belong here, not in
`protocol-sdk`.

## Dependency Direction

Allowed:

```text
protocol-runtime -> protocol-sdk
```

Forbidden:

```text
protocol-sdk -> protocol-runtime
protocol-sdk -> Spring or Netty
protocol-sdk -> MQTT or Kafka clients
protocol-sdk -> HTTP server/client frameworks
protocol-sdk -> database or Redis clients
```

## Build

JDK 21 is required.

```bash
mvn -q verify
```

## SDK Version

The bootstrap runtime consumes published SDK `0.7.0` artifacts:

- `io.github.qbsstg:protocol-core:0.7.0`
- `io.github.qbsstg:protocol-iec104:0.7.0`

The runtime can move to newer SDK versions after they are published and
verified.
