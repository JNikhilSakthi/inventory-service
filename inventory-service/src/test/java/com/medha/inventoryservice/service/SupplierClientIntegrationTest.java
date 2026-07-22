package com.medha.inventoryservice.service;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.medha.inventoryservice.dto.SupplierStockResponseDto;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the full Resilience4j chain on {@link SupplierClient} against a real HTTP
 * server (WireMock) instead of mocking RestTemplate directly - this is what proves the
 * CircuitBreaker/Retry/TimeLimiter/fallback wiring in application-test.yml actually works,
 * not just that the Java code compiles.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class SupplierClientIntegrationTest {

    private static WireMockServer wireMockServer;

    @Autowired
    private SupplierClient supplierClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        registry.add("supplier.base-url", wireMockServer::baseUrl);
    }

    @AfterAll
    static void tearDown() {
        wireMockServer.stop();
    }

    @BeforeEach
    void resetState() {
        wireMockServer.resetAll();
        circuitBreakerRegistry.circuitBreaker("supplierService").reset();
    }

    @Test
    void checkSupplierStock_returnsLiveData_whenSupplierHealthy() throws Exception {
        wireMockServer.stubFor(get(urlPathMatching("/api/supplier/stock/.*"))
                .willReturn(okJson("{\"sku\":\"SKU-1\",\"availableUnits\":50,\"leadTimeDays\":2,"
                        + "\"checkedAt\":\"2026-01-01T00:00:00Z\"}")));

        SupplierStockResponseDto response = supplierClient.checkSupplierStock("SKU-1").get(3, TimeUnit.SECONDS);

        assertEquals("LIVE", response.source());
        assertTrue(!response.degraded());
        assertEquals(50, response.availableUnits());
    }

    @Test
    void checkSupplierStock_fallsBack_whenSupplierReturns500Repeatedly() throws Exception {
        wireMockServer.stubFor(get(urlPathMatching("/api/supplier/stock/.*"))
                .willReturn(aResponse().withStatus(500)));

        SupplierStockResponseDto response = supplierClient.checkSupplierStock("SKU-2").get(3, TimeUnit.SECONDS);

        assertEquals("FALLBACK", response.source());
        assertTrue(response.degraded());
    }

    @Test
    void checkSupplierStock_fallsBack_whenSupplierTimesOut() throws Exception {
        wireMockServer.stubFor(get(urlPathMatching("/api/supplier/stock/.*"))
                .willReturn(aResponse().withFixedDelay(3000).withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"sku\":\"SKU-4\",\"availableUnits\":10,\"leadTimeDays\":1,"
                                + "\"checkedAt\":\"2026-01-01T00:00:00Z\"}")));

        SupplierStockResponseDto response = supplierClient.checkSupplierStock("SKU-4").get(5, TimeUnit.SECONDS);

        assertEquals("FALLBACK", response.source());
        assertTrue(response.degraded());
    }

    @Test
    void circuitBreaker_opensAfterRepeatedFailures() throws Exception {
        wireMockServer.stubFor(get(urlPathMatching("/api/supplier/stock/.*"))
                .willReturn(aResponse().withStatus(500)));

        // application-test.yml drops minimumNumberOfCalls to 4; drive well past that so the
        // breaker has enough recorded failures to open regardless of retry/aspect ordering.
        for (int i = 0; i < 10; i++) {
            supplierClient.checkSupplierStock("SKU-3").get(3, TimeUnit.SECONDS);
        }

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("supplierService");
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }
}
