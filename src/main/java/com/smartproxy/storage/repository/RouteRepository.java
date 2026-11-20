package com.smartproxy.storage.repository;

import com.smartproxy.storage.entity.RouteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RouteRepository extends JpaRepository<RouteEntity, UUID> {
    Optional<RouteEntity> findByRouteId(String routeId);
    boolean existsByRouteId(String routeId);
    List<RouteEntity> findByEnabledTrue();
}
