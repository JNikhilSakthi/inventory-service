package com.medha.inventoryservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for inventory-service.
 *
 * <p>Owns inventory records in MySQL and exposes a live-availability endpoint that combines
 * that local data with a real-time call to the external-mock-service supplier. The outbound
 * call is the focal point of this project: it is wrapped with Resilience4j's CircuitBreaker,
 * Retry, RateLimiter, ThreadPoolBulkhead and TimeLimiter so that a flaky or slow supplier
 * degrades gracefully instead of taking inventory-service down with it.
 */
@SpringBootApplication
public class InventoryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryServiceApplication.class, args);
    }
}
