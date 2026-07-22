package com.medha.inventoryservice.controller;

import com.medha.inventoryservice.dto.AvailabilityResponse;
import com.medha.inventoryservice.dto.InventoryItemResponse;
import com.medha.inventoryservice.dto.SupplierStockResponseDto;
import com.medha.inventoryservice.exception.ResourceNotFoundException;
import com.medha.inventoryservice.service.AvailabilityService;
import com.medha.inventoryservice.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryController.class)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InventoryService inventoryService;

    @MockitoBean
    private AvailabilityService availabilityService;

    @Test
    void createInventoryItem_returns400_whenSkuBlank() throws Exception {
        mockMvc.perform(post("/api/inventory")
                        .contentType("application/json")
                        .content("{\"sku\": \"\", \"productName\": \"Widget\", \"unitPrice\": 9.99}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.sku").exists());
    }

    @Test
    void createInventoryItem_returns201_whenValid() throws Exception {
        InventoryItemResponse response = sampleResponse("SKU-1");
        when(inventoryService.create(org.mockito.ArgumentMatchers.any())).thenReturn(response);

        mockMvc.perform(post("/api/inventory")
                        .contentType("application/json")
                        .content("{\"sku\": \"SKU-1\", \"productName\": \"Widget\", \"quantityOnHand\": 20, "
                                + "\"reorderThreshold\": 5, \"unitPrice\": 9.99}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("SKU-1"));
    }

    @Test
    void getBySku_returns404_whenNotFound() throws Exception {
        when(inventoryService.getBySku("MISSING")).thenThrow(new ResourceNotFoundException("not found"));

        mockMvc.perform(get("/api/inventory/MISSING"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAvailability_returnsDegradedFlag_whenSupplierDown() throws Exception {
        AvailabilityResponse response = new AvailabilityResponse(
                sampleResponse("SKU-1"),
                new SupplierStockResponseDto("SKU-1", 0, -1, "FALLBACK", true, "supplier down"),
                Instant.now());
        when(availabilityService.checkAvailability("SKU-1")).thenReturn(response);

        mockMvc.perform(get("/api/inventory/SKU-1/availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplierStock.degraded").value(true))
                .andExpect(jsonPath("$.supplierStock.source").value("FALLBACK"));
    }

    private InventoryItemResponse sampleResponse(String sku) {
        return new InventoryItemResponse(
                1L, sku, "Widget", 20, 5, new BigDecimal("9.99"), false,
                Instant.now(), Instant.now(), Instant.now());
    }
}
