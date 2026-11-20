package com.smartproxy.storage.repository;

import com.smartproxy.storage.entity.RateLimitPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RateLimitPolicyRepository extends JpaRepository<RateLimitPolicyEntity, UUID> {
    Optional<RateLimitPolicyEntity> findByPolicyName(String policyName);
    boolean existsByPolicyName(String policyName);
}
