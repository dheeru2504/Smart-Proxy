# Smart-Proxy Distributed API Gateway: Reproducible Benchmark Plan

This document establishes the official benchmark methodology, test scenarios, execution scripts, metrics extraction queries, and validation criteria for evaluating the **Smart-Proxy** distributed API gateway.

---

## 1. Objectives & Scope

The benchmark plan evaluates five core architectural characteristics:
1. **Raw Gateway Proxying Throughput & Latency Percentiles (p50, p90, p95, p99)** under steady and peak load.
2. **Redis Lua Script Overhead**: End-to-end Redis latency for atomic Token Bucket and Multi-Tier rate limit evaluations.
3. **429 Rate Limiter Accuracy**: Strict adherence to configured burst capacities and token refill rates without leaks or over-allocation under concurrent race conditions.
4. **L7 Load Balancer Traffic Distribution**: Distribution variance across upstream instances under Round Robin, Weighted Round Robin, Least Connections, and Power of Two Choices (P2C).
5. **Fault Injection & Resilience Overhead**: Fast-fail latency under Circuit Breaker `OPEN` state, active health check isolation speed, and retry latency budgets.

---

## 2. Benchmark Environment & Prerequisites

### Hardware & Operating System
- **CPU**: 8+ Physical Cores (dedicated, non-virtualized/pinned for clean measurements)
- **RAM**: 16 GB+
- **Network**: Local loopback or dedicated 10GbE network interface
- **OS**: Linux / Windows 11 with TCP socket buffer tuning (`net.ipv4.tcp_tw_reuse = 1`, `net.core.somaxconn = 65535`)

### Software Stack
- **Gateway Runtime**: Java 21+ / JVM flags:
  ```bash
  -server -Xms4g -Xmx4g -XX:+UseZGC -XX:+ZGenerational
  ```
- **Redis**: Redis 7+ standalone / cluster instance
- **PostgreSQL**: PostgreSQL 16+
- **Load Testing Tools**: [k6](https://k6.io/), [wrk](https://github.com/wg/wrk), or [hey](https://github.com/rakyll/hey)

---

## 3. Test Scenarios & Execution Commands

### Benchmark 1: Baseline Throughput & Latency Percentiles (p50, p95, p99)

- **Goal**: Measure gateway proxying throughput (RPS) and latency distribution for pass-through traffic.
- **k6 Command**:
  ```bash
  k6 run --vus 50 --duration 60s docker/benchmarks/k6-load-test.js --env SCENARIO=baseline
  ```
- **wrk Command**:
  ```bash
  wrk -t8 -c100 -d60s --latency http://localhost:8080/api/v1/orders
  ```
- **Observed Metrics**:
  - `http_reqs`: Requests per second (RPS)
  - `http_req_duration`: p50, p90, p95, p99 latency in ms
  - Prometheus Metric: `smartproxy_requests_latency_seconds{quantile="0.99"}`

---

### Benchmark 2: Redis Lua Rate Limiter Overhead

- **Goal**: Isolate and measure the latency introduced by Redis Lua execution during rate limit decisions.
- **k6 Command**:
  ```bash
  k6 run --vus 100 --duration 60s docker/benchmarks/k6-load-test.js --env SCENARIO=rate_limited
  ```
- **Prometheus Metric Extraction**:
  ```promql
  # Average Redis Lua Latency
  rate(smartproxy_ratelimiter_redis_latency_seconds_sum[1m]) / rate(smartproxy_ratelimiter_redis_latency_seconds_count[1m])
  ```

---

### Benchmark 3: 429 Rate Limiter Accuracy & Quota Verification

- **Goal**: Validate that exactly $N$ tokens are permitted during a burst and all excess requests receive HTTP 429.
- **Test Setup**:
  - Policy: Capacity = 100, Refill Rate = 0 tokens/sec (drain test).
  - Workload: 500 simultaneous requests fired via 50 concurrent workers.
- **hey Command**:
  ```bash
  hey -n 500 -c 50 -H "X-API-Key: benchmark-client-key" http://localhost:8080/api/v1/orders
  ```
- **Validation Equation**:
  $$\text{Allowed (HTTP 200)} = \text{Capacity} = 100$$
  $$\text{Throttled (HTTP 429)} = \text{Total Requests} - \text{Capacity} = 400$$
- **Header Check**:
  - Response on 101st request contains `HTTP/1.1 429 Too Many Requests` with `Retry-After: <seconds>` and `X-RateLimit-Remaining: 0`.

---

### Benchmark 4: L7 Load Balancer Distribution Verification

- **Goal**: Verify traffic distribution variance across $M$ backend instances under different strategies.
- **Execution**:
  1. Configure 3 mock upstream instances ($A$, $B$, $C$).
  2. For **Weighted Round Robin** (Weights: $A=3, B=2, C=1$):
     - Send 6,000 requests.
     - Expected Distribution: $A \approx 3,000$, $B \approx 2,000$, $C \approx 1,000$ (Ratio: 3:2:1).
  3. For **Round Robin / P2C / Least Connections**:
     - Send 3,000 requests.
     - Expected Distribution: $A \approx 1,000$, $B \approx 1,000$, $C \approx 1,000$ (Uniform $\pm 2\%$).
- **Metric Query**:
  ```promql
  sum by (instance) (smartproxy_loadbalancer_selections_total)
  ```

---

### Benchmark 5: Fault Injection & Circuit Breaker Reaction Latency

- **Goal**: Measure circuit breaker trip reaction time and fast-fail latency under downstream failure cascades.
- **Execution**:
  1. Inject downstream faults (mock backend returning HTTP 500 on 75% of calls).
  2. Measure number of calls before state trips `CLOSED -> OPEN` (must equal `minimumNumberOfCalls`).
  3. Measure latency of fast-failed calls in `OPEN` state:
     - Expected: $< 1\text{ms}$ (instant gateway rejection without touching network).
  4. Measure recovery transition after `waitDurationInOpenStateSec`.
- **Metric Query**:
  ```promql
  smartproxy_circuitbreaker_state{service="order-service"}
  ```

---

## 4. Automated Benchmark Execution Harness

Execute the automated end-to-end benchmark test harness:
```powershell
.\mvnw.cmd test -Dtest=BenchmarkHarnessTest
```
This runs deterministic in-memory throughput, latency percentile, distribution variance, and 429 accuracy measurements.
