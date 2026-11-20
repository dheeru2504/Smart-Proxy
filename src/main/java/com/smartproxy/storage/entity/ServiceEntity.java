package com.smartproxy.storage.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "services")
public class ServiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "service_id", unique = true, nullable = false, length = 100)
    private String serviceId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "load_balancer_strategy", length = 50, nullable = false)
    private String loadBalancerStrategy = "ROUND_ROBIN";

    @Column(name = "connect_timeout_ms")
    private int connectTimeoutMs = 3000;

    @Column(name = "read_timeout_ms")
    private int readTimeoutMs = 5000;

    @Column(name = "health_check_path", length = 255)
    private String healthCheckPath = "/health";

    @Column(name = "health_check_interval_sec")
    private int healthCheckIntervalSec = 10;

    @Column(name = "unhealthy_threshold")
    private int unhealthyThreshold = 3;

    @Column(name = "healthy_threshold")
    private int healthyThreshold = 2;

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ServiceInstanceEntity> instances = new ArrayList<>();

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public ServiceEntity() {}

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getServiceId() { return serviceId; }
    public void setServiceId(String serviceId) { this.serviceId = serviceId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLoadBalancerStrategy() { return loadBalancerStrategy; }
    public void setLoadBalancerStrategy(String loadBalancerStrategy) { this.loadBalancerStrategy = loadBalancerStrategy; }

    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }

    public int getReadTimeoutMs() { return readTimeoutMs; }
    public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }

    public String getHealthCheckPath() { return healthCheckPath; }
    public void setHealthCheckPath(String healthCheckPath) { this.healthCheckPath = healthCheckPath; }

    public int getHealthCheckIntervalSec() { return healthCheckIntervalSec; }
    public void setHealthCheckIntervalSec(int healthCheckIntervalSec) { this.healthCheckIntervalSec = healthCheckIntervalSec; }

    public int getUnhealthyThreshold() { return unhealthyThreshold; }
    public void setUnhealthyThreshold(int unhealthyThreshold) { this.unhealthyThreshold = unhealthyThreshold; }

    public int getHealthyThreshold() { return healthyThreshold; }
    public void setHealthyThreshold(int healthyThreshold) { this.healthyThreshold = healthyThreshold; }

    public List<ServiceInstanceEntity> getInstances() { return instances; }
    public void setInstances(List<ServiceInstanceEntity> instances) { this.instances = instances; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
