package com.smartproxy.loadbalancer;

import com.smartproxy.loadbalancer.strategy.RoundRobinStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RoundRobinStrategyTest {

    private final RoundRobinStrategy strategy = new RoundRobinStrategy();

    @Test
    void shouldDistributeSequentiallyAcrossHealthyInstances() {
        UpstreamInstance inst1 = new UpstreamInstance(UUID.randomUUID(), "srv", "10.0.0.1", 8080, 1);
        UpstreamInstance inst2 = new UpstreamInstance(UUID.randomUUID(), "srv", "10.0.0.2", 8080, 1);
        UpstreamInstance inst3 = new UpstreamInstance(UUID.randomUUID(), "srv", "10.0.0.3", 8080, 1);

        List<UpstreamInstance> list = List.of(inst1, inst2, inst3);

        assertEquals("10.0.0.1", strategy.select(list, null).block().getHost());
        assertEquals("10.0.0.2", strategy.select(list, null).block().getHost());
        assertEquals("10.0.0.3", strategy.select(list, null).block().getHost());
        assertEquals("10.0.0.1", strategy.select(list, null).block().getHost());
    }

    @Test
    void shouldSkipUnhealthyInstances() {
        UpstreamInstance inst1 = new UpstreamInstance(UUID.randomUUID(), "srv", "10.0.0.1", 8080, 1);
        UpstreamInstance inst2 = new UpstreamInstance(UUID.randomUUID(), "srv", "10.0.0.2", 8080, 1);
        inst2.setStatus(InstanceHealthStatus.DOWN);
        UpstreamInstance inst3 = new UpstreamInstance(UUID.randomUUID(), "srv", "10.0.0.3", 8080, 1);

        List<UpstreamInstance> list = List.of(inst1, inst2, inst3);

        for (int i = 0; i < 6; i++) {
            UpstreamInstance selected = strategy.select(list, null).block();
            assertNotNull(selected);
            assertNotEquals("10.0.0.2", selected.getHost());
        }
    }
}
