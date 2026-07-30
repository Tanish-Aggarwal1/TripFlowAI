package com.tripflow.backend.dto;

import java.util.Map;

/**
 * API-layer mirror of {@code CloudinarySigningService}'s signed payload.
 * Exists so controllers never depend on the {@code client.cloudinary}
 * package directly (see ArchitectureTest: controllers must go through
 * the service layer for external API clients).
 */
public record PhotoSignatureResponse(
        String cloudName,
        String apiKey,
        long timestamp,
        String signature,
        Map<String, Object> uploadParams
) {}