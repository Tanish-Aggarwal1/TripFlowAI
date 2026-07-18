package com.tripflow.backend.exception;

public class InsufficientStopsException extends RuntimeException {
    public InsufficientStopsException(String message) {
        super(message);
    }
}