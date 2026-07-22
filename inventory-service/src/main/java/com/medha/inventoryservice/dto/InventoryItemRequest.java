package com.medha.inventoryservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Request payload for creating or updating an inventory item. */
public class InventoryItemRequest {

    @NotBlank(message = "sku is required")
    @Size(max = 64, message = "sku must be at most 64 characters")
    private String sku;

    @NotBlank(message = "productName is required")
    @Size(max = 255, message = "productName must be at most 255 characters")
    private String productName;

    @PositiveOrZero(message = "quantityOnHand cannot be negative")
    private int quantityOnHand;

    @PositiveOrZero(message = "reorderThreshold cannot be negative")
    private int reorderThreshold;

    @NotNull(message = "unitPrice is required")
    @DecimalMin(value = "0.0", message = "unitPrice cannot be negative")
    private BigDecimal unitPrice;

    public InventoryItemRequest() {
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public int getQuantityOnHand() {
        return quantityOnHand;
    }

    public void setQuantityOnHand(int quantityOnHand) {
        this.quantityOnHand = quantityOnHand;
    }

    public int getReorderThreshold() {
        return reorderThreshold;
    }

    public void setReorderThreshold(int reorderThreshold) {
        this.reorderThreshold = reorderThreshold;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }
}
