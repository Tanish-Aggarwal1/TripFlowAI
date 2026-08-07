package com.tripflow.backend.exception;

/**
 * Raised when a stop-photo URL/public-id doesn't match the configured Cloudinary
 * cloud, meaning it didn't come from the signed-upload flow it's supposed to
 * follow. Translated to 400 Bad Request.
 */
public class InvalidPhotoUrlException extends RuntimeException {

    public InvalidPhotoUrlException(String message) {
        super(message);
    }
}
