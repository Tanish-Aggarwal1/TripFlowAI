package com.tripflow.backend.util;

import org.springframework.security.core.Authentication;

public final class AuthUtils {

    private AuthUtils() {}

    public static Long currentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user");
        }
        return Long.parseLong(authentication.getName());
    }
}