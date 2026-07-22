package com.medha.inventoryservice.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers a listener on every CircuitBreaker instance so state transitions (CLOSED -&gt;
 * OPEN -&gt; HALF_OPEN -&gt; CLOSED) are logged. Resilience4j's Spring Boot starter auto-detects
 * any {@link RegistryEventConsumer} bean of the matching generic type and wires it into the
 * registry at startup - no manual registration call is needed.
 */
@Configuration
public class ResilienceEventLoggingConfig {

    private static final Logger log = LoggerFactory.getLogger(ResilienceEventLoggingConfig.class);

    @Bean
    public RegistryEventConsumer<CircuitBreaker> circuitBreakerEventConsumer() {
        return new RegistryEventConsumer<>() {
            @Override
            public void onEntryAddedEvent(EntryAddedEvent<CircuitBreaker> entryAddedEvent) {
                CircuitBreaker circuitBreaker = entryAddedEvent.getAddedEntry();
                circuitBreaker.getEventPublisher().onStateTransition(event -> log.warn(
                        "CircuitBreaker '{}' changed state: {} -> {}",
                        circuitBreaker.getName(),
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()));
            }

            @Override
            public void onEntryRemovedEvent(EntryRemovedEvent<CircuitBreaker> entryRemovedEvent) {
                // no-op: instances are static for this demo
            }

            @Override
            public void onEntryReplacedEvent(EntryReplacedEvent<CircuitBreaker> entryReplacedEvent) {
                // no-op: instances are static for this demo
            }
        };
    }
}
