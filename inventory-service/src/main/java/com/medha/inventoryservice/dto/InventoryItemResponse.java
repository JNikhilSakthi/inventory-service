package com.medha.inventoryservice.dto;

import java.math.BigDecimal;
import java.time.Instant;

/** Read model for an inventory item, including a derived low-stock flag. */
public record InventoryItemResponse(
        Long id,
        String sku,
        String productName,
        int quantityOnHand,
        int reorderThreshold,
        BigDecimal unitPrice,
        boolean belowReorderThreshold,
        Instant lastRestockedAt,
        Instant createdAt,
        Instant updatedAt) {
}
