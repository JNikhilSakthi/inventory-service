package com.medha.inventoryservice.service;

import com.medha.inventoryservice.dto.SupplierStockPayload;
import com.medha.inventoryservice.dto.SupplierStockResponseDto;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.CompletableFuture;

/**
 * The star of this project: a single outbound call to external-mock-service, wrapped with
 * every Resilience4j module configured under the {@code supplierService} instance name in
 * application.yml.
 *
 * <p>Annotation stack, applied to one method:
 * <ul>
 *   <li>{@link CircuitBreaker} - stops calling a supplier that is already failing, so we fail
 *       fast instead of piling up slow/broken requests.</li>
 *   <li>{@link Retry} - re-attempts calls that fail with transient network/timeout errors
 *       only (see {@code resilience4j.retry.instances.supplierService.retryExceptions}); a
 *       plain HTTP 500 is treated as non-transient and is not retried.</li>
 *   <li>{@link RateLimiter} - caps how many supplier calls inventory-service issues per
 *       second, protecting the (fake) supplier from being hammered by our own retries.</li>
 *   <li>{@link Bulkhead} (type = THREADPOOL) - isolates supplier calls onto their own bounded
 *       thread pool/queue so a slow supplier cannot exhaust the servlet container's threads.</li>
 *   <li>{@link TimeLimiter} - bounds the total time the caller waits for the
 *       CompletableFuture, independent of the RestTemplate's own read timeout.</li>
 * </ul>
 *
 * <p>The method must return {@code CompletableFuture} because {@code @Bulkhead(type =
 * THREADPOOL)} and {@code @TimeLimiter} both operate on asynchronous execution.
 */
@Component
public class SupplierClient {

    private static final Logger log = LoggerFactory.getLogger(SupplierClient.class);
    private static final String SERVICE_NAME = "supplierService";

    private final RestTemplate restTemplate;
    private final String supplierBaseUrl;

    public SupplierClient(RestTemplate restTemplate, @Value("${supplier.base-url}") String supplierBaseUrl) {
        this.restTemplate = restTemplate;
        this.supplierBaseUrl = supplierBaseUrl;
    }

    @CircuitBreaker(name = SERVICE_NAME, fallbackMethod = "fallbackCheckStock")
    @RateLimiter(name = SERVICE_NAME)
    @Retry(name = SERVICE_NAME)
    @Bulkhead(name = SERVICE_NAME, type = Bulkhead.Type.THREADPOOL)
    @TimeLimiter(name = SERVICE_NAME)
    public CompletableFuture<SupplierStockResponseDto> checkSupplierStock(String sku) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Calling external supplier service for sku={}", sku);
            SupplierStockPayload payload = restTemplate.getForObject(
                    supplierBaseUrl + "/api/supplier/stock/{sku}", SupplierStockPayload.class, sku);
            if (payload == null) {
                throw new SupplierClientException("Empty response from supplier service for sku " + sku);
            }
            return new SupplierStockResponseDto(
                    payload.sku(), payload.availableUnits(), payload.leadTimeDays(), "LIVE", false,
                    "Live data from supplier");
        });
    }

    /**
     * Fallback signature must mirror the guarded method's parameters plus a trailing
     * {@link Throwable}. Invoked whenever the CircuitBreaker is OPEN, or when Retry exhausts
     * its attempts, or when RateLimiter/Bulkhead/TimeLimiter reject or time out the call.
     */
    @SuppressWarnings("unused")
    private CompletableFuture<SupplierStockResponseDto> fallbackCheckStock(String sku, Throwable throwable) {
        log.warn("Falling back for sku={} due to {}: {}", sku, throwable.getClass().getSimpleName(), throwable.getMessage());
        return CompletableFuture.completedFuture(new SupplierStockResponseDto(
                sku, 0, -1, "FALLBACK", true,
                "Supplier unavailable (" + throwable.getClass().getSimpleName() + "), showing local inventory only"));
    }
}
