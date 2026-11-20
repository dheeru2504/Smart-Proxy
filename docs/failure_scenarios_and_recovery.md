# Production Failure Scenarios, Chaos Testing & Operational Recovery Runbook

This runbook documents the architecture, expected behaviors, telemetry metrics, and recovery procedures for 7 critical production failure scenarios in the **Smart-Proxy Distributed API Gateway**.

---

## Summary Matrix

| Scenario | Trigger / Fault Mode | Gateway Expected Behavior | Observability & Metrics Impact | Automated / Manual Recovery |
| :--- | :--- | :--- | :--- | :--- |
| **1. Redis Outage / Latency** | Redis cluster partition or high command latency | Fail-open / graceful degradation; logs warnings; avoids 500 crashes | `smartproxy.ratelimiter.redis.errors` increments; `redis.latency` spikes | Automated reconnection via Lettuce pool once Redis resumes |
| **2. Gateway Node Crash** | Ingress node SIGKILL or VM reboot | Cold start rehydrates in-memory snapshot from PostgreSQL; zero DB reads on request | `smartproxy.config.version` hydrated; readiness probe turns green | Automated Kubernetes / process supervisor restart |
| **3. Backend Instance Crash** | Upstream instance connection refused (502/ECONNREFUSED) | Health prober transitions instance `UP -> SUSPECT -> DOWN`; LB isolates instance; retries safe requests | `smartproxy.healthcheck.instances.down` increments; `backend.failures` recorded | Prober sends periodic background checks; recovers `SUSPECT -> UP` on consecutive successes |
| **4. Backend Latency Spike** | Downstream database lock or compute saturation | Outbound deadline enforced (`504 Gateway Timeout`); in-flight concurrency released | `smartproxy.backend.timeouts` increments; `requests.latency` p95/p99 spikes | Concurrency slots immediately freed on timeout; prevents gateway thread pool starvation |
| **5. Downstream 5xx Spike** | Service error rate exceeds 50% threshold | Circuit Breaker trips `CLOSED -> OPEN`; fast-fails requests with `503 Service Unavailable` | `smartproxy.circuitbreaker.state` transitions to `2 (OPEN)`; latency drops to 0ms | Cool-down timer elapses $\to$ `HALF_OPEN` trial probes $\to$ auto-recovers to `CLOSED` |
| **6. Stale Configuration** | Split-brain node broadcasts outdated version ($v_{\text{candidate}} \le v_{\text{active}}$) | `SnapshotValidator` rejects snapshot; active valid snapshot retained | Snapshot validation warning in logs; version remains unchanged | Admin triggers `/api/admin/config/sync` to broadcast latest authoritative snapshot |
| **7. Hot Rate-Limit Key** | Extreme concurrent bursts targeting a single API key/IP | Redis Lua script executes atomically; exactly capacity tokens consumed; rest get RFC 7807 429 | `smartproxy.ratelimiter.rejected.total` increments with tag `status=429` | Atomic token refill over time; `Retry-After` header informs client backoff |

---

## Detailed Failure Scenarios & Runbooks

### Scenario 1: Redis Outage & High Latency
- **Failure Description**: The distributed Redis cache becomes unreachable due to network partition or memory saturation.
- **Expected Behavior**:
  - Rate limiting filter gracefully falls back without blocking request proxying.
  - Telemetry logs warn: `Failed to evaluate Redis rate limit Lua script: Connection refused`.
  - Micrometer counter `smartproxy.ratelimiter.redis.errors` increments.
- **Reproduction**:
  ```bash
  # Stop Redis container
  docker stop smart-proxy-redis
  
  # Send traffic to rate-limited route
  curl -i http://localhost:8080/api/v1/orders/123
  ```
- **Recovery**:
  ```bash
  # Restart Redis container
  docker start smart-proxy-redis
  ```
  - Gateway's reactive Lettuce driver automatically re-establishes connections and resumes distributed token bucket rate limiting without restarting the gateway.

---

### Scenario 2: Gateway Node Crash & Snapshot Rehydration
- **Failure Description**: A gateway ingress instance terminates unexpectedly (OOM or host reboot).
- **Expected Behavior**:
  - Upon restart, `ConfigurationSyncService.onApplicationReady()` queries PostgreSQL once to rehydrate all active routes, upstream pools, rate policies, and client credentials.
  - Zero database queries occur during runtime request processing.
  - Active snapshot version and checksum are verified and loaded into `GatewaySnapshotManager` memory in $< 500\text{ms}$.
- **Reproduction**:
  ```bash
  # Force terminate gateway process
  kill -9 <PID>
  
  # Restart gateway
  java -jar target/smart-proxy-1.0.0-SNAPSHOT.jar
  ```
- **Verification**:
  ```bash
  curl http://localhost:8080/api/admin/config/snapshot-status \
    -H "X-Admin-Key: admin-super-secret-key-change-in-prod"
  ```

