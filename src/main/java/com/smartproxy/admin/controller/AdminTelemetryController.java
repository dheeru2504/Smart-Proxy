package com.smartproxy.admin.controller;

import com.smartproxy.admin.service.ConfigurationSyncService;
import com.smartproxy.admin.snapshot.GatewayConfigSnapshot;
import com.smartproxy.circuitbreaker.CircuitBreaker;
import com.smartproxy.circuitbreaker.CircuitBreakerRegistry;
import com.smartproxy.circuitbreaker.CircuitState;
import com.smartproxy.core.routing.RouteRegistry;
import com.smartproxy.loadbalancer.UpstreamInstance;
import com.smartproxy.loadbalancer.UpstreamPoolRegistry;
import com.smartproxy.telemetry.GatewayMetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.net.InetAddress;
import java.util.*;

/**
 * Controller exposing real telemetry, metrics, health status, and circuit breaker controls for the dashboard.
 */
@RestController
@RequestMapping("/api/admin/telemetry")
public class AdminTelemetryController {

    private final RouteRegistry routeRegistry;
    private final UpstreamPoolRegistry upstreamPoolRegistry;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final ConfigurationSyncService syncService;
    private final GatewayMetricsService metricsService;

    public AdminTelemetryController(RouteRegistry routeRegistry,
                                    UpstreamPoolRegistry upstreamPoolRegistry,
                                    CircuitBreakerRegistry circuitBreakerRegistry,
                                    ConfigurationSyncService syncService,
                                    GatewayMetricsService metricsService) {
        this.routeRegistry = routeRegistry;
        this.upstreamPoolRegistry = upstreamPoolRegistry;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.syncService = syncService;
        this.metricsService = metricsService;
    }

    @GetMapping("/overview")
    public Mono<ResponseEntity<Map<String, Object>>> getOverview() {
        return Mono.fromCallable(() -> {
            GatewayConfigSnapshot snapshot = syncService.getActiveSnapshot();
            int routeCount = routeRegistry.getSnapshot().size();
            Map<String, List<UpstreamInstance>> pools = upstreamPoolRegistry.getAllPools();

            int totalInstances = 0;
            int healthyInstances = 0;
            for (List<UpstreamInstance> list : pools.values()) {
                totalInstances += list.size();
                for (UpstreamInstance inst : list) {
                    if (inst.getStatus() == com.smartproxy.loadbalancer.InstanceHealthStatus.UP) {
                        healthyInstances++;
                    }
                }
            }

            Map<String, Object> data = new HashMap<>();
            data.put("activeVersion", snapshot.version());
            data.put("propagationLagMs", snapshot.getPropagationLagMs());
            data.put("checksum", snapshot.checksum() != null ? snapshot.checksum() : "N/A");
            data.put("routeCount", routeCount);
            data.put("serviceCount", pools.size());
            data.put("totalInstances", totalInstances);
            data.put("healthyInstances", healthyInstances);
            data.put("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);

            return ResponseEntity.ok(data);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/circuit-breakers")
    public Mono<ResponseEntity<List<Map<String, Object>>>> getCircuitBreakers() {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> list = new ArrayList<>();
            circuitBreakerRegistry.getAll().forEach((name, cb) -> {
                Map<String, Object> item = new HashMap<>();
                item.put("name", name);
                item.put("state", cb.getState().name());
                item.put("config", cb.getConfig());
                list.add(item);
            });
            return ResponseEntity.ok(list);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/circuit-breakers/{name}/transition")
    public Mono<ResponseEntity<Map<String, Object>>> transitionCircuitBreaker(
            @PathVariable String name,
            @RequestParam CircuitState state) {
        return Mono.fromCallable(() -> {
            CircuitBreaker cb = circuitBreakerRegistry.getOrCreate(name);
            cb.transitionTo(state);
            return ResponseEntity.ok(Map.<String, Object>of(
                    "name", name,
                    "state", cb.getState().name(),
                    "message", "State transitioned to " + state
            ));
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/health/instances")
    public Mono<ResponseEntity<List<Map<String, Object>>>> getHealthInstances() {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> result = new ArrayList<>();
            Map<String, List<UpstreamInstance>> pools = upstreamPoolRegistry.getAllPools();

            pools.forEach((serviceId, instances) -> {
                for (UpstreamInstance inst : instances) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", inst.getId().toString());
                    map.put("serviceId", serviceId);
                    map.put("host", inst.getHost());
                    map.put("port", inst.getPort());
                    map.put("weight", inst.getWeight());
                    map.put("status", inst.getStatus().name());
                    map.put("activeConnections", inst.getActiveConnections().get());
                    map.put("totalRequests", inst.getTotalRequests().get());
                    map.put("totalFailures", inst.getTotalFailures().get());
                    map.put("consecutiveSuccesses", inst.getConsecutiveSuccesses().get());
                    map.put("consecutiveFailures", inst.getConsecutiveFailures().get());
                    result.add(map);
                }
            });

            return ResponseEntity.ok(result);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/nodes")
    public Mono<ResponseEntity<List<Map<String, Object>>>> getGatewayNodes() {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> nodes = new ArrayList<>();
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            long heapUsedMb = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
            long heapMaxMb = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024);

            String hostName = "gateway-node-1";
            String hostAddress = "127.0.0.1";
            try {
                InetAddress local = InetAddress.getLocalHost();
                hostName = local.getHostName();
                hostAddress = local.getHostAddress();
            } catch (Exception ignored) {}

            Map<String, Object> node = new HashMap<>();
            node.put("nodeId", "gw-" + hostName.toLowerCase());
            node.put("host", hostAddress);
            node.put("port", 8080);
            node.put("status", "HEALTHY");
            node.put("version", syncService.getActiveSnapshot().version());
            node.put("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
            node.put("heapUsedMb", heapUsedMb);
            node.put("heapMaxMb", heapMaxMb);
            node.put("activeThreads", Thread.activeCount());

            nodes.add(node);
            return ResponseEntity.ok(nodes);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
