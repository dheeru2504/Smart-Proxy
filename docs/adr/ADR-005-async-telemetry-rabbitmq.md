# ADR-005: Asynchronous Telemetry, Audit Logging, and Metrics Streaming via RabbitMQ

## Status
**ACCEPTED**

## Context
High-throughput API Gateways process large volumes of traffic. Writing detailed access logs, audit trails (rate limit violations, authentication failures, circuit trips), and latency metrics synchronously to disk or relational databases in the critical request path adds significant latency and can cause gateway thread starvation.

## Decision
We will decouple all non-critical telemetry and audit logging from the main request path using **RabbitMQ** as an asynchronous message bus.

### Key Architectural Elements:
1. **Fire-and-Forget Reactive Publishing**:
   - The gateway pipeline captures an immutable `GatewayAccessEvent` upon completing every request (containing timestamp, trace ID, client IP, route ID, selected upstream, status code, latency breakdowns, and rate-limit remaining).
   - Events are published asynchronously onto RabbitMQ exchanges (`smartproxy.telemetry.exchange`) without blocking the HTTP response back to the client.
2. **Dedicated Telemetry Queues & Routing Keys**:
   - `smartproxy.access.logs` (Topic: `telemetry.access.*`) -> Ingested by async storage workers into PostgreSQL audit tables.
   - `smartproxy.security.alerts` (Topic: `telemetry.security.*`) -> Rate limit breaches, unauthorized API key attempts, IP blacklist hits.
   - `smartproxy.metrics.stream` (Topic: `telemetry.metrics.*`) -> Real-time aggregates streamed to Admin Dashboard via Server-Sent Events (SSE) / WebSockets.
3. **Backpressure & Bounded Buffers**:
   - Telemetry emitters use bounded non-blocking queues (dropping or sampling logs if RabbitMQ broker is saturated) to guarantee gateway request processing is never throttled by logging infrastructure.

## Consequences

### Positive:
- Zero latency penalty on the client request path for comprehensive audit logging.
- Reliable event buffering and horizontal scalability for downstream analytics consumers.

### Negative / Trade-offs:
- Adds RabbitMQ as an operational dependency for telemetry persistence (though gateway proxying remains operational even if the telemetry bus degrades).
