package com.medha.inventoryservice.service;

import com.medha.inventoryservice.dto.AvailabilityResponse;
import com.medha.inventoryservice.dto.InventoryItemResponse;
import com.medha.inventoryservice.dto.SupplierStockResponseDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates a single "availability" view for a SKU: local quantity from MySQL plus a
 * live supplier stock check. The supplier call is already guarded end-to-end by Resilience4j
 * inside {@link SupplierClient}; the extra {@code get(3, SECONDS)} timeout here is a second,
 * outer safety net in case the CompletableFuture itself never completes for an unexpected
 * reason (defense in depth, not a substitute for the TimeLimiter).
 */
@Service
public class AvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityService.class);

    private final InventoryService inventoryService;
    private final SupplierClient supplierClient;

    public AvailabilityService(InventoryService inventoryService, SupplierClient supplierClient) {
        this.inventoryService = inventoryService;
        this.supplierClient = supplierClient;
    }

    public AvailabilityResponse checkAvailability(String sku) {
        InventoryItemResponse local = inventoryService.getBySku(sku);

        SupplierStockResponseDto supplierStock;
        try {
            supplierStock = supplierClient.checkSupplierStock(sku).get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Supplier availability check failed unexpectedly for sku={}: {}", sku, e.getMessage());
            supplierStock = new SupplierStockResponseDto(
                    sku, 0, -1, "FALLBACK", true, "Supplier call failed: " + e.getMessage());
        }

        return new AvailabilityResponse(local, supplierStock, Instant.now());
    }
}
