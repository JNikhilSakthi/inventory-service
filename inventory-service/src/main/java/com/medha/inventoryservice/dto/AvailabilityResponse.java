package com.medha.inventoryservice.dto;

import java.time.Instant;

/** Combined view of local inventory data and the (possibly degraded) live supplier check. */
public record AvailabilityResponse(
        InventoryItemResponse inventoryItem,
        SupplierStockResponseDto supplierStock,
        Instant checkedAt) {
}
