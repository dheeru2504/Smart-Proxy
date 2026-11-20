package com.smartproxy.loadbalancer;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrencyTrackingTest {

    private final LoadBalancerService lbService = new LoadBalancerService(
            new UpstreamPoolRegistry(null),
            new LoadBalancerStrategyFactory(List.of())
    );

    @Test
    void shouldAccuratelyTrackActiveConnectionsUnderHighConcurrency() throws InterruptedException {
        UpstreamInstance instance = new UpstreamInstance(UUID.randomUUID(), "srv", "10.0.0.1", 8080, 1);

        int totalThreads = 100;
        int requestsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch latch = new CountDownLatch(totalThreads * requestsPerThread);

        for (int i = 0; i < totalThreads * requestsPerThread; i++) {
            executor.submit(() -> {
                Mono<String> simulatedWork = Mono.delay(Duration.ofMillis(2))
                        .thenReturn("OK");

                lbService.trackExecution(instance, simulatedWork)
                        .doFinally(signal -> latch.countDown())
                        .subscribe();
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        assertTrue(completed, "All requests should complete");

        executor.shutdown();

        // Give reactor threads a few milliseconds to process all doFinally callbacks
        Thread.sleep(100);

        // Active connections must return exactly to 0
        assertEquals(0, instance.getActiveConnections().get(), "Active connections must return to 0 after all executions");
        assertEquals(totalThreads * requestsPerThread, instance.getTotalRequests().get());
        assertEquals(0, instance.getTotalFailures().get());
    }
}
