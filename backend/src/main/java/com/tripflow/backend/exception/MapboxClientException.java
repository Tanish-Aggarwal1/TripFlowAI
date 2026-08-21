package com.tripflow.backend.exception;

/**
 * Raised when the Mapbox Static Images API is unreachable, times out, or returns an error.
 * Translated to 502 Bad Gateway by GlobalExceptionHandler — but PdfExportService catches this
 * internally and degrades to a map-less PDF, so the GlobalExceptionHandler mapping is a safety
 * net for any future caller, not the primary path.
 */
public class MapboxClientException extends RuntimeException {

	public MapboxClientException(String message) {
		super(message);
	}

	public MapboxClientException(String message, Throwable cause) {
		super(message, cause);
	}
}
