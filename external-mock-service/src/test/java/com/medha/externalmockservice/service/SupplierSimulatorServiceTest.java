package com.medha.externalmockservice.service;

import com.medha.externalmockservice.config.ChaosProperties;
import com.medha.externalmockservice.dto.StockResponse;
import com.medha.externalmockservice.exception.SupplierUnavailableException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupplierSimulatorServiceTest {

    @Test
    void getStock_returnsResult_whenFailureRateIsZero() {
        ChaosProperties props = new ChaosProperties();
        props.setFailureRate(0.0);
        props.setMinDelayMs(0);
        props.setMaxDelayMs(0);
        SupplierSimulatorService service = new SupplierSimulatorService(props);

        StockResponse response = service.getStock("SKU-1");

        assertEquals("SKU-1", response.sku());
        assertTrue(response.availableUnits() > 0);
        assertTrue(response.leadTimeDays() > 0);
    }

    @Test
    void getStock_throws_whenFailureRateIsOne() {
        ChaosProperties props = new ChaosProperties();
        props.setFailureRate(1.0);
        props.setMinDelayMs(0);
        props.setMaxDelayMs(0);
        SupplierSimulatorService service = new SupplierSimulatorService(props);

        assertThrows(SupplierUnavailableException.class, () -> service.getStock("SKU-2"));
    }

    @Test
    void getStock_throws_whenForceFailureEnabled() {
        ChaosProperties props = new ChaosProperties();
        props.setFailureRate(0.0);
        props.setForceFailure(true);
        props.setMinDelayMs(0);
        props.setMaxDelayMs(0);
        SupplierSimulatorService service = new SupplierSimulatorService(props);

        assertThrows(SupplierUnavailableException.class, () -> service.getStock("SKU-3"));
    }

    @Test
    void getPrice_returnsPositivePrice_whenFailureRateIsZero() {
        ChaosProperties props = new ChaosProperties();
        props.setFailureRate(0.0);
        props.setMinDelayMs(0);
        props.setMaxDelayMs(0);
        SupplierSimulatorService service = new SupplierSimulatorService(props);

        var response = service.getPrice("SKU-4");

        assertEquals("SKU-4", response.sku());
        assertTrue(response.price().doubleValue() > 0);
    }
}
