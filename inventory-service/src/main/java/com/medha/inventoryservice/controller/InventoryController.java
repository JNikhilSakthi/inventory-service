package com.medha.inventoryservice.controller;

import com.medha.inventoryservice.dto.AvailabilityResponse;
import com.medha.inventoryservice.dto.InventoryItemRequest;
import com.medha.inventoryservice.dto.InventoryItemResponse;
import com.medha.inventoryservice.dto.StockAdjustmentRequest;
import com.medha.inventoryservice.service.AvailabilityService;
import com.medha.inventoryservice.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;
    private final AvailabilityService availabilityService;

    public InventoryController(InventoryService inventoryService, AvailabilityService availabilityService) {
        this.inventoryService = inventoryService;
        this.availabilityService = availabilityService;
    }

    @GetMapping
    public List<InventoryItemResponse> getAll() {
        return inventoryService.getAll();
    }

    @GetMapping("/{sku}")
    public InventoryItemResponse getBySku(@PathVariable String sku) {
        return inventoryService.getBySku(sku);
    }

    @PostMapping
    public ResponseEntity<InventoryItemResponse> create(@Valid @RequestBody InventoryItemRequest request) {
        InventoryItemResponse created = inventoryService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{sku}")
    public InventoryItemResponse update(@PathVariable String sku, @Valid @RequestBody InventoryItemRequest request) {
        return inventoryService.update(sku, request);
    }

    @DeleteMapping("/{sku}")
    public ResponseEntity<Void> delete(@PathVariable String sku) {
        inventoryService.delete(sku);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{sku}/adjust")
    public InventoryItemResponse adjust(@PathVariable String sku, @Valid @RequestBody StockAdjustmentRequest request) {
        return inventoryService.adjustQuantity(sku, request);
    }

    /**
     * Combines local inventory with a real-time supplier check. When the supplier is down,
     * the response still returns 200 with {@code supplierStock.degraded = true} - a degraded
     * read beats a failed one.
     */
    @GetMapping("/{sku}/availability")
    public AvailabilityResponse getAvailability(@PathVariable String sku) {
        return availabilityService.checkAvailability(sku);
    }
}
