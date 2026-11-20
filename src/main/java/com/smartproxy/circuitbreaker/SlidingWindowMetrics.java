package com.smartproxy.circuitbreaker;

import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe fixed-size sliding window ring buffer tracking call outcomes.
 */
public class SlidingWindowMetrics {

    public enum Outcome {
        SUCCESS,
        SLOW_SUCCESS,
        FAILURE
    }

    private final int capacity;
    private final Outcome[] ringBuffer;
    private final ReentrantLock lock = new ReentrantLock();

    private int head = 0;
    private int totalCount = 0;
    private int successCount = 0;
    private int slowCount = 0;
    private int failureCount = 0;

    public SlidingWindowMetrics(int capacity) {
        this.capacity = Math.max(1, capacity);
        this.ringBuffer = new Outcome[this.capacity];
    }

    public void recordSuccess(boolean slow) {
        record(slow ? Outcome.SLOW_SUCCESS : Outcome.SUCCESS);
    }

    public void recordFailure() {
        record(Outcome.FAILURE);
    }

    private void record(Outcome outcome) {
        lock.lock();
        try {
            Outcome evicted = ringBuffer[head];
            if (evicted != null) {
                switch (evicted) {
                    case SUCCESS -> successCount--;
                    case SLOW_SUCCESS -> slowCount--;
                    case FAILURE -> failureCount--;
                }
            } else {
                totalCount++;
            }

            ringBuffer[head] = outcome;
            switch (outcome) {
                case SUCCESS -> successCount++;
                case SLOW_SUCCESS -> slowCount++;
                case FAILURE -> failureCount++;
            }

            head = (head + 1) % capacity;
        } finally {
            lock.unlock();
        }
    }

    public void reset() {
        lock.lock();
        try {
            Arrays.fill(ringBuffer, null);
            head = 0;
            totalCount = 0;
            successCount = 0;
            slowCount = 0;
            failureCount = 0;
        } finally {
            lock.unlock();
        }
    }

    public Snapshot getSnapshot() {
        lock.lock();
        try {
            double failRate = totalCount > 0 ? ((double) failureCount / totalCount) * 100.0 : 0.0;
            double slowRate = totalCount > 0 ? ((double) slowCount / totalCount) * 100.0 : 0.0;
            return new Snapshot(totalCount, successCount, slowCount, failureCount, failRate, slowRate);
        } finally {
            lock.unlock();
        }
    }

    public record Snapshot(
            int totalCalls,
            int successfulCalls,
            int slowCalls,
            int failedCalls,
            double failureRatePercentage,
            double slowCallRatePercentage
    ) {}
}
