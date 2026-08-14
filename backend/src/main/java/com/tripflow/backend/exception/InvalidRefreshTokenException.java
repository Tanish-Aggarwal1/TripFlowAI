package com.tripflow.backend.exception;

/** Presented refresh token is absent, unknown, already redeemed, revoked, or expired.
 * The message is fixed and generic so the response never distinguishes those cases. */
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException() {
        super("Invalid or expired refresh token");
    }
}
