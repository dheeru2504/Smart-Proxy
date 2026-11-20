# ADR-003: Custom Layer-7 Load Balancing Algorithms and Dual Health Checking Engine

## Status
**ACCEPTED**

## Context
A primary function of an API Gateway is intelligent traffic distribution across dynamic clusters of upstream backend instances. Using external load-balancer frameworks (e.g. Ribbon, Spring Cloud LoadBalancer, or Envoy) would obscure core algorithm design and internal connection routing mechanics.

The gateway requires a fully native, non-blocking L7 load balancer capable of managing dynamic server pools, tracking instance health in real time, and selecting the optimal upstream instance for every incoming HTTP request.

## Decision
We will build a custom **Reactive L7 Load Balancing Engine** supporting multiple distribution algorithms, sticky sessions, active background health probing, and passive failure tracking.

### Key Architectural Elements:
1. **Extensible Load Balancing Strategy Hierarchy (`LoadBalancerStrategy`)**:
   - **Round Robin**: Atomic index rotation (`AtomicInteger` / modulo) across all healthy upstream instances.
   - **Weighted Round Robin**: Proportional request distribution based on static server capacity weights (using an efficient smooth weighted round-robin / interleaved algorithm).
   - **Least Active Connections (Least Loaded)**: Tracks concurrent pending in-flight requests per instance via atomic counters (`AtomicInteger activeRequests`); routes to the instance with the lowest in-flight load.
   - **IP Hash / Consistent Hashing**: Consistent hash ring with virtual nodes (MD5 / Murmur3) based on client source IP or session token for sticky routing with minimal redistribution on cluster resizing.
   - **Random Selection**: Weighted/unweighted randomized distribution for lightweight workloads.
2. **Dual-Mode Health Checking**:
   - **Active Health Probing (Background Ping)**:
     - Configurable periodic HTTP ping (`GET /health` or `HEAD /`) on each upstream instance using non-blocking WebClient.
     - Tracks consecutive successful pings before marking an instance `HEALTHY` (e.g. 2 successes).
     - Tracks consecutive failed pings before marking an instance `UNHEALTHY` (e.g. 3 failures).
   - **Passive Health Checking (Inline Anomaly Detection)**:
     - Intercepts live proxy responses in real time.
     - If an instance returns `5xx` server errors, connection timeouts, or connection refused above a threshold rate (e.g. 5 consecutive 503s within 10 seconds), it is immediately marked `SUSPENDED` and removed from the active routing pool without waiting for the next active ping cycle.
3. **Dynamic Upstream Pool Management**:
   - Thread-safe `UpstreamServerPool` managing server state transitions (`HEALTHY`, `SUSPENDED`, `DRAINING`, `DEAD`).
   - Dynamic instance registration/deregistration via Admin Control Plane API.

## Consequences

### Positive:
- Total visibility into load distribution metrics, connection counts, and upstream response times.
- Zero external runtime load-balancer dependencies.
- Dual health checking prevents routing traffic to failing instances instantly (sub-second reaction time via passive checks).

### Negative / Trade-offs:
- Memory and thread pool management for active health check timers must be carefully bounded.
