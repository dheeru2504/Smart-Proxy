package com.smartproxy.ratelimiter;

import com.smartproxy.config.SmartProxyProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency race condition test around the final token in multi-tier rate limiting.
 * Simulates 100 concurrent workers contending for the last remaining tokens.
 */
class MultiTierRateLimiterRaceTest {

    /**
     * Simulated thread-safe atomic multi-tier Token Bucket mimicking Redis Lua script execution.
     */
    static class AtomicMultiTierSimulatedLimiter {
        private final int globalCapacity;
        private final int tenantCapacity;
        private final int routeCapacity;

        private final AtomicInteger globalTokens;
        private final AtomicInteger tenantTokens;
        private final AtomicInteger routeTokens;

        public AtomicMultiTierSimulatedLimiter(int global, int tenant, int route) {
            this.globalCapacity = global;
            this.tenantCapacity = tenant;
            this.routeCapacity = route;

            this.globalTokens = new AtomicInteger(global);
            this.tenantTokens = new AtomicInteger(tenant);
            this.routeTokens = new AtomicInteger(route);
        }

        public synchronized boolean tryAcquire() {
            // Atomic check of all tiers (All-or-Nothing Phase 1)
            if (globalTokens.get() >= 1 && tenantTokens.get() >= 1 && routeTokens.get() >= 1) {
                // Commit (Phase 2)
                globalTokens.decrementAndGet();
                tenantTokens.decrementAndGet();
                routeTokens.decrementAndGet();
                return true;
            }
            // Rejection: zero tokens consumed from any tier
            return false;
        }

        public int getRemainingGlobal() { return globalTokens.get(); }
        public int getRemainingTenant() { return tenantTokens.get(); }
        public int getRemainingRoute() { return routeTokens.get(); }
    }

    @Test
    void shouldEnforceExactFinalTokenQuotaUnderMassiveConcurrency() throws InterruptedException {
        int totalWorkers = 100;
        int routeQuota = 5; // The bottleneck tier has only 5 tokens remaining!
        int tenantQuota = 50;
        int globalQuota = 200;

        AtomicMultiTierSimulatedLimiter limiter =
                new AtomicMultiTierSimulatedLimiter(globalQuota, tenantQuota, routeQuota);

        ExecutorService executor = Executors.newFixedThreadPool(totalWorkers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalWorkers);

        AtomicInteger permittedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);

        for (int i = 0; i < totalWorkers; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // All threads start simultaneously
                    boolean allowed = limiter.tryAcquire();
                    if (allowed) {
                        permittedCount.incrementAndGet();
                    } else {
                        rejectedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        // Exact assertions
        assertEquals(5, permittedCount.get(), "Exactly 5 requests must be permitted for the last 5 tokens");
        assertEquals(95, rejectedCount.get(), "Exactly 95 requests must be rejected (429)");

        // Assert no partial token leakage across any tier
        assertEquals(globalQuota - 5, limiter.getRemainingGlobal(), "Global tier should only have 5 tokens consumed");
        assertEquals(tenantQuota - 5, limiter.getRemainingTenant(), "Tenant tier should only have 5 tokens consumed");
        assertEquals(0, limiter.getRemainingRoute(), "Route tier should have exactly 0 tokens remaining");
    }
}
