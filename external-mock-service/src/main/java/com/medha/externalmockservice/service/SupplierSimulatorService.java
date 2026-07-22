package com.medha.externalmockservice.service;

import com.medha.externalmockservice.config.ChaosProperties;
import com.medha.externalmockservice.dto.PriceResponse;
import com.medha.externalmockservice.dto.StockResponse;
import com.medha.externalmockservice.exception.SupplierUnavailableException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Random;

/**
 * Produces deterministic-enough fake supplier data while injecting configurable latency
 * and failures, driven by {@link ChaosProperties}.
 *
 * <p>The failure check uses {@code Random#nextDouble()}, which returns a value in
 * {@code [0.0, 1.0)}. That makes the boundaries intentional: a failureRate of {@code 0.0}
 * can never trip (nothing is {@code < 0.0}) and a failureRate of {@code 1.0} always trips
 * (everything is {@code < 1.0}), which keeps unit tests deterministic without needing a
 * seeded Random.
 */
@Service
public class SupplierSimulatorService {

    private final ChaosProperties chaosProperties;
    private final Random random = new Random();

    public SupplierSimulatorService(ChaosProperties chaosProperties) {
        this.chaosProperties = chaosProperties;
    }

    public StockResponse getStock(String sku) {
        simulateLatency();
        maybeFail(sku);
        int availableUnits = 10 + Math.abs(sku.hashCode() % 90);
        int leadTimeDays = 1 + Math.abs(sku.hashCode() % 5);
        return new StockResponse(sku, availableUnits, leadTimeDays, Instant.now());
    }

    public PriceResponse getPrice(String sku) {
        simulateLatency();
        maybeFail(sku);
        BigDecimal price = BigDecimal.valueOf(10 + Math.abs(sku.hashCode() % 490)).setScale(2, java.math.RoundingMode.HALF_UP);
        return new PriceResponse(sku, price, Instant.now());
    }

    private void simulateLatency() {
        long min = chaosProperties.getMinDelayMs();
        long max = Math.max(min, chaosProperties.getMaxDelayMs());
        long delay = min + (max > min ? (long) (random.nextDouble() * (max - min)) : 0);
        if (delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void maybeFail(String sku) {
        if (chaosProperties.isForceFailure() || random.nextDouble() < chaosProperties.getFailureRate()) {
            throw new SupplierUnavailableException("Simulated supplier outage for sku " + sku);
        }
    }
}
