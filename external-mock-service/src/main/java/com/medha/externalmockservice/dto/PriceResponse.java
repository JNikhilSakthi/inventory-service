package com.medha.externalmockservice.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Simulated supplier price quote for a SKU. */
public record PriceResponse(String sku, BigDecimal price, Instant checkedAt) {
}
