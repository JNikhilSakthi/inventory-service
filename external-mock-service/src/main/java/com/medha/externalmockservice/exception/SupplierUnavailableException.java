package com.medha.externalmockservice.exception;

/** Thrown by the simulator to represent a transient outage on the (fake) supplier side. */
public class SupplierUnavailableException extends RuntimeException {

    public SupplierUnavailableException(String message) {
        super(message);
    }
}
