package com.tripflow.backend.exception;

import java.io.IOException;

/**
 * Thrown mid-read by {@code RequestSizeLimitFilter}'s wrapped input stream once a request
 * body exceeds the global byte cap (SCRUM-417) — this is the backstop for a body that lies
 * about (or omits) its {@code Content-Length}, e.g. via chunked transfer encoding, since the
 * filter's upfront header check alone can't catch that case. Surfaces to Jackson as an
 * {@link IOException}, which Spring wraps as {@code HttpMessageNotReadableException};
 * {@link GlobalExceptionHandler#handleMalformedJson} unwraps it back out to a 413.
 */
public class PayloadTooLargeException extends IOException {
    public PayloadTooLargeException(String message) {
        super(message);
    }
}
