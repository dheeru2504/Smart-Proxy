package com.smartproxy.storage.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "routes")
public class RouteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "route_id", unique = true, nullable = false, length = 100)
    private String routeId;

    @Column(name = "path_pattern", nullable = false, length = 255)
    private String pathPattern;

    @Column(length = 100)
    private String methods = "*";

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "service_id")
    private ServiceEntity service;

    @Column(name = "strip_prefix_count")
    private int stripPrefixCount = 0;

    @Column(name = "auth_required")
    private boolean authRequired = false;

    @Column(name = "connect_timeout_ms")
    private int connectTimeoutMs = 3000;

    @Column(name = "response_timeout_ms")
    private int responseTimeoutMs = 10000;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "rate_limit_policy_id")
    private RateLimitPolicyEntity rateLimitPolicy;

    @Column
    private int priority = 0;

    @Column(nullable = false)
    private boolean enabled = true;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public RouteEntity() {}

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getRouteId() { return routeId; }
    public void setRouteId(String routeId) { this.routeId = routeId; }

    public String getPathPattern() { return pathPattern; }
    public void setPathPattern(String pathPattern) { this.pathPattern = pathPattern; }

    public String getMethods() { return methods; }
    public void setMethods(String methods) { this.methods = methods; }

    public ServiceEntity getService() { return service; }
    public void setService(ServiceEntity service) { this.service = service; }

    public int getStripPrefixCount() { return stripPrefixCount; }
    public void setStripPrefixCount(int stripPrefixCount) { this.stripPrefixCount = stripPrefixCount; }

    public boolean isAuthRequired() { return authRequired; }
    public void setAuthRequired(boolean authRequired) { this.authRequired = authRequired; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public int getResponseTimeoutMs() { return responseTimeoutMs; }
    public void setResponseTimeoutMs(int responseTimeoutMs) { this.responseTimeoutMs = responseTimeoutMs; }

    public RateLimitPolicyEntity getRateLimitPolicy() { return rateLimitPolicy; }
    public void setRateLimitPolicy(RateLimitPolicyEntity rateLimitPolicy) { this.rateLimitPolicy = rateLimitPolicy; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
