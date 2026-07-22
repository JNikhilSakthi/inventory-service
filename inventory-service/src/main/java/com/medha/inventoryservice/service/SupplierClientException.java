package com.medha.inventoryservice.service;

/** Wraps unexpected supplier responses (e.g. an empty body) so they participate in the
 *  CircuitBreaker's recordExceptions list like any other supplier failure. */
public class SupplierClientException extends RuntimeException {

    public SupplierClientException(String message) {
        super(message);
    }
}
