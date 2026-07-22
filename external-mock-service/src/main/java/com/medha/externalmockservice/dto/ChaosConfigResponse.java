package com.medha.externalmockservice.dto;

/** Current chaos configuration, returned after a read or an update. */
public record ChaosConfigResponse(double failureRate, long minDelayMs, long maxDelayMs, boolean forceFailure) {
}
