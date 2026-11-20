package com.smartproxy.storage.repository;

import com.smartproxy.storage.entity.ServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServiceRepository extends JpaRepository<ServiceEntity, UUID> {
    Optional<ServiceEntity> findByServiceId(String serviceId);
    boolean existsByServiceId(String serviceId);
}
