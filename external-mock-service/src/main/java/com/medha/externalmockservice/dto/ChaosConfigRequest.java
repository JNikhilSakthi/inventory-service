package com.medha.externalmockservice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Partial update payload for {@code POST /api/supplier/chaos}. Every field is optional -
 * only non-null fields are applied, so a caller can nudge a single knob (e.g. just
 * failureRate) without resending the whole configuration.
 */
public class ChaosConfigRequest {

    @DecimalMin(value = "0.0", message = "failureRate must be >= 0.0")
    @DecimalMax(value = "1.0", message = "failureRate must be <= 1.0")
    private Double failureRate;

    @PositiveOrZero(message = "minDelayMs cannot be negative")
    private Long minDelayMs;

    @PositiveOrZero(message = "maxDelayMs cannot be negative")
    private Long maxDelayMs;

    private Boolean forceFailure;

    public Double getFailureRate() {
        return failureRate;
    }

    public void setFailureRate(Double failureRate) {
        this.failureRate = failureRate;
    }

    public Long getMinDelayMs() {
        return minDelayMs;
    }

    public void setMinDelayMs(Long minDelayMs) {
        this.minDelayMs = minDelayMs;
    }

    public Long getMaxDelayMs() {
        return maxDelayMs;
    }

    public void setMaxDelayMs(Long maxDelayMs) {
        this.maxDelayMs = maxDelayMs;
    }

    public Boolean getForceFailure() {
        return forceFailure;
    }

    public void setForceFailure(Boolean forceFailure) {
        this.forceFailure = forceFailure;
    }
}
