package com.medha.externalmockservice.controller;

import com.medha.externalmockservice.config.ChaosProperties;
import com.medha.externalmockservice.dto.ChaosConfigRequest;
import com.medha.externalmockservice.dto.ChaosConfigResponse;
import com.medha.externalmockservice.dto.PriceResponse;
import com.medha.externalmockservice.dto.StockResponse;
import com.medha.externalmockservice.service.SupplierSimulatorService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simulated supplier API. {@code /stock} and {@code /price} are the endpoints inventory-service
 * calls; {@code /chaos} lets a demo dial the failure rate and latency up or down at runtime.
 */
@RestController
@RequestMapping("/api/supplier")
public class SupplierController {

    private final SupplierSimulatorService simulatorService;
    private final ChaosProperties chaosProperties;

    public SupplierController(SupplierSimulatorService simulatorService, ChaosProperties chaosProperties) {
        this.simulatorService = simulatorService;
        this.chaosProperties = chaosProperties;
    }

    @GetMapping("/stock/{sku}")
    public StockResponse getStock(@PathVariable String sku) {
        return simulatorService.getStock(sku);
    }

    @GetMapping("/price/{sku}")
    public PriceResponse getPrice(@PathVariable String sku) {
        return simulatorService.getPrice(sku);
    }

    @GetMapping("/chaos")
    public ChaosConfigResponse getChaosConfig() {
        return toResponse();
    }

    @PostMapping("/chaos")
    public ChaosConfigResponse updateChaosConfig(@Valid @RequestBody ChaosConfigRequest request) {
        if (request.getFailureRate() != null) {
            chaosProperties.setFailureRate(request.getFailureRate());
        }
        if (request.getMinDelayMs() != null) {
            chaosProperties.setMinDelayMs(request.getMinDelayMs());
        }
        if (request.getMaxDelayMs() != null) {
            chaosProperties.setMaxDelayMs(request.getMaxDelayMs());
        }
        if (request.getForceFailure() != null) {
            chaosProperties.setForceFailure(request.getForceFailure());
        }
        return toResponse();
    }

    private ChaosConfigResponse toResponse() {
        return new ChaosConfigResponse(
                chaosProperties.getFailureRate(),
                chaosProperties.getMinDelayMs(),
                chaosProperties.getMaxDelayMs(),
                chaosProperties.isForceFailure());
    }
}
