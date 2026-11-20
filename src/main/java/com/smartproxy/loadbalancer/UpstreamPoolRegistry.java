package com.smartproxy.loadbalancer;

import com.smartproxy.storage.entity.ServiceEntity;
import com.smartproxy.storage.entity.ServiceInstanceEntity;
import com.smartproxy.storage.repository.ServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe in-memory pool registry holding active backend instances grouped by serviceId.
 */
@Component
public class UpstreamPoolRegistry {

    private static final Logger log = LoggerFactory.getLogger(UpstreamPoolRegistry.class);

    private final Map<String, List<UpstreamInstance>> servicePools = new ConcurrentHashMap<>();
    private final ServiceRepository serviceRepository;

    public UpstreamPoolRegistry(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Hydrating upstream server instance pools from database...");
        reloadAllFromDatabase();
    }

    public synchronized void reloadAllFromDatabase() {
        try {
            List<ServiceEntity> services = serviceRepository.findAll();
            for (ServiceEntity service : services) {
                List<UpstreamInstance> instances = new CopyOnWriteArrayList<>();
                if (service.getInstances() != null) {
                    for (ServiceInstanceEntity instEntity : service.getInstances()) {
                        UpstreamInstance inst = new UpstreamInstance(
                                instEntity.getId(),
                                service.getServiceId(),
                                instEntity.getHost(),
                                instEntity.getPort(),
                                instEntity.getWeight(),
                                InstanceHealthStatus.DOWN
                        );
                        instances.add(inst);
                    }
                }
                servicePools.put(service.getServiceId(), instances);
            }
            log.info("Hydrated {} upstream service pools.", servicePools.size());
        } catch (Exception e) {
            log.warn("Could not hydrate upstream pools from database during startup: {}", e.getMessage());
        }
    }

    public List<UpstreamInstance> getInstances(String serviceId) {
        if (serviceId == null) {
            return Collections.emptyList();
        }
        return servicePools.getOrDefault(serviceId, Collections.emptyList());
    }

    public void setPool(String serviceId, List<UpstreamInstance> instances) {
        if (serviceId == null) return;
        servicePools.put(serviceId, new CopyOnWriteArrayList<>(instances != null ? instances : Collections.emptyList()));
    }

    public void registerInstance(String serviceId, UpstreamInstance instance) {
        if (serviceId == null || instance == null) return;
        List<UpstreamInstance> pool = servicePools.computeIfAbsent(serviceId, k -> new CopyOnWriteArrayList<>());
        pool.removeIf(inst -> inst.getId().equals(instance.getId()));
        pool.add(instance);
        log.info("Registered upstream instance {}:{} to pool '{}'", instance.getHost(), instance.getPort(), serviceId);
    }

    public void unregisterInstance(String serviceId, UUID instanceId) {
        if (serviceId == null || instanceId == null) return;
        List<UpstreamInstance> pool = servicePools.get(serviceId);
        if (pool != null) {
            pool.removeIf(inst -> inst.getId().equals(instanceId));
            log.info("Unregistered upstream instance {} from pool '{}'", instanceId, serviceId);
        }
    }

    public void clearPool(String serviceId) {
        if (serviceId != null) {
            servicePools.remove(serviceId);
        }
    }

    public Map<String, List<UpstreamInstance>> getAllPools() {
        return Collections.unmodifiableMap(servicePools);
    }
}
