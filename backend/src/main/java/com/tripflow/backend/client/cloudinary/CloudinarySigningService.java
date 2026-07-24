package com.tripflow.backend.client.cloudinary;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Signs Cloudinary upload requests server-side so the client can upload
 * binaries directly to Cloudinary without proxying through our backend.
 * <p>
 * Algorithm (per Cloudinary docs):
 * <ol>
 *   <li>Take the caller's params, remove reserved keys
 *       ({@code file}, {@code cloud_name}, {@code resource_type},
 *       {@code api_key}, {@code signature}).</li>
 *   <li>Inject a {@code timestamp} (epoch seconds).</li>
 *   <li>Sort the remaining params alphabetically by key.</li>
 *   <li>Join as {@code key1=value1&key2=value2...}.</li>
 *   <li>Append the API secret.</li>
 *   <li>SHA-1 hash the result, hex-encode (lowercase).</li>
 * </ol>
 *
 * @see <a href="https://cloudinary.com/documentation/signatures">Cloudinary
 *      signatures documentation</a>
 */
@Service
public class CloudinarySigningService {

    /** Reserved Cloudinary params that must NOT be part of the signature. */
    private static final java.util.Set<String> UNSIGNED_KEYS = java.util.Set.of(
            "file", "cloud_name", "resource_type", "api_key", "signature"
    );

    private final CloudinaryProperties properties;
    private final Clock clock;

    public CloudinarySigningService(CloudinaryProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Signs the given upload params and returns the payload the client
     * should POST to Cloudinary.
     *
     * @param callerParams params the caller wants signed (e.g. {@code folder},
     *                     {@code public_id}, {@code eager}). May be empty.
     * @return signed request payload (never contains the API secret)
     */
    public SignedUploadRequest sign(Map<String, Object> callerParams) {
        Map<String, Object> params = callerParams == null
                ? new HashMap<>()
                : new HashMap<>(callerParams);
        UNSIGNED_KEYS.forEach(params::remove);

        long timestamp = clock.instant().getEpochSecond();
        params.put("timestamp", timestamp);

        String stringToSign = buildStringToSign(params);
        String signature = sha1Hex(stringToSign + properties.apiSecret());

        // Response payload — preserve sorted order for readability;
        // NEVER include api_secret.
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

    /**
     * Builds the canonical {@code key=value&key=value} string over
     * alphabetically-sorted, signature-eligible params.
     */
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
            // SHA-1 is guaranteed by every JVM per the JDK spec.
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }
}