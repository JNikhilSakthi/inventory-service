package com.medha.inventoryservice.mapper;

import com.medha.inventoryservice.domain.InventoryItem;
import com.medha.inventoryservice.dto.InventoryItemRequest;
import com.medha.inventoryservice.dto.InventoryItemResponse;

public final class InventoryMapper {

    private InventoryMapper() {
    }

    public static InventoryItem toEntity(InventoryItemRequest request) {
        InventoryItem item = new InventoryItem();
        item.setSku(request.getSku());
        item.setProductName(request.getProductName());
        item.setQuantityOnHand(request.getQuantityOnHand());
        item.setReorderThreshold(request.getReorderThreshold());
        item.setUnitPrice(request.getUnitPrice());
        return item;
    }

    public static InventoryItemResponse toResponse(InventoryItem item) {
        return new InventoryItemResponse(
                item.getId(),
                item.getSku(),
                item.getProductName(),
                item.getQuantityOnHand(),
                item.getReorderThreshold(),
                item.getUnitPrice(),
                item.getQuantityOnHand() <= item.getReorderThreshold(),
                item.getLastRestockedAt(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }
}
