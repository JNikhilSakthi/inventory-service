package com.medha.inventoryservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    /**
     * Connect/read timeouts here are intentionally tight and layered underneath Resilience4j's
     * own TimeLimiter: this bounds how long a single HTTP attempt can block the calling thread,
     * while the TimeLimiter instance ("supplierService") bounds the whole async call including
     * any retries.
     */
    @Bean
    public RestTemplate restTemplate(
            RestTemplateBuilder builder,
            @Value("${supplier.connect-timeout-ms:1000}") long connectTimeoutMs,
            @Value("${supplier.read-timeout-ms:2000}") long readTimeoutMs) {
        return builder
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .readTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }
}
