package com.medha.externalmockservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Runtime-mutable knobs that control how unreliable this mock supplier behaves.
 *
 * <p>Bound from the {@code mock.chaos.*} properties at startup, but also mutated directly
 * by {@code SupplierController#updateChaosConfig} so the failure profile can be dialed up or
 * down while inventory-service is running, without restarting either service. That is what
 * lets a demo trigger the CircuitBreaker on demand.
 */
@Component
@ConfigurationProperties(prefix = "mock.chaos")
public class ChaosProperties {

    /** Probability (0.0-1.0) that any given call fails with a simulated 503. */
    private double failureRate = 0.3;

    /** Minimum artificial latency applied to every call, in milliseconds. */
    private long minDelayMs = 50;

    /** Maximum artificial latency applied to every call, in milliseconds. */
    private long maxDelayMs = 800;

    /** When true, every call fails regardless of failureRate - useful to force a demo outage. */
    private boolean forceFailure = false;

    public double getFailureRate() {
        return failureRate;
    }

    public void setFailureRate(double failureRate) {
        this.failureRate = failureRate;
    }

    public long getMinDelayMs() {
        return minDelayMs;
    }

    public void setMinDelayMs(long minDelayMs) {
        this.minDelayMs = minDelayMs;
    }

    public long getMaxDelayMs() {
        return maxDelayMs;
    }

    public void setMaxDelayMs(long maxDelayMs) {
        this.maxDelayMs = maxDelayMs;
    }

    public boolean isForceFailure() {
        return forceFailure;
    }

    public void setForceFailure(boolean forceFailure) {
        this.forceFailure = forceFailure;
    }
}
