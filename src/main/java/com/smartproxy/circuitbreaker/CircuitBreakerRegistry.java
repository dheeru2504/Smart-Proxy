package com.smartproxy.circuitbreaker;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry holding and managing named CircuitBreaker instances.
 */
@Component
public class CircuitBreakerRegistry {

    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();

    public CircuitBreaker getOrCreate(String name) {
        return getOrCreate(name, null);
    }

    public CircuitBreaker getOrCreate(String name, CircuitBreakerConfig config) {
        if (name == null || name.isBlank()) {
            name = "default-service";
        }
        return circuitBreakers.computeIfAbsent(name, n -> new CustomCircuitBreaker(n, config));
    }

    public Map<String, CircuitBreaker> getAll() {
        return Map.copyOf(circuitBreakers);
    }

    public void clear() {
        circuitBreakers.clear();
    }
}
