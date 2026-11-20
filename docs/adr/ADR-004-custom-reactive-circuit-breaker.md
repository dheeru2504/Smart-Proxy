# ADR-004: Custom Reactive Circuit Breaker Engine with Sliding Window Statistics

## Status
**ACCEPTED**

## Context
When upstream microservices fail or degrade, cascading failures can rapidly overwhelm the API Gateway, exhaust thread and connection pools, and degrade overall system availability. 

Standard industry practice frequently relies on third-party libraries like Netflix Hystrix (deprecated) or Resilience4j. In accordance with our core engineering principles, we will design and implement a custom, reactive, thread-safe circuit breaker engine from first principles.

## Decision
We will build a custom **Reactive Circuit Breaker Engine** implementing a three-state finite state machine (`CLOSED`, `OPEN`, `HALF_OPEN`) with lock-free sliding-window telemetry.

### Key Architectural Elements:
1. **Finite State Machine & State Transitions**:
   - **CLOSED**: All requests pass through to the upstream service. Metrics are recorded in a sliding time/count window.
   - **OPEN**: Triggered when the failure rate or slow-call rate exceeds the configured threshold (e.g. >= 50% failure rate over a window of 20 calls). All incoming requests fail immediately with `503 Service Unavailable` (or route to a configured fallback endpoint) without attempting network calls.
   - **HALF_OPEN**: After a configured cooldown duration (`waitDurationInOpenState`, e.g. 15 seconds), the circuit transitions to `HALF_OPEN`. A bounded number of trial probe requests (`permittedNumberOfCallsInHalfOpenState`, e.g. 5 requests) are allowed through.
     - If probe success rate meets the threshold, the circuit transitions back to `CLOSED`.
     - If any probe fails or threshold is missed, the circuit immediately reverts to `OPEN`.
2. **Lock-Free Sliding Window Telemetry**:
   - **Count-Based Window**: Circular array (Ring Buffer) of size $N$ storing outcome records (`SUCCESS`, `FAILURE`, `SLOW_CALL`).
   - **Time-Based Window**: Time-partitioned epoch buckets (e.g. 10 buckets of 1 second each) aggregated for rolling metrics.
   - Concurrency is managed using `AtomicReference` state holders and lock-free atomic accumulators (`LongAdder`, `AtomicIntegerArray`) to guarantee non-blocking execution under extreme load.
3. **Slow Call Rate Thresholds**:
   - Differentiates between hard network exceptions (connection refused, timeouts) and slow calls (calls taking longer than `slowCallDurationThreshold`, e.g. 2000ms).
4. **Fallback Handlers**:
   - Support for custom reactive fallback publishers (returning cached static JSON, default degraded responses, or alternative route redirects).

## Consequences

### Positive:
- Complete transparency of failure detection math, state transitions, and recovery behavior.
- Fully non-blocking integration with Spring WebFlux / Project Reactor pipelines (`Mono` / `Flux`).
- Zero external resilience libraries.

### Negative / Trade-offs:
- Requires strict automated concurrency testing to ensure race-free state transitions during rapid boundary conditions.
