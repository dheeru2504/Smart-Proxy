package com.smartproxy.storage.repository;

import com.smartproxy.storage.entity.ServiceInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ServiceInstanceRepository extends JpaRepository<ServiceInstanceEntity, UUID> {
    List<ServiceInstanceEntity> findByServiceId(UUID serviceId);
}
