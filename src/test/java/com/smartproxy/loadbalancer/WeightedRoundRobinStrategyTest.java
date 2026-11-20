package com.smartproxy.loadbalancer;

import com.smartproxy.loadbalancer.strategy.WeightedRoundRobinStrategy;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WeightedRoundRobinStrategyTest {

    private final WeightedRoundRobinStrategy strategy = new WeightedRoundRobinStrategy();

    @Test
    void shouldDistributeSmoothlyProportionalToWeights() {
        // Servers A, B, C with weights 4, 2, 1 (Total weight = 7)
        UpstreamInstance serverA = new UpstreamInstance(UUID.randomUUID(), "srv", "server-A", 8080, 4);
        UpstreamInstance serverB = new UpstreamInstance(UUID.randomUUID(), "srv", "server-B", 8080, 2);
        UpstreamInstance serverC = new UpstreamInstance(UUID.randomUUID(), "srv", "server-C", 8080, 1);

        List<UpstreamInstance> instances = List.of(serverA, serverB, serverC);

        Map<String, Integer> hitCount = new HashMap<>();
        int totalRequests = 70;

        for (int i = 0; i < totalRequests; i++) {
            UpstreamInstance chosen = strategy.select(instances, null).block();
            assertNotNull(chosen);
            hitCount.merge(chosen.getHost(), 1, Integer::sum);
        }

        // Expected counts: A=40, B=20, C=10
        assertEquals(40, hitCount.get("server-A"));
        assertEquals(20, hitCount.get("server-B"));
        assertEquals(10, hitCount.get("server-C"));
    }
}
