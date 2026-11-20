# Smart-Proxy: Internal Service Developer Integration Guide

This guide is for internal backend service developers who are onboarding and integrating their microservices with the **Smart-Proxy Distributed API Gateway**.

---

## 1. Gateway Overview & Architecture

Smart-Proxy sits at the edge of the infrastructure as the single ingress entry point. It shields internal microservices by handling edge concerns:

```
[ Public Clients / Web / Mobile ]
               │
               ▼
   ╔═══════════════════════════════════════════════════════╗
   ║               Smart-Proxy API Gateway                 ║
   ║  - JWT / API Key Verification                         ║
   ║  - Distributed Atomic Rate Limiting (Token Bucket)    ║
   ║  - Custom Layer-7 Load Balancing                      ║
   ║  - Active/Passive Health Probing & Anti-Flapping      ║
   ║  - Reactive Circuit Breaking & Bounded Retries        ║
   ║  - Zero-Trust Identity Header Injection               ║
   ║  - SSRF Guard & Payload Limiting (10MB)               ║
   ╚═══════════════════════════════════════════════════════╝
               │
   ┌───────────┼───────────┐
   ▼           ▼           ▼
[ Order Service ] [ Payment Service ] [ Inventory Service ]
```

### What Smart-Proxy Handles For Your Service:
1. **Authentication & Authorization**: Validates client JWTs and API Keys. Cleanses client headers and injects verified claims (`X-Authenticated-User`, `X-Authenticated-Tenant`, `X-Authenticated-Roles`).
2. **Rate Limiting**: Protects your service from traffic spikes and bad actors using distributed Redis token buckets.
3. **Load Balancing & Failover**: Distributes traffic across your backend instances using Smooth Weighted Round-Robin, Least Connections, or Power of Two Choices (P2C).
4. **Resilience**: Enforces deadlines, prevents cascade failures with circuit breaking, and performs safe, bounded retries with jitter.
5. **Distributed Tracing**: Injects unique `X-Request-ID` correlation identifiers across all inbound and outbound requests.

---

## 2. Onboarding Checklist (Quick 4-Step Process)

1. [ ] **Step 1: Register Service Pool** in Smart-Proxy.
2. [ ] **Step 2: Register Backend Instances** (host, port, capacity weight).
3. [ ] **Step 3: Define Dynamic Route** (path pattern, auth rules, rate limit policy).
4. [ ] **Step 4: Implement Ingress Headers & Health Check Contract** in your code.

---

## 3. Step 1 & 2: Service & Instance Registration

You can register your service via the **Operations Dashboard** (`http://localhost:5173`) or via the **Admin REST API**:

### A. Register Your Upstream Service Pool
```bash
curl -X POST http://localhost:8080/api/admin/services \
  -H "Content-Type: application/json" \
  -H "X-Admin-Key: admin-super-secret-key-change-in-prod" \
  -d '{
    "serviceId": "order-service",
    "name": "Order Management Service",
    "loadBalancerStrategy": "ROUND_ROBIN",
    "connectTimeoutMs": 3000,
    "readTimeoutMs": 5000,
    "healthCheckPath": "/health",
    "healthCheckIntervalSec": 10,
    "healthyThreshold": 2,
    "unhealthyThreshold": 3
  }'
```

#### Available Load Balancing Strategies:
* `ROUND_ROBIN`: Standard sequential rotation across healthy instances.
* `WEIGHTED_ROUND_ROBIN`: Smooth Interleaved Weighted Round-Robin taking instance capacity weights into account.
* `LEAST_CONNECTIONS`: Dispatches requests to the instance with fewest active in-flight connections.
* `POWER_OF_TWO_CHOICES`: Selects 2 random healthy instances and picks the one with fewer active connections ($O(1)$ constant time).

---

### B. Register Your Service Instances
Register all running instances of your service using the **Service UUID** (`id`) returned from the service registration in Step 1:

```bash
# Register Instance 1 (Weight: 100) - Replace <SERVICE_UUID> with the "id" from Step 1
curl -X POST http://localhost:8080/api/admin/services/<SERVICE_UUID>/instances \
  -H "Content-Type: application/json" \
  -H "X-Admin-Key: admin-super-secret-key-change-in-prod" \
  -d '{
    "host": "order-service-node-1.internal",
    "port": 8081,
    "weight": 100
  }'

# Register Instance 2 (Higher capacity node, Weight: 200)
curl -X POST http://localhost:8080/api/admin/services/<SERVICE_UUID>/instances \
  -H "Content-Type: application/json" \
  -H "X-Admin-Key: admin-super-secret-key-change-in-prod" \
  -d '{
    "host": "order-service-node-2.internal",
    "port": 8082,
    "weight": 200
  }'
```

---

## 4. Step 3: Dynamic Route Configuration

Map public URL paths to your service pool:

