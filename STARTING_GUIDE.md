# Smart-Proxy: Complete Getting Started & API Reference Guide

This comprehensive guide covers everything needed to start, run, verify, and interact with the **Smart-Proxy Distributed API Gateway**, including backend, frontend, infrastructure dependencies, and the complete catalog of exposed APIs.

---

## 1. System Architecture & Component Ports

| Component | Technology | Default Port | Description |
| :--- | :--- | :--- | :--- |
| **Backend Gateway** | Java 21 / Spring Boot WebFlux / Netty | `8080` | High-throughput distributed API gateway, custom L7 load balancer & rate limiter |
| **Operations Dashboard** | React 18 / TypeScript / Vite | `5173` | Real-time administrative operations & telemetry dashboard |
| **PostgreSQL** | PostgreSQL 16 Alpine | `5432` | Authoritative relational store for routes, policies, services, and audit logs |
| **Redis** | Redis 7.2 Alpine | `6379` | Distributed in-memory state store executing atomic Lua rate-limiting scripts |
| **RabbitMQ** | RabbitMQ 3.13 Management | `5672` (AMQP), `15672` (UI) | Distributed event bus broadcasting configuration updates (`CONFIG_UPDATED`) |
| **Mock Upstream Node 1** | Node.js / Express | `8091` | Upstream backend test server instance 1 |
| **Mock Upstream Node 2** | Node.js / Express | `8092` | Upstream backend test server instance 2 |
| **Mock Upstream Node 3** | Node.js / Express | `8093` | Upstream backend test server instance 3 |

---

## 2. Prerequisites

- **Java Development Kit (JDK)**: JDK 21+ (e.g. Eclipse Temurin 21)
- **Node.js & npm**: Node.js 18+ and npm 9+
- **Docker & Docker Compose**: Docker Desktop / Docker Engine

---

## 3. Step-by-Step Startup Guide

### Step 1: Start Infrastructure Dependencies (Docker Compose)
In the project root directory (`c:\java-projects\Smart-Proxy`), run:

```bash
docker-compose up -d postgres redis rabbitmq mock-upstream-1 mock-upstream-2 mock-upstream-3
```

Verify all containers are healthy:
```bash
docker-compose ps
```

* **PostgreSQL Credentials**: `user=smartproxy`, `password=smartproxy_dev_secret`, `database=smartproxydb`
* **Redis Auth**: `smartproxy_redis_secret`
* **RabbitMQ Management UI**: `http://localhost:15672` (`user=smartproxy`, `pass=smartproxy_rabbit_secret`)

---

### Step 2: Start the Backend Gateway (Spring Boot / Netty)

Open a new terminal in the project root (`c:\java-projects\Smart-Proxy`):

#### Using Maven Wrapper (Recommended for Development):
```powershell
# Windows PowerShell / Command Prompt
.\mvnw.cmd spring-boot:run
```

```bash
# Linux / macOS
./mvnw spring-boot:run
```

#### Or Running the Pre-Packaged Production JAR:
```powershell
# Build executable jar
.\mvnw.cmd clean package -DskipTests

# Run standalone jar
java -jar target/smart-proxy-1.0.0-SNAPSHOT.jar
```

The gateway will start on **`http://localhost:8080`**. Upon booting, it automatically connects to PostgreSQL, builds the active configuration snapshot, swaps it atomically into memory, and begins listening for ingress traffic.

---

### Step 3: Start the Operations Dashboard (React + TypeScript)

Open a separate terminal and navigate into the `dashboard/` directory:

```bash
cd dashboard

# Install npm dependencies (first time only)
npm install

# Start Vite Development Server
npm run dev
```

* Open your browser at **`http://localhost:5173`**.
* The Vite dev server is preconfigured to proxy `/api/**` and `/actuator/**` requests directly to `http://localhost:8080`.

---

## 4. Quick Verification & Health Smoke Test

Run these commands in any terminal to verify the entire stack is operational:

### 1. Check Gateway Health (Actuator)
```bash
curl http://localhost:8080/actuator/health
```
**Expected Response:** `{"status":"UP"}`

### 2. Check Active Configuration Snapshot
```bash
curl http://localhost:8080/api/admin/config/snapshot-status \
  -H "X-Admin-Key: admin-super-secret-key-change-in-prod"
```
**Expected Response:** JSON with `version`, `checksum`, `totalRoutes`, and `propagationLagMs`.

### 3. Test Proxy Request Forwarding to Upstream
```bash
curl -i http://localhost:8080/api/v1/orders/101 \
  -H "X-API-Key: test-client-key-1"
```
**Expected Response:** `HTTP/1.1 200 OK` with `X-Request-ID` and `X-RateLimit-Remaining` headers.

---

## 5. Complete API Collections Catalog

> [!NOTE]
> All Admin Control Plane endpoints (`/api/admin/**`) require the Admin Header:  
> `X-Admin-Key: admin-super-secret-key-change-in-prod` (or a valid JWT with `ROLE_ADMIN` / `ROLE_OPERATOR` / `ROLE_AUDITOR`).

### A. Public Ingress Gateway Endpoints (Reverse Proxy)
| Method | Path Pattern | Required Headers | Description |
| :--- | :--- | :--- | :--- |
| `GET/POST/PUT/DELETE` | `/api/v1/orders/**` | `X-API-Key: <key>` | Forwarded to `order-service` cluster with rate limiting, circuit breaking, and load balancing. |
| `GET/POST/PUT/DELETE` | `/api/v1/payments/**` | `X-API-Key: <key>` | Forwarded to `payment-service` cluster. |
| `GET` | `/api/v1/inventory/**` | Optional | Public anonymous endpoint with IP-based rate limiting. |

