package com.smartproxy.loadbalancer;

import com.smartproxy.loadbalancer.strategy.LeastConnectionsStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LeastConnectionsStrategyTest {

    private final LeastConnectionsStrategy strategy = new LeastConnectionsStrategy();

    @Test
    void shouldPickServerWithLowestActiveConnections() {
        UpstreamInstance busy1 = new UpstreamInstance(UUID.randomUUID(), "srv", "server-busy1", 8080, 1);
        busy1.getActiveConnections().set(10);

        UpstreamInstance idle = new UpstreamInstance(UUID.randomUUID(), "srv", "server-idle", 8080, 1);
        idle.getActiveConnections().set(2);

        UpstreamInstance busy2 = new UpstreamInstance(UUID.randomUUID(), "srv", "server-busy2", 8080, 1);
        busy2.getActiveConnections().set(8);

        List<UpstreamInstance> list = List.of(busy1, idle, busy2);

        UpstreamInstance chosen = strategy.select(list, null).block();
        assertNotNull(chosen);
        assertEquals("server-idle", chosen.getHost());
    }
}
