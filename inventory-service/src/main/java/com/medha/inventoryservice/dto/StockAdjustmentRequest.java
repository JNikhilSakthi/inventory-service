package com.medha.inventoryservice.dto;

import jakarta.validation.constraints.NotNull;

/** Request payload to increment or decrement quantityOnHand for a SKU. */
public class StockAdjustmentRequest {

    @NotNull(message = "delta is required")
    private Integer delta;

    private String reason;

    public StockAdjustmentRequest() {
    }

    public Integer getDelta() {
        return delta;
    }

    public void setDelta(Integer delta) {
        this.delta = delta;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
