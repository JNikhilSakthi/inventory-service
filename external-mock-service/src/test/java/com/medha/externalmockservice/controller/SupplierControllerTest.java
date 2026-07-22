package com.medha.externalmockservice.controller;

import com.medha.externalmockservice.config.ChaosProperties;
import com.medha.externalmockservice.dto.StockResponse;
import com.medha.externalmockservice.exception.SupplierUnavailableException;
import com.medha.externalmockservice.service.SupplierSimulatorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SupplierController.class)
class SupplierControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SupplierSimulatorService simulatorService;

    @MockBean
    private ChaosProperties chaosProperties;

    @Test
    void getStock_returnsOk_withPayload() throws Exception {
        when(simulatorService.getStock("SKU-1")).thenReturn(new StockResponse("SKU-1", 42, 3, Instant.now()));

        mockMvc.perform(get("/api/supplier/stock/SKU-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-1"))
                .andExpect(jsonPath("$.availableUnits").value(42));
    }

    @Test
    void getStock_returns503_whenSupplierUnavailable() throws Exception {
        when(simulatorService.getStock(anyString())).thenThrow(new SupplierUnavailableException("simulated outage"));

        mockMvc.perform(get("/api/supplier/stock/SKU-2"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void updateChaosConfig_returns400_whenFailureRateOutOfRange() throws Exception {
        mockMvc.perform(post("/api/supplier/chaos")
                        .contentType("application/json")
                        .content("{\"failureRate\": 1.5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateChaosConfig_appliesValues_whenValid() throws Exception {
        when(chaosProperties.getFailureRate()).thenReturn(0.9);
        when(chaosProperties.getMinDelayMs()).thenReturn(100L);
        when(chaosProperties.getMaxDelayMs()).thenReturn(200L);
        when(chaosProperties.isForceFailure()).thenReturn(false);

        mockMvc.perform(post("/api/supplier/chaos")
                        .contentType("application/json")
                        .content("{\"failureRate\": 0.9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failureRate").value(0.9));
    }
}