```bash
curl -X POST http://localhost:8080/api/admin/routes \
  -H "Content-Type: application/json" \
  -H "X-Admin-Key: admin-super-secret-key-change-in-prod" \
  -d '{
    "routeId": "orders-v1-api",
    "pathPattern": "/api/v1/orders/**",
    "serviceId": "order-service",
    "authType": "JWT",
    "rateLimitPolicyId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "stripPrefixCount": 2,
    "connectTimeoutMs": 3000,
    "responseTimeoutMs": 5000,
    "priority": 100,
    "enabled": true
  }'
```

### Route Configuration Parameters:
| Parameter | Description |
| :--- | :--- |
| `pathPattern` | Ant-style pattern for incoming requests (e.g. `/api/v1/orders/**`). |
| `stripPrefixCount` | Number of URL segments to strip before forwarding to your service. E.g., with `stripPrefixCount: 2`, `/api/v1/orders/123` becomes `/orders/123` on your backend. |
| `authType` | `NONE` (public), `API_KEY`, or `JWT`. |
| `responseTimeoutMs` | Hard timeout deadline. If your service takes longer, Smart-Proxy immediately cancels the request and returns `504 Gateway Timeout`. |

---

## 5. Step 4: Upstream Request Contract (Headers Sent to Your Service)

> [!IMPORTANT]
> Smart-Proxy implements a **Zero-Trust Boundary**. Any client attempting to send `X-Authenticated-*` headers directly will have those headers stripped at ingress. Only Smart-Proxy's verified claims are forwarded to your service.

### Headers Injected by Smart-Proxy:

| Header Name | Type | Example | Description |
| :--- | :--- | :--- | :--- |
| `X-Request-ID` | String | `req-9f82a1c0-...` | Distributed trace correlation ID. **You MUST include this in your service logs.** |
| `X-Authenticated-User` | String | `john.doe@company.com` | Verified Subject/Username from JWT or API Client. |
| `X-Authenticated-Tenant` | String | `tenant-acme-corp` | Tenant ID for multi-tenant data isolation. |
| `X-Authenticated-Roles` | CSV | `ROLE_USER,ROLE_ADMIN` | Comma-separated list of verified user/client roles. |
| `X-Authenticated-Scopes`| CSV | `read:orders,write:orders`| Verified OAuth scopes. |
| `X-Authenticated-Client-Id` | String | `acme-partner-client` | Client ID from API Key or OAuth client. |
| `X-Forwarded-For` | IP | `203.0.113.195` | Real client IP address. |
| `X-Forwarded-Proto` | String | `https` | Original protocol (`http` or `https`). |

---

## 6. Health Check Contract

Smart-Proxy performs active health probing against all registered instances.

### Contract Requirements:
1. **Endpoint**: Your service must expose `GET /health` or `GET /actuator/health`.
2. **Status Code**: Return `HTTP 200 OK` when healthy.
3. **Response Time**: Respond within `< 2000ms`.
4. **Hysteresis & Anti-Flapping**:
   * **Unhealthy Trigger**: 3 consecutive failures $\implies$ Transition to `DOWN` (removed from active load balancing).
   * **Recovery Trigger**: 2 consecutive successes $\implies$ Transition from `DOWN` $\to$ `SUSPECT` $\to$ `UP`.
5. **Graceful Shutdown**:
   * During service deployment/pod termination, make `/health` return `HTTP 503` for 5–10 seconds before shutting down. Smart-Proxy will automatically drain traffic without dropping active client requests.

---

## 7. Idempotency & Safe Retries

Smart-Proxy implements bounded retries with exponential backoff and full jitter:

* **Safe & Idempotent Methods (`GET`, `HEAD`, `PUT`, `DELETE`, `OPTIONS`)**:
  * Automatically retried on transient network timeouts (`502 Bad Gateway`, `503 Service Unavailable`, `504 Gateway Timeout`).
* **Non-Idempotent Methods (`POST`, `PATCH`)**:
  * **By default, POST/PATCH requests are NOT retried** to prevent duplicate database inserts or double charges.
  * **Enabling POST Retries**: If your client or gateway sends the `Idempotency-Key: <unique-uuid>` header, Smart-Proxy will safely retry the request if a network blip occurs.
  * **Backend Requirement**: If you accept `Idempotency-Key`, your service should cache the idempotency key in Redis/DB to prevent duplicate processing.

---

## 8. Code Integration Examples

### A. Java / Spring Boot Controller Example
```java
package com.example.orders.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderDto> getOrder(
            @PathVariable String orderId,
            @RequestHeader(value = "X-Request-ID", required = false) String traceId,
            @RequestHeader(value = "X-Authenticated-User", required = false) String userId,
            @RequestHeader(value = "X-Authenticated-Tenant", required = false) String tenantId,
            @RequestHeader(value = "X-Authenticated-Roles", defaultValue = "") String roles) {

        log.info("[{}] Fetching order '{}' for user '{}' (tenant: '{}', roles: '{}')",
                traceId, orderId, userId, tenantId, roles);

        OrderDto order = orderService.findByIdAndTenant(orderId, tenantId);
        return ResponseEntity.ok(order);
    }
}
```

