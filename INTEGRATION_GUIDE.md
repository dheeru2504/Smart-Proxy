# Smart-Proxy: Internal Service Developer Integration Guide

Please see the full documentation at:
**[docs/service_integration_guide.md](file:///c:/java-projects/Smart-Proxy/docs/service_integration_guide.md)**

---

### Quick Integration Summary

1. **Register Service & Instances**:
   ```bash
   POST http://localhost:8080/api/admin/services
   POST http://localhost:8080/api/admin/services/{serviceId}/instances
   ```
2. **Register Route**:
   ```bash
   POST http://localhost:8080/api/admin/routes
   ```
3. **Headers Sent to Your Backend**:
   - `X-Request-ID`: Distributed correlation UUID (log this in all backend logs).
   - `X-Authenticated-User`: Verified username/subject.
   - `X-Authenticated-Tenant`: Multi-tenant organization identifier.
   - `X-Authenticated-Roles`: Comma-separated verified roles (`ROLE_USER,ROLE_ADMIN`).
   - `X-Forwarded-For`: Original client IP.
4. **Health Check Requirement**:
   - Expose `GET /health` returning `200 OK` in `<2000ms`.
5. **Idempotency for Retries**:
   - Send `Idempotency-Key` header for safe `POST`/`PATCH` retries.