---

### Scenario 3: Backend Instance Crash & Load Balancer Failover
- **Failure Description**: One backend instance in an upstream pool crashes.
- **Expected Behavior**:
  - `ActiveHealthCheckScheduler` probes the instance and records consecutive connection failures.
  - State machine transitions `UP -> SUSPECT -> DOWN` after `unhealthyThreshold` failures (default: 3).
  - `UpstreamPoolRegistry.getHealthyInstances(serviceId)` removes the crashed instance from active routing.
  - Load balancer algorithms (`RoundRobin`, `WeightedRoundRobin`, `P2C`, `LeastConnections`) route all subsequent traffic to remaining healthy instances.
- **Reproduction**:
  ```bash
  # Stop one backend instance
  docker stop backend-service-instance-1
  ```
- **Recovery**:
  - Once the backend instance restarts and passes `healthyThreshold` (default: 2) consecutive health checks, state machine transitions `DOWN -> SUSPECT -> UP`, automatically restoring it to the load balancer pool.

---

### Scenario 4: Backend Latency Spike & Deadline Timeouts
- **Failure Description**: Downstream service experiences severe slowdown (e.g. database locks).
- **Expected Behavior**:
  - Outbound timeout `route.responseTimeoutMs` terminates waiting upstream calls with `504 Gateway Timeout`.
  - `OutboundConcurrencyLimiter` decrements active connection counters in `doFinally()`, preventing gateway resource starvation.
  - `SlidingWindowMetrics` flags slow requests (`durationMs >= slowCallDurationThresholdMs`).
- **Reproduction**:
  ```bash
  # Simulate slow backend endpoint
  curl -i http://localhost:8080/api/v1/slow-endpoint
  ```
- **Verification**:
  - Check Prometheus metric: `smartproxy.backend.timeouts`.

---

### Scenario 5: Downstream 5xx Error Spike & Circuit Breaker Trip
- **Failure Description**: A downstream service returns continuous 500/502/503 errors.
- **Expected Behavior**:
  - `CustomCircuitBreaker` records outcomes in the sliding window ring buffer.
  - When `failureRatePercentage >= 50%` over `minimumNumberOfCalls` (e.g. 5 calls):
    - Circuit state trips `CLOSED -> OPEN`.
    - All incoming requests to that service fast-fail immediately with RFC 7807 `503 Service Unavailable` with 0ms backend overhead.
  - After `waitDurationInOpenStateSec` (e.g. 10s), state becomes `HALF_OPEN`.
  - Admits limited trial probes (`permittedCallsInHalfOpenState = 3`).
  - If probes succeed $\to$ recovers to `CLOSED`. If any probe fails $\to$ immediately trips back to `OPEN`.
- **Manual Intervention (Operations Dashboard)**:
  - An operator can manually force state via REST or dashboard:
  ```bash
  curl -X POST "http://localhost:8080/api/admin/telemetry/circuit-breakers/order-service/transition?state=CLOSED" \
    -H "X-Admin-Key: admin-super-secret-key-change-in-prod"
  ```

---

### Scenario 6: Stale Configuration Collision & Split-Brain Prevention
- **Failure Description**: A partitioned node or outdated admin transaction attempts to apply an obsolete configuration snapshot.
- **Expected Behavior**:
  - `SnapshotValidator` checks monotonicity: $\text{candidate.version} > \text{active.version}$.
  - Rejects stale candidates with `400 Bad Request` (`Snapshot version X is not greater than active version Y`).
  - `GatewaySnapshotManager` preserves active state without disruption.
- **Recovery**:
  - Call `/api/admin/config/sync` to pull the authoritative configuration from PostgreSQL and broadcast across RabbitMQ.

---

### Scenario 7: Hot Rate-Limit Partition Key & Token Exhaustion
- **Failure Description**: A single client or distributed botnet targets a single route/API key with thousands of concurrent requests.
- **Expected Behavior**:
  - Redis executes the atomic Lua script in a single server-side step using `redis.call('TIME')`.
  - Decrements available tokens until 0.
  - Excess requests immediately receive RFC 7807 `429 Too Many Requests` with `Retry-After: <seconds>` and `X-RateLimit-Remaining: 0`.
  - Zero token leakage or CAS race conditions occur even with 100+ concurrent threads.
- **Reproduction**:
  ```bash
  # Benchmark hot key with apache benchmark / hey
  hey -n 1000 -c 50 -H "X-API-Key: test-api-key" http://localhost:8080/api/v1/orders
  ```

---

## Verification Test Suite

All 7 chaos scenarios are validated in the automated test suite [`ChaosFailureScenariosTest.java`](file:///c:/java-projects/Smart-Proxy/src/test/java/com/smartproxy/chaos/ChaosFailureScenariosTest.java).
Run the suite via:
```powershell
.\mvnw.cmd test -Dtest=ChaosFailureScenariosTest
```
