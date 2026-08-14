package com.tripflow.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Refresh-token lifetime and cookie delivery, bound from app.refresh-token.* .
 *
 * <p>The lifetime is fixed from issuance rather than sliding (D-07): rotation already
 * re-issues on every refresh, so an active session survives indefinitely and this value
 * really bounds "how long may a user be away before re-login" — which a stolen-but-unused
 * token must not be able to extend.
 */
@ConfigurationProperties(prefix = "app.refresh-token")
public record RefreshTokenProperties(
        int expirationDays,
        boolean cookieSecure,
        String cookieSameSite,
        String cookieName,
        String cookiePath) {

    public RefreshTokenProperties {
        if (expirationDays <= 0) {
            throw new IllegalStateException(
                    "app.refresh-token.expiration-days must be positive — check REFRESH_TOKEN_EXPIRY_DAYS");
        }
        // Browsers silently drop a SameSite=None cookie that isn't Secure, and the only symptom
        // is a refresh cookie that never comes back. Fail at startup instead.
        if ("None".equalsIgnoreCase(cookieSameSite) && !cookieSecure) {
            throw new IllegalStateException(
                    "app.refresh-token.cookie-same-site=None requires cookie-secure=true — "
                            + "browsers reject that pairing outright");
        }
    }
}
