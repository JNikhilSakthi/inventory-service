package com.medha.inventoryservice.dto;

import java.time.Instant;
import java.util.Map;

/** Uniform error body returned by the global exception handler. */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors) {
}
