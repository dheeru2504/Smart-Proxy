package com.smartproxy.loadbalancer;

import com.smartproxy.loadbalancer.strategy.PowerOfTwoChoicesStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PowerOfTwoChoicesStrategyTest {

    private final PowerOfTwoChoicesStrategy strategy = new PowerOfTwoChoicesStrategy();

    @Test
    void shouldAvoidHeavilyLoadedServer() {
        UpstreamInstance light1 = new UpstreamInstance(UUID.randomUUID(), "srv", "light1", 8080, 1);
        light1.getActiveConnections().set(1);

        UpstreamInstance light2 = new UpstreamInstance(UUID.randomUUID(), "srv", "light2", 8080, 1);
        light2.getActiveConnections().set(2);

        UpstreamInstance heavy = new UpstreamInstance(UUID.randomUUID(), "srv", "heavy", 8080, 1);
        heavy.getActiveConnections().set(500);

        List<UpstreamInstance> list = List.of(light1, light2, heavy);

        for (int i = 0; i < 20; i++) {
            UpstreamInstance selected = strategy.select(list, null).block();
            assertNotNull(selected);
            assertNotEquals("heavy", selected.getHost(), "P2C should avoid severely overloaded server");
        }
    }
}
