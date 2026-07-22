package com.medha.externalmockservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the external-mock-service.
 *
 * <p>This service simulates a third-party supplier system that inventory-service depends on.
 * It is intentionally unreliable: its failure rate and response latency are configurable at
 * runtime through {@code /api/supplier/chaos}, which makes it possible to demonstrate
 * inventory-service's Resilience4j CircuitBreaker opening, retrying, rate-limiting and
 * falling back in a fully local, deterministic way.
 */
@SpringBootApplication
public class ExternalMockServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExternalMockServiceApplication.class, args);
    }
}