---

### B. Node.js / Express Middleware Example
```javascript
const express = require('express');
const app = express();

// Middleware to extract Smart-Proxy Zero-Trust context
app.use((req, res, next) => {
  req.context = {
    traceId: req.header('X-Request-ID') || 'unknown',
    user: req.header('X-Authenticated-User'),
    tenantId: req.header('X-Authenticated-Tenant'),
    roles: (req.header('X-Authenticated-Roles') || '').split(','),
    clientIp: req.header('X-Forwarded-For') || req.socket.remoteAddress
  };
  next();
});

// Health check endpoint required by Smart-Proxy
app.get('/health', (req, res) => {
  res.status(200).json({ status: 'UP', timestamp: new Date().toISOString() });
});

// Protected order endpoint
app.get('/orders/:id', (req, res) => {
  console.log(`[${req.context.traceId}] Tenant ${req.context.tenantId} requested order ${req.params.id}`);
  res.json({ orderId: req.params.id, status: 'PROCESSING', owner: req.context.user });
});

app.listen(8080, () => console.log('Order Service running on port 8080'));
```

---

### C. Python / FastAPI Example
```python
from fastapi import FastAPI, Header, HTTPException, status
from typing import Optional

app = FastAPI()

@app.get("/health")
def health_check():
    return {"status": "UP"}

@app.get("/orders/{order_id}")
def get_order(
    order_id: str,
    x_request_id: Optional[str] = Header(None),
    x_authenticated_user: Optional[str] = Header(None),
    x_authenticated_tenant: Optional[str] = Header(None),
    x_authenticated_roles: Optional[str] = Header("")
):
    roles = [r.strip() for r in x_authenticated_roles.split(",") if r.strip()]
    
    print(f"[{x_request_id}] User {x_authenticated_user} (Tenant: {x_authenticated_tenant}) accessing order {order_id}")
    
    return {
        "orderId": order_id,
        "tenantId": x_authenticated_tenant,
        "user": x_authenticated_user,
        "roles": roles
    }
```

---

## 9. Gateway Error Code Standard (RFC 7807)

When Smart-Proxy intercepts or rejects a request, it returns standardized RFC 7807 JSON:

```json
{
  "type": "https://smartproxy.io/errors/rate-limit-exceeded",
  "title": "Rate Limit Exceeded",
  "status": 429,
  "code": "RATE_LIMIT_EXCEEDED",
  "detail": "Rate limit exceeded for client. Replenish rate: 10 tokens/sec.",
  "instance": "/api/v1/orders/123",
  "timestamp": "2026-08-22T15:45:00.123Z",
  "traceId": "req-9f82a1c0-..."
}
```

### Common Gateway Error Status Codes:
| Status Code | Code | Reason | What Backend Should Do |
| :--- | :--- | :--- | :--- |
| `400 Bad Request` | `BAD_REQUEST` | Malformed request or invalid target URI. | Fix client payload. |
| `401 Unauthorized` | `UNAUTHORIZED` | Invalid/missing JWT token or API key. | Ensure client provides valid authentication. |
| `403 Forbidden` | `FORBIDDEN` | Insufficient roles or SSRF violation. | Verify user roles in JWT. |
| `404 Not Found` | `ROUTE_NOT_FOUND` | No route matching path. | Check route registration in gateway. |
| `413 Payload Too Large` | `PAYLOAD_TOO_LARGE`| Body exceeds 10MB limit. | Reduce payload size or upload via S3. |
| `429 Too Many Requests`| `RATE_LIMIT_EXCEEDED`| Rate limit quota exhausted. | Respect `Retry-After` header. |
| `502 Bad Gateway` | `BAD_GATEWAY` | TCP connection refused to backend. | Ensure backend service is running on registered port. |
| `503 Service Unavailable` | `CIRCUIT_BREAKER_OPEN`| Circuit breaker tripped due to consecutive backend 5xx errors. | Fix downstream errors to allow circuit recovery. |
| `504 Gateway Timeout`| `GATEWAY_TIMEOUT` | Backend exceeded `responseTimeoutMs`. | Optimize backend database queries or increase timeout. |

---

## 10. Summary & Support Contacts

* **Operations Dashboard**: `http://localhost:5173`
* **Admin REST API Base**: `http://localhost:8080/api/admin`
* **Gateway Prometheus Metrics**: `http://localhost:8080/actuator/prometheus`
* **Gateway Health**: `http://localhost:8080/actuator/health`
