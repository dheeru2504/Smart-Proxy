# ADR-006: Real-time Admin Control Plane and React/TypeScript Telemetry Dashboard

## Status
**ACCEPTED**

## Context
Operating a distributed API Gateway, Rate Limiter, and Load Balancer requires granular runtime observability and control: inspecting active routes, dynamically adjusting rate limit tiers, updating upstream server weights, viewing health check status, and manually tripping or resetting circuit breakers.

## Decision
We will establish a dedicated **Admin Control Plane** comprising a secure backend REST/SSE API and a modern, high-performance **React + TypeScript + Vite** frontend dashboard.

### Key Architectural Elements:
1. **Separation of Gateway Ingress and Admin Plane**:
   - Gateway Ingress: Port `8080` (High-performance public/private API traffic).
   - Admin Control Plane API: Port `8081` (Internal management endpoints secured with role-based JWT auth).
2. **Real-time Metrics Streaming via Server-Sent Events (SSE)**:
   - Live stream of RPS (Requests Per Second), p50/p95/p99 latencies, upstream health state, circuit breaker status (`CLOSED`, `OPEN`, `HALF_OPEN`), and rate limit drops pushed reactively from Gateway to Dashboard.
3. **Frontend Dashboard Architecture (React + TypeScript + Vite + Modern Glassmorphic Dark UI)**:
   - **Live Telemetry & Topology View**: Real-time graph of active routes, traffic distribution per upstream instance, and health status.
   - **Route Manager**: Dynamic CRUD operations for routes (path pattern, method, upstream pool, plugins).
   - **Rate Limiter Policy Studio**: Visual configuration of Token Bucket & Sliding Window limits per tier/consumer.
   - **Load Balancer & Upstream Monitor**: Dynamic weighting, instance addition/drain, and health probe status.
   - **Circuit Breaker Command Center**: Live sliding-window failure rate visualizer with manual trip/reset overrides.
   - **Security & API Key Management**: Key generation, revocation, and IP blacklist management.

## Consequences

### Positive:
- Single-pane-of-glass operational visibility for engineers and operators.
- Dynamic configuration changes take effect in real time without restarting the gateway nodes.
