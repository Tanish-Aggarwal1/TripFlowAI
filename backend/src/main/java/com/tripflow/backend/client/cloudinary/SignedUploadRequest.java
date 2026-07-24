package com.tripflow.backend.client.cloudinary;

import java.util.Map;

/**
 * Payload the frontend uses to POST directly to Cloudinary's upload endpoint.
 * <p>
 * {@code uploadParams} is the full parameter map (including {@code signature},
 * {@code api_key}, {@code timestamp}, and any caller-supplied params such as
 * {@code folder} or {@code public_id}) that the client sends as multipart
 * form fields alongside the actual {@code file}.
 */
public record SignedUploadRequest(
        String cloudName,
        String apiKey,
        long timestamp,
        String signature,
        Map<String, Object> uploadParams
) {
}