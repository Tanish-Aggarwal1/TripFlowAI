package com.tripflow.backend.client.cloudinary;

import java.util.Map;

public record SignedUploadRequest(
        String cloudName,
        String apiKey,
        long timestamp,
        String signature,
        Map<String, Object> uploadParams
) {
}