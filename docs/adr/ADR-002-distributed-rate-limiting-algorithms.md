# ADR-002: Distributed Rate Limiting via Atomic Redis Lua Scripts

## Status
**ACCEPTED**

## Context
In a distributed multi-instance API gateway deployment, rate limiting must be synchronized across all gateway worker nodes to prevent bypass attacks, maintain fair quota distribution across tenants, and guard upstream services against traffic spikes or denial-of-service (DoS) attempts.

In-memory rate limiters (e.g. standard Guava RateLimiter) only operate per instance and do not scale horizontally across cluster nodes. Naive multi-step Redis commands (`GET` followed by `INCR` or `SET`) introduce race conditions, clock skew vulnerability, and unnecessary network round-trips.

## Decision
We will build a custom **Distributed Rate Limiting Engine** utilizing **Redis with atomic Lua scripts**, accessed via Spring Data Redis Reactive (`ReactiveStringRedisTemplate` / `ReactiveRedisScript`).

### Key Architectural Elements:
1. **Multiple Supported Rate Limiting Algorithms**:
   - **Token Bucket (`token_bucket.lua`)**: Ideal for burst-tolerant API consumption. Refills tokens smoothly over time based on configured replenishment rate and burst capacity.
   - **Sliding Window Log / Counter (`sliding_window.lua`)**: Prevents boundary-burst issues found in fixed window counters by partitioning time into micro-buckets or sorted sets with epoch timestamps.
   - **Leaky Bucket (`leaky_bucket.lua`)**: Smooths out egress requests to a steady constant rate for sensitive backend downstreams.
2. **Atomic Execution via Lua**:
   - The entire evaluate-and-decrement/increment sequence runs atomically inside Redis single-threaded execution context, guaranteeing ACID isolation with zero race conditions across gateway instances.
3. **Multi-Dimensional Partitioning (Rate Limit Keys)**:
   - Rate limit keys are dynamically resolved using flexible evaluators:
     - Client IP / CIDR block (anonymous access)
     - API Key / Consumer ID (authenticated access)
     - JWT Claim (`sub`, `tenant_id`, `tier`)
     - Route ID / Endpoint path
4. **Standardized Response Headers**:
   - `X-RateLimit-Limit`: Maximum permitted quota in the current window.
   - `X-RateLimit-Remaining`: Remaining capacity.
   - `X-RateLimit-Reset`: Milliseconds or timestamp until quota replenishment.
   - `Retry-After`: Seconds to wait when HTTP `429 Too Many Requests` is returned.
5. **Fail-Open vs. Fail-Closed Strategy**:
   - Configurable fallback mode if the Redis cluster encounters temporary unreachability:
     - `FAIL_OPEN`: Allow traffic through while logging alarms (prioritizes availability).
     - `FAIL_CLOSED`: Reject traffic with 429/503 (prioritizes backend protection).

## Consequences

### Positive:
- True distributed state consistency across arbitrary gateway nodes.
- Sub-millisecond decision latency due to single-round-trip atomic Lua scripts.
- Highly configurable per-route, per-tier, and per-tenant policy overrides.

### Negative / Trade-offs:
- Redis cluster availability is critical for rate-limiting enforcement.
- Memory management in Redis requires TTL expiration policies on all rate-limit tracking keys.
