package com.medha.externalmockservice.dto;

import java.time.Instant;

/** Simulated stock levels for a SKU, as returned by the supplier's stock endpoint. */
public record StockResponse(String sku, int availableUnits, int leadTimeDays, Instant checkedAt) {
}
