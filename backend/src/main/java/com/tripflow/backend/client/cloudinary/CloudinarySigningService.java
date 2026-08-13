package com.tripflow.backend.client.cloudinary;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Component
public class CloudinarySigningService {

    private static final Set<String> UNSIGNED_KEYS = Set.of(
            "file", "cloud_name", "resource_type", "api_key", "signature"
    );

    private final CloudinaryProperties properties;
    private final Clock clock;

    public CloudinarySigningService(CloudinaryProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public SignedUploadRequest sign(Map<String, Object> callerParams) {
        Map<String, Object> params = callerParams == null
                ? new HashMap<>()
                : new HashMap<>(callerParams);
        UNSIGNED_KEYS.forEach(params::remove);

        long timestamp = clock.instant().getEpochSecond();
        params.put("timestamp", timestamp);

        String stringToSign = buildStringToSign(params);
        String signature = sha1Hex(stringToSign + properties.apiSecret());

        Map<String, Object> uploadParams = new LinkedHashMap<>(new TreeMap<>(params));
        uploadParams.put("signature", signature);
        uploadParams.put("api_key", properties.apiKey());

        return new SignedUploadRequest(
                properties.cloudName(),
                properties.apiKey(),
                timestamp,
                signature,
                uploadParams
        );
    }

    static String buildStringToSign(Map<String, Object> params) {
        return new TreeMap<>(params).entrySet().stream()
                .map(e -> e.getKey() + "=" + stringifyValue(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String stringifyValue(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
        }
        return String.valueOf(value);
    }

    private static String sha1Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }
}