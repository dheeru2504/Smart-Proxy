package com.smartproxy.storage.repository;

import com.smartproxy.storage.entity.ApiClientEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiClientRepository extends JpaRepository<ApiClientEntity, UUID> {
    Optional<ApiClientEntity> findByClientId(String clientId);
    Optional<ApiClientEntity> findByKeyHash(String keyHash);
    boolean existsByClientId(String clientId);
}
