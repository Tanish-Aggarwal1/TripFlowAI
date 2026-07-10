package com.tripflow.backend.util;

import org.springframework.security.core.Authentication;

public final class AuthUtils {

    private AuthUtils() {}

    /**
     * Dev principal resolution. SINGLE SWAP POINT for the JWT filter:
     * replace the body with
     *   return ((UserPrincipal) authentication.getPrincipal()).getId();
     * once the filter populates a real principal.
     */
    public static Long currentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user");
        }
        return Long.parseLong(authentication.getPrincipal().toString());
    }
}