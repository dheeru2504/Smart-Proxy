package com.smartproxy.loadbalancer.health;

import com.smartproxy.loadbalancer.InstanceHealthStatus;
import com.smartproxy.loadbalancer.UpstreamInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class HealthStateMachineTest {

    private HealthStateMachine stateMachine;
    private UpstreamInstance instance;

    @BeforeEach
    void setUp() {
        // failureThreshold = 3, recoveryThreshold = 2
        stateMachine = new HealthStateMachine(3, 2);
        instance = new UpstreamInstance(UUID.randomUUID(), "test-service", "127.0.0.1", 8080, 1);
        instance.setStatus(InstanceHealthStatus.UP);
    }

    @Test
    void shouldTransitionUpToSuspectThenDownOnConsecutiveFailures() {
        assertEquals(InstanceHealthStatus.UP, instance.getStatus());

        // Probe 1 Failure: (1 failure -> still UP)
        stateMachine.evaluate(instance, false);
        assertEquals(InstanceHealthStatus.UP, instance.getStatus());

        // Probe 2 Failure: (2 failures >= ceil(3/2)=2 -> SUSPECT)
        stateMachine.evaluate(instance, false);
        assertEquals(InstanceHealthStatus.SUSPECT, instance.getStatus());

        // Probe 3 Failure: (3 failures >= 3 -> DOWN)
        stateMachine.evaluate(instance, false);
        assertEquals(InstanceHealthStatus.DOWN, instance.getStatus());
    }

    @Test
    void shouldRecoverFromDownToSuspectThenUpOnConsecutiveSuccesses() {
        instance.setStatus(InstanceHealthStatus.DOWN);

        // Probe 1 Success: (1 success >= 1 -> SUSPECT)
        stateMachine.evaluate(instance, true);
        assertEquals(InstanceHealthStatus.SUSPECT, instance.getStatus());

        // Probe 2 Success: (2 successes >= 2 -> UP)
        stateMachine.evaluate(instance, true);
        assertEquals(InstanceHealthStatus.UP, instance.getStatus());
    }

    @Test
    void shouldPreventFlappingOnSingleTransientFailure() {
        assertEquals(InstanceHealthStatus.UP, instance.getStatus());

        // Single transient failure
        stateMachine.evaluate(instance, false);
        assertEquals(InstanceHealthStatus.UP, instance.getStatus());

        // Next probe immediately succeeds -> resets failure counter, remains UP
        stateMachine.evaluate(instance, true);
        assertEquals(InstanceHealthStatus.UP, instance.getStatus());
        assertEquals(0, instance.getConsecutiveFailures().get());
    }

    @Test
    void shouldPreventPrematureRecoveryOnSingleSuccessWhenDown() {
        instance.setStatus(InstanceHealthStatus.DOWN);

        // 1 success transitions to SUSPECT
        stateMachine.evaluate(instance, true);
        assertEquals(InstanceHealthStatus.SUSPECT, instance.getStatus());

        // Next probe fails -> immediately returns to DOWN
        stateMachine.evaluate(instance, false);
        assertEquals(InstanceHealthStatus.DOWN, instance.getStatus());
        assertEquals(0, instance.getConsecutiveSuccesses().get());
    }
}
