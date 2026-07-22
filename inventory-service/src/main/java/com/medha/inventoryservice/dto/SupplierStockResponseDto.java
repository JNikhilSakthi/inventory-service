package com.medha.inventoryservice.dto;

/**
 * Outcome of a supplier stock check, whether it came from a live call ({@code source =
 * "LIVE"}) or from the Resilience4j fallback ({@code source = "FALLBACK"}, {@code degraded =
 * true}) after the CircuitBreaker/Retry/RateLimiter/Bulkhead/TimeLimiter chain gave up.
 */
public record SupplierStockResponseDto(
        String sku,
        int availableUnits,
        int leadTimeDays,
        String source,
        boolean degraded,
        String message) {
}
