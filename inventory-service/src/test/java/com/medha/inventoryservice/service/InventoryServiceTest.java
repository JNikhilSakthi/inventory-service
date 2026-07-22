package com.medha.inventoryservice.service;

import com.medha.inventoryservice.domain.InventoryItem;
import com.medha.inventoryservice.dto.InventoryItemRequest;
import com.medha.inventoryservice.dto.InventoryItemResponse;
import com.medha.inventoryservice.dto.StockAdjustmentRequest;
import com.medha.inventoryservice.exception.DuplicateSkuException;
import com.medha.inventoryservice.exception.ResourceNotFoundException;
import com.medha.inventoryservice.repository.InventoryItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryItemRepository repository;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void create_throwsDuplicateSkuException_whenSkuExists() {
        when(repository.existsBySku("SKU-1")).thenReturn(true);
        InventoryItemRequest request = sampleRequest("SKU-1");

        assertThrows(DuplicateSkuException.class, () -> inventoryService.create(request));
        verify(repository, never()).save(any());
    }

    @Test
    void create_savesItem_whenSkuIsNew() {
        when(repository.existsBySku("SKU-1")).thenReturn(false);
        when(repository.save(any(InventoryItem.class))).thenAnswer(inv -> {
            InventoryItem item = inv.getArgument(0);
            item.setId(1L);
            return item;
        });

        InventoryItemResponse response = inventoryService.create(sampleRequest("SKU-1"));

        assertEquals("SKU-1", response.sku());
        verify(repository).save(any(InventoryItem.class));
    }

    @Test
    void getBySku_throwsResourceNotFoundException_whenMissing() {
        when(repository.findBySku("MISSING")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> inventoryService.getBySku("MISSING"));
    }

    @Test
    void adjustQuantity_increasesQuantity_whenDeltaPositive() {
        InventoryItem item = sampleEntity("SKU-1", 10);
        when(repository.findBySku("SKU-1")).thenReturn(Optional.of(item));
        when(repository.save(any(InventoryItem.class))).thenAnswer(inv -> inv.getArgument(0));

        StockAdjustmentRequest adjustment = new StockAdjustmentRequest();
        adjustment.setDelta(5);

        InventoryItemResponse result = inventoryService.adjustQuantity("SKU-1", adjustment);

        assertEquals(15, result.quantityOnHand());
    }

    @Test
    void adjustQuantity_throwsIllegalArgumentException_whenResultWouldBeNegative() {
        InventoryItem item = sampleEntity("SKU-1", 3);
        when(repository.findBySku("SKU-1")).thenReturn(Optional.of(item));

        StockAdjustmentRequest adjustment = new StockAdjustmentRequest();
        adjustment.setDelta(-10);

        assertThrows(IllegalArgumentException.class, () -> inventoryService.adjustQuantity("SKU-1", adjustment));
        verify(repository, never()).save(any());
    }

    @Test
    void delete_throwsResourceNotFoundException_whenMissing() {
        when(repository.findBySku("MISSING")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> inventoryService.delete("MISSING"));
    }

    private InventoryItemRequest sampleRequest(String sku) {
        InventoryItemRequest request = new InventoryItemRequest();
        request.setSku(sku);
        request.setProductName("Widget");
        request.setQuantityOnHand(20);
        request.setReorderThreshold(5);
        request.setUnitPrice(new BigDecimal("9.99"));
        return request;
    }

    private InventoryItem sampleEntity(String sku, int quantity) {
        InventoryItem item = new InventoryItem();
        item.setId(1L);
        item.setSku(sku);
        item.setProductName("Widget");
        item.setQuantityOnHand(quantity);
        item.setReorderThreshold(5);
        item.setUnitPrice(new BigDecimal("9.99"));
        item.setLastRestockedAt(Instant.now());
        return item;
    }
}
