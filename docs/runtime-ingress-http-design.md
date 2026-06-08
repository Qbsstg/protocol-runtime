# Runtime HTTP Ingress Design

This note defines the first `runtime-ingress-http` boundary for the `0.5.0`
development line. It is a design contract only; it does not introduce an HTTP
server dependency yet.

## Goals

- Accept HTTP request bodies as runtime ingress payloads.
- Convert each accepted request into an `IngressEnvelope`.
- Dispatch the envelope through `RuntimePipelineRunner` and the configured
  `RuntimeParserBinding`.
- Return an HTTP response derived from the runtime outcome.
- Keep all HTTP server, routing, request limit, and response policy code out of
  `runtime-core`.
- Keep protocol SDK modules out of `runtime-ingress-http`.

## Non-Goals

- No Spring Boot default runtime.
- No HTTP dependency in `runtime-core` or `protocol-sdk`.
- No protocol parsing inside the HTTP adapter.
- No durable retry queue, database sink, Redis cache, or object storage.
- No management API, dashboard, authentication, TLS certificate management, or
  metrics exporter in the first HTTP ingress boundary.
- No replacement for the existing TCP/Netty collector path.

## Proposed Module

| Module | Responsibility |
| --- | --- |
| `runtime-ingress-http` | Own HTTP listener lifecycle, request parsing, request limits, source mapping, payload mapping, response policy, and conversion to `IngressEnvelope`. |

Allowed dependencies:

- `runtime-core`
- an HTTP server/client stack selected by this module
- test dependencies

Not allowed:

- protocol SDK parser modules
- `runtime-protocol-*`
- Kafka, MQTT, database, Redis, object storage, or observability exporters
- app framework dependencies in `runtime-core`

## Endpoint Shape

The first endpoint should be intentionally narrow:

| Field | Baseline |
| --- | --- |
| Method | `POST` |
| Path | `/ingress/{sourceId}` or configured static path |
| Body | raw binary or text payload forwarded as bytes |
| Protocol selection | adapter/source configuration, not URL parsing by default |
| Content type | recorded as envelope metadata; not used by `runtime-core` |
| Response body | small structured response or empty response, owned by adapter policy |

The adapter may support a static endpoint path for deployments that do not want
source ids in URLs. In that mode, source id must come from adapter
configuration or an explicitly configured header.

## Configuration Shape

The HTTP adapter should follow the named-adapter pattern planned for `0.5.0`:

```properties
collector.http.listeners=http-main
collector.http.listener.http-main.host=0.0.0.0
collector.http.listener.http-main.port=8080
collector.http.listener.http-main.path=/ingress/{sourceId}
collector.http.listener.http-main.sourceIdMode=PATH
collector.http.listener.http-main.sourceIdHeader=X-Protocol-Source
collector.http.listener.http-main.maxPayloadBytes=65536
collector.http.listener.http-main.protocol=iec104
collector.http.listener.http-main.backpressure=ACCEPT
collector.http.listener.http-main.responseMode=ACK_ON_ACCEPT
```

Baseline validation rules:

- host must be non-empty
- port must be `0..65535`
- path must start with `/`
- `sourceIdMode` must be `PATH`, `HEADER`, or `CONFIGURED`
- `sourceIdHeader` is required when `sourceIdMode=HEADER`
- configured source id is required when `sourceIdMode=CONFIGURED`
- `maxPayloadBytes` must be `0` or positive
- protocol must map to an existing runtime parser binding
- response mode must be a supported adapter response policy

## Envelope Mapping

The adapter converts a request into an `IngressEnvelope`:

| Envelope field | HTTP source |
| --- | --- |
| `sourceId` | path variable, configured header, or configured source id |
| `transport` | `http` |
| `payload` | request body bytes |
| `receivedAt` | adapter clock when body is accepted |
| `attributes` | method, path, query string, content type, remote address, request id, selected protocol, and listener name |

HTTP-specific values remain attributes. They must not become new
`runtime-core` fields unless more than one adapter proves the need for a
protocol-neutral contract.

## Runtime Wiring

The adapter should not parse protocol frames directly. It owns only request to
envelope conversion and runner invocation:

```text
HTTP request
  -> runtime-ingress-http
  -> IngressEnvelope
  -> RuntimePipelineRunner
  -> selected RuntimeParserBinding
  -> RecordSink / FailureSink
  -> HTTP response policy
```

Runner creation can follow one of two models:

- per-listener runner, when the listener has one configured source/protocol
- per-request runner lookup, when source id or protocol can vary per request

The lookup model belongs to the adapter or app assembly. It must not require
`runtime-core` to know HTTP routing rules.

## Backpressure Policy

HTTP response handling is adapter policy:

| Runtime decision | Baseline HTTP response |
| --- | --- |
| `ACCEPT` | continue parsing request payload |
| `DROP` | return `202 Accepted` or `204 No Content` according to response mode, record drop counters |
| `RETRY_LATER` | return `429 Too Many Requests` or `503 Service Unavailable` with optional `Retry-After` |

`runtime-core` returns a backpressure decision. It does not choose HTTP status
codes.

## Parse Failure Policy

Malformed payloads should route to `FailureSink` and return a response based on
adapter response mode:

| Response mode | Parse failure response |
| --- | --- |
| `ACK_ON_ACCEPT` | success response after the request is accepted and failure is routed |
| `ACK_ON_PARSE_SUCCESS` | `422 Unprocessable Entity` or `400 Bad Request` after parse failure |
| `NO_BODY` | status-only response |

The first implementation should prefer deterministic tests over a broad status
code matrix. Response policy can expand after downstream users prove a need.

## Request Limits

The adapter owns HTTP request limits:

- max body size
- read timeout
- allowed methods
- allowed content types, if enabled
- max request header size, if exposed by the selected HTTP stack
- connection keep-alive policy

Exceeded request limits should fail before building an `IngressEnvelope`.

## Test Strategy

Unit tests:

- configuration validation
- source id resolution from path/header/configuration
- request attributes mapping
- payload limit rejection
- response policy mapping for accept, drop, retry, parse success, and parse
  failure

Integration tests:

- local port `0` bind
- real HTTP client POST to the adapter
- `RecordSink` receives parsed records for a known IEC104 payload
- malformed payload routes to `FailureSink`
- `RETRY_LATER` returns the configured HTTP retry response
- no protocol SDK dependency appears in `runtime-ingress-http`

The first implementation should avoid live external services. Embedded or
in-process HTTP server tests are acceptable inside `runtime-ingress-http`.

## Open Decisions

- Whether the first implementation should use JDK `HttpServer`, Netty HTTP, or
  another minimal HTTP stack.
- Whether response mode should be per listener or per source.
- Whether the adapter should support batched payloads in the first release.
- Whether authentication belongs in `runtime-ingress-http` or a later app
  security adapter.
- Whether HTTP health/status endpoints should live in this module or a separate
  management module.
