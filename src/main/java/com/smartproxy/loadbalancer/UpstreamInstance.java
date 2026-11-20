package com.smartproxy.loadbalancer;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a live backend server target instance with metrics and concurrency-safe health state.
 */
public class UpstreamInstance {

    private final UUID id;
    private final String serviceId;
    private final String host;
    private final int port;
    private final int weight;

    private final AtomicReference<InstanceHealthStatus> status = new AtomicReference<>(InstanceHealthStatus.UP);

    // Runtime Load Balancer Telemetry Counters
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalFailures = new AtomicLong(0);
    private final AtomicInteger consecutiveSuccesses = new AtomicInteger(0);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    // Smooth Weighted Round-Robin dynamic state
    private final AtomicInteger currentWeight = new AtomicInteger(0);

    private volatile Instant lastHealthCheck;

    public UpstreamInstance(UUID id, String serviceId, String host, int port, int weight) {
        this(id, serviceId, host, port, weight, InstanceHealthStatus.UP);
    }

    public UpstreamInstance(UUID id, String serviceId, String host, int port, int weight, InstanceHealthStatus initialStatus) {
        this.id = id != null ? id : UUID.randomUUID();
        this.serviceId = serviceId;
        this.host = host;
        this.port = port;
        this.weight = Math.max(1, weight);
        this.status.set(initialStatus != null ? initialStatus : InstanceHealthStatus.DOWN);
    }

    public UUID getId() { return id; }
    public String getServiceId() { return serviceId; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public int getWeight() { return weight; }

    public InstanceHealthStatus getStatus() { return status.get(); }
    public void setStatus(InstanceHealthStatus newStatus) { this.status.set(newStatus); }
    public boolean compareAndSetStatus(InstanceHealthStatus expect, InstanceHealthStatus update) {
        return this.status.compareAndSet(expect, update);
    }

    public AtomicInteger getActiveConnections() { return activeConnections; }
    public AtomicLong getTotalRequests() { return totalRequests; }
    public AtomicLong getTotalFailures() { return totalFailures; }
    public AtomicInteger getConsecutiveSuccesses() { return consecutiveSuccesses; }
    public AtomicInteger getConsecutiveFailures() { return consecutiveFailures; }
    public AtomicInteger getCurrentWeight() { return currentWeight; }

    public Instant getLastHealthCheck() { return lastHealthCheck; }
    public void setLastHealthCheck(Instant lastHealthCheck) { this.lastHealthCheck = lastHealthCheck; }

    public int incrementActiveConnections() {
        totalRequests.incrementAndGet();
        return activeConnections.incrementAndGet();
    }

    public int decrementActiveConnections() {
        return activeConnections.updateAndGet(c -> Math.max(0, c - 1));
    }

    public void recordSuccess() {
        consecutiveSuccesses.incrementAndGet();
        consecutiveFailures.set(0);
    }

    public void recordFailure() {
        totalFailures.incrementAndGet();
        consecutiveFailures.incrementAndGet();
        consecutiveSuccesses.set(0);
    }

    public URI getUri() {
        return URI.create("http://" + host + ":" + port);
    }

    public boolean isAvailable() {
        return status.get() == InstanceHealthStatus.UP;
    }
}
