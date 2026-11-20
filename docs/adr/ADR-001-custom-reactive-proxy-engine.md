# ADR-001: Custom Reactive L7 Reverse Proxy Engine over Third-Party Frameworks

## Status
**ACCEPTED**

## Context
The goal of the Smart-Proxy project is to provide a high-throughput, low-latency, distributed API Gateway and Layer-7 reverse proxy capable of handling tens of thousands of concurrent client connections with minimal resource overhead.

Off-the-shelf solutions (such as Spring Cloud Gateway, NGINX, HAProxy, Envoy, or AWS ALB) abstract away core networking, request rewriting, connection pooling, and lifecycle management. A fundamental architectural requirement of this project is to build the proxying engine natively in Java 21 using reactive, non-blocking I/O.

## Decision
We will engineer a custom **Reactive L7 Reverse Proxy Pipeline** built directly on top of **Spring WebFlux, Project Reactor, and Netty HttpClient**.

### Key Architectural Elements:
1. **Non-Blocking Reactor Netty Pipeline**:
   - Ingress requests are accepted via Netty event loops (`ServerWebExchange`).
   - Requests are processed through a custom `GatewayFilterChain` composed of ordered `GatewayFilter` stages (Security -> Rate Limiting -> Route Resolution -> Load Balancing -> Circuit Breaking -> Proxy Forwarding -> Response Transformation).
2. **Dynamic Route Matching**:
   - Prefix tree (Radix/Trie) and regular expression route matching supporting URI prefixes, HTTP methods, headers, and query parameters.
   - Dynamic routing definitions loaded from PostgreSQL (via R2DBC) and cached in-memory with invalidation events.
3. **Upstream Streaming & Connection Pooling**:
   - Utilizing Netty's `HttpClient` with dedicated, configurable connection pooling per upstream target (leased connections, max idle times, acquire timeouts, and keep-alive HTTP/1.1 and HTTP/2 multiplexing).
   - Zero-copy / reactive streaming of request/response payloads without buffering full payloads in RAM unless explicit payload transformation is configured.
4. **Header and Query Param Mutation**:
   - Stripping sensitive hop-by-hop headers (`Transfer-Encoding`, `Connection`, `Keep-Alive`, `Upgrade`, `Proxy-Authenticate`, `Proxy-Authorization`).
   - Injecting standard proxy headers (`X-Forwarded-For`, `X-Forwarded-Proto`, `X-Forwarded-Host`, `X-Forwarded-Port`, `X-Request-ID`, `X-Gateway-Latency`).

## Consequences

### Positive:
- Total architectural control over every microsecond of request processing and lifecycle.
- Zero dependency on heavy opaque proxy frameworks.
- Transparent memory footprint and deterministic garbage collection behavior under high concurrency.
- Ability to inject custom instrumentation, dynamic rate limit headers (`X-RateLimit-Remaining`, `Retry-After`), and custom circuit breaker fallbacks directly into the pipeline.

### Negative / Trade-offs:
- We must handle low-level HTTP protocol subtleties (chunked transfer encoding, hop-by-hop header cleanup, backpressure propagation, client disconnect handling) manually.
