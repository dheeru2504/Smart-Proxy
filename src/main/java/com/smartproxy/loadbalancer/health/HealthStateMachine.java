package com.smartproxy.loadbalancer.health;

import com.smartproxy.loadbalancer.InstanceHealthStatus;
import com.smartproxy.loadbalancer.UpstreamInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Thread-safe 3-state health state machine with anti-flapping hysteresis.
 */
public class HealthStateMachine {

    private static final Logger log = LoggerFactory.getLogger(HealthStateMachine.class);

    private final int failureThreshold;
    private final int recoveryThreshold;

    public HealthStateMachine(int failureThreshold, int recoveryThreshold) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.recoveryThreshold = Math.max(1, recoveryThreshold);
    }

    /**
     * Evaluates a probe result and updates the instance health status atomically.
     *
     * @param instance     target server instance
     * @param probeSuccess true if probe succeeded (HTTP 2xx/3xx), false otherwise
     * @return updated InstanceHealthStatus
     */
    public InstanceHealthStatus evaluate(UpstreamInstance instance, boolean probeSuccess) {
        if (instance == null) {
            return InstanceHealthStatus.DOWN;
        }

        instance.setLastHealthCheck(Instant.now());
        InstanceHealthStatus currentStatus = instance.getStatus();

        if (probeSuccess) {
            int successes = instance.getConsecutiveSuccesses().incrementAndGet();
            instance.getConsecutiveFailures().set(0);

            switch (currentStatus) {
                case DOWN -> {
                    if (successes >= recoveryThreshold) {
                        transition(instance, InstanceHealthStatus.DOWN, InstanceHealthStatus.UP,
                                "Consecutive recovery threshold reached: " + successes + "/" + recoveryThreshold);
                    } else if (successes >= Math.max(1, recoveryThreshold / 2)) {
                        transition(instance, InstanceHealthStatus.DOWN, InstanceHealthStatus.SUSPECT,
                                "Partial recovery detected: " + successes + "/" + recoveryThreshold);
                    }
                }
                case SUSPECT -> {
                    if (successes >= recoveryThreshold) {
                        transition(instance, InstanceHealthStatus.SUSPECT, InstanceHealthStatus.UP,
                                "Full recovery confirmed: " + successes + "/" + recoveryThreshold);
                    }
                }
                case UP, DRAINING -> {
                    // Stays UP or DRAINING
                }
            }
        } else {
            int failures = instance.getConsecutiveFailures().incrementAndGet();
            instance.getConsecutiveSuccesses().set(0);

            switch (currentStatus) {
                case UP -> {
                    if (failures >= failureThreshold) {
                        transition(instance, InstanceHealthStatus.UP, InstanceHealthStatus.DOWN,
                                "Consecutive failure threshold exceeded: " + failures + "/" + failureThreshold);
                    } else if (failures >= Math.max(1, (failureThreshold + 1) / 2)) {
                        transition(instance, InstanceHealthStatus.UP, InstanceHealthStatus.SUSPECT,
                                "Transient failure suspected: " + failures + "/" + failureThreshold);
                    }
                }
                case SUSPECT -> {
                    // Any failure while in SUSPECT demotes immediately back to DOWN
                    transition(instance, InstanceHealthStatus.SUSPECT, InstanceHealthStatus.DOWN,
                            "Probe failed while in SUSPECT state (" + failures + " failures)");
                }
                case DOWN, DRAINING -> {
                    // Stays DOWN or DRAINING
                }
            }
        }

        return instance.getStatus();
    }

    private void transition(UpstreamInstance instance, InstanceHealthStatus expected, InstanceHealthStatus next, String reason) {
        boolean changed = instance.compareAndSetStatus(expected, next);
        if (changed) {
            log.warn("Upstream instance {}:{} [{}] transitioned from {} -> {}. Reason: {}",
                    instance.getHost(), instance.getPort(), instance.getServiceId(), expected, next, reason);
        }
    }
}
