package com.smartproxy.storage.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rate_limit_policies")
public class RateLimitPolicyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "policy_name", unique = true, nullable = false, length = 100)
    private String policyName;

    @Column(nullable = false, length = 50)
    private String algorithm = "TOKEN_BUCKET";

    @Column(name = "replenish_rate", nullable = false)
    private int replenishRate = 100;

    @Column(name = "burst_capacity", nullable = false)
    private int burstCapacity = 200;

    @Column(name = "window_duration_sec", nullable = false)
    private int windowDurationSec = 1;

    @Column(name = "limit_by", nullable = false, length = 50)
    private String limitBy = "IP";

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public RateLimitPolicyEntity() {}

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getPolicyName() { return policyName; }
    public void setPolicyName(String policyName) { this.policyName = policyName; }

    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }

    public int getReplenishRate() { return replenishRate; }
    public void setReplenishRate(int replenishRate) { this.replenishRate = replenishRate; }

    public int getBurstCapacity() { return burstCapacity; }
    public void setBurstCapacity(int burstCapacity) { this.burstCapacity = burstCapacity; }

    public int getWindowDurationSec() { return windowDurationSec; }
    public void setWindowDurationSec(int windowDurationSec) { this.windowDurationSec = windowDurationSec; }

    public String getLimitBy() { return limitBy; }
    public void setLimitBy(String limitBy) { this.limitBy = limitBy; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
