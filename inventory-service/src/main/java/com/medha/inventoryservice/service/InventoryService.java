package com.medha.inventoryservice.service;

import com.medha.inventoryservice.domain.InventoryItem;
import com.medha.inventoryservice.dto.InventoryItemRequest;
import com.medha.inventoryservice.dto.InventoryItemResponse;
import com.medha.inventoryservice.dto.StockAdjustmentRequest;
import com.medha.inventoryservice.exception.DuplicateSkuException;
import com.medha.inventoryservice.exception.ResourceNotFoundException;
import com.medha.inventoryservice.mapper.InventoryMapper;
import com.medha.inventoryservice.repository.InventoryItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class InventoryService {

    private final InventoryItemRepository repository;

    public InventoryService(InventoryItemRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<InventoryItemResponse> getAll() {
        return repository.findAll().stream().map(InventoryMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public InventoryItemResponse getBySku(String sku) {
        return repository.findBySku(sku)
                .map(InventoryMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found for sku: " + sku));
    }

    public InventoryItemResponse create(InventoryItemRequest request) {
        if (repository.existsBySku(request.getSku())) {
            throw new DuplicateSkuException("Inventory item already exists for sku: " + request.getSku());
        }
        InventoryItem saved = repository.save(InventoryMapper.toEntity(request));
        return InventoryMapper.toResponse(saved);
    }

    public InventoryItemResponse update(String sku, InventoryItemRequest request) {
        InventoryItem existing = repository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found for sku: " + sku));
        existing.setProductName(request.getProductName());
        existing.setQuantityOnHand(request.getQuantityOnHand());
        existing.setReorderThreshold(request.getReorderThreshold());
        existing.setUnitPrice(request.getUnitPrice());
        return InventoryMapper.toResponse(repository.save(existing));
    }

    public void delete(String sku) {
        InventoryItem existing = repository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found for sku: " + sku));
        repository.delete(existing);
    }

    public InventoryItemResponse adjustQuantity(String sku, StockAdjustmentRequest request) {
        InventoryItem existing = repository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found for sku: " + sku));
        int newQuantity = existing.getQuantityOnHand() + request.getDelta();
        if (newQuantity < 0) {
            throw new IllegalArgumentException(
                    "Resulting quantity cannot be negative for sku: " + sku + " (current="
                            + existing.getQuantityOnHand() + ", delta=" + request.getDelta() + ")");
        }
        existing.setQuantityOnHand(newQuantity);
        if (request.getDelta() > 0) {
            existing.setLastRestockedAt(Instant.now());
        }
        return InventoryMapper.toResponse(repository.save(existing));
    }
}
