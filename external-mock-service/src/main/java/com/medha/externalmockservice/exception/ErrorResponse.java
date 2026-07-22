package com.medha.externalmockservice.exception;

import java.time.Instant;

/** Uniform error body returned by the mock service's global exception handler. */
public record ErrorResponse(Instant timestamp, int status, String error, String message, String path) {
}
