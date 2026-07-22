package com.medha.inventoryservice.dto;

import java.time.Instant;

/**
 * Raw JSON shape returned by external-mock-service's {@code /api/supplier/stock/{sku}}
 * endpoint. Kept separate from {@link SupplierStockResponseDto} because the latter also
 * carries inventory-service-local metadata (source, degraded, message) that has no
 * equivalent on the wire.
 */
public record SupplierStockPayload(String sku, int availableUnits, int leadTimeDays, Instant checkedAt) {
}