---

### B. Admin Control Plane: Configuration & Version Snapshots
| Method | Path | Query / Body | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/admin/config/snapshot-status` | None | Returns active in-memory snapshot version, published/applied timestamps, propagation lag (ms), and SHA-256 checksum. |
| `GET` | `/api/admin/config/versions` | None | Lists historical configuration version records from PostgreSQL. |
| `POST` | `/api/admin/config/sync` | None | Forces authoritative database sync and broadcasts `CONFIG_UPDATED` across RabbitMQ. |

---

### C. Admin Control Plane: Dynamic Route Management
| Method | Path | Request Body | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/admin/routes` | None | Retrieves all active routing rules. |
| `POST` | `/api/admin/routes` | JSON (see below) | Creates a new dynamic route and triggers cluster hot-swap. |
| `PUT` | `/api/admin/routes/{id}` | JSON (see below) | Modifies an existing route definition. |
| `DELETE` | `/api/admin/routes/{id}` | None | Deletes a dynamic route. |

**Route Creation Payload (`POST /api/admin/routes`):**
```json
{
  "routeId": "orders-v1",
  "pathPattern": "/api/v1/orders/**",
  "serviceId": "order-service",
  "authType": "API_KEY",
  "rateLimitPolicyId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "loadBalancerStrategy": "ROUND_ROBIN",
  "stripPrefixCount": 0,
  "responseTimeoutMs": 5000,
  "priority": 100,
  "enabled": true
}
```

---

### D. Admin Control Plane: Upstream Services & Instance Pools
| Method | Path | Request Body | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/admin/services` | None | Lists all upstream service clusters and instance pools. |
| `POST` | `/api/admin/services` | `{"serviceId": "order-service", "name": "Orders API", "loadBalancerStrategy": "ROUND_ROBIN"}` | Registers a new upstream service pool. |
| `POST` | `/api/admin/services/{serviceId}/instances` | `{"host": "127.0.0.1", "port": 8091, "weight": 3}` | Adds a backend instance to a service pool. |
| `DELETE` | `/api/admin/services/{serviceId}/instances/{instanceId}` | None | Removes a backend instance from a service pool. |

---

### E. Admin Control Plane: Rate Limiting Policies
| Method | Path | Request Body | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/admin/rate-limits` | None | Lists all distributed rate limit policies. |
| `POST` | `/api/admin/rate-limits` | JSON (see below) | Creates a new distributed rate limit policy. |
| `DELETE` | `/api/admin/rate-limits/{id}` | None | Deletes a rate limit policy. |

**Rate Limit Policy Payload (`POST /api/admin/rate-limits`):**
```json
{
  "name": "tier-1-token-bucket",
  "algorithm": "TOKEN_BUCKET",
  "burstCapacity": 100,
  "replenishRate": 10,
  "windowDurationSec": 60,
  "limitBy": "API_KEY"
}
```

---

### F. Admin Control Plane: API Clients & Key Management
| Method | Path | Request Body | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/admin/api-clients` | None | Lists all registered API clients and tenants. |
| `POST` | `/api/admin/api-clients` | JSON (see below) | Creates a new client and returns the raw plaintext API key (only displayed once). |
| `DELETE` | `/api/admin/api-clients/{id}` | None | Revokes an API client and key. |

**Client Creation Payload (`POST /api/admin/api-clients`):**
```json
{
  "name": "Acme Partner Integration",
  "tenantId": "tenant-acme",
  "roles": ["ROLE_USER", "ROLE_READ"],
  "rateLimitPolicyId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "expiresAt": "2026-12-31T23:59:59Z"
}
```

---

### G. Admin Control Plane: Telemetry, Circuit Breakers & Audit Events
| Method | Path | Query / Description |
| :--- | :--- | :--- |
| `GET` | `/api/admin/telemetry/stats` | Returns real-time aggregate RPS, error rates, p50/p95/p99 latencies, and uptime. |
| `GET` | `/api/admin/telemetry/circuit-breakers` | Returns real-time state (`CLOSED`, `OPEN`, `HALF_OPEN`), failure rates, and ring buffer snapshots. |
| `POST` | `/api/admin/telemetry/circuit-breakers/{serviceId}/transition` | Query param: `?state=OPEN` or `?state=CLOSED` or `?state=HALF_OPEN`. Manually forces circuit state. |
| `GET` | `/api/admin/telemetry/health` | Returns instance health matrix across all upstream instances (`UP`, `SUSPECT`, `DOWN`). |
| `GET` | `/api/admin/audit-events` | Returns recent administrative audit log events with actor, IP, timestamp, and details. |

---

### H. Actuator, Health & Prometheus Scrape Endpoints
| Method | Path | Description |
| :--- | :--- | :--- |
| `GET` | `/actuator/health` | Spring Boot reactive liveness and readiness probe. |
| `GET` | `/actuator/prometheus` | Prometheus metrics scrape endpoint (`smartproxy_requests_total`, `smartproxy_requests_latency_seconds`, etc.). |
| `GET` | `/actuator/metrics` | Index of all active Micrometer meters. |

---

## 6. Testing & Development Commands

```powershell
# Run the entire test suite (125 unit, integration, chaos, and concurrency tests)
.\mvnw.cmd test

# Run only Chaos Failure Scenarios
.\mvnw.cmd test -Dtest=ChaosFailureScenariosTest

# Run Multi-Node Distributed Concurrency Token Bucket Races
.\mvnw.cmd test -Dtest=DistributedConcurrencyTokenBucketTest

# Run In-Memory Performance Benchmark Harness
.\mvnw.cmd test -Dtest=BenchmarkHarnessTest
```
