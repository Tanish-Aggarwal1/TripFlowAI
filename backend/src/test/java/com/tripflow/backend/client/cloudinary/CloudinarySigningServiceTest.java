package com.tripflow.backend.client.cloudinary;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CloudinarySigningServiceTest {

    private static final CloudinaryProperties PROPS = new CloudinaryProperties(
            "demo-cloud", "test-api-key", "abcd"
    );

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.ofEpochSecond(1_315_060_510L), ZoneOffset.UTC
    );

    private final CloudinarySigningService service =
            new CloudinarySigningService(PROPS, FIXED_CLOCK);

    /**
     * Canonical Cloudinary example: params {@code public_id=sample&timestamp=1315060510},
     * secret {@code abcd}. Verify the signed string matches what Cloudinary's own
     * algorithm produces.
     * <p>
     * To regenerate the expected value from a shell:
     * <pre>
     * echo -n "public_id=sample&timestamp=1315060510abcd" | sha1sum
     * </pre>
     */
    @Test
    void sign_producesExpectedSignatureForCanonicalExample() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("public_id", "sample");

        SignedUploadRequest signed = service.sign(params);

        // Independently computed: SHA-1("public_id=sample&timestamp=1315060510abcd")
        String expected = "c3470533147774275dd37996cc4d0e68fd03cd4f";
        
        assertThat(signed.signature()).isEqualTo(expected);
        assertThat(signed.timestamp()).isEqualTo(1_315_060_510L);
        assertThat(signed.cloudName()).isEqualTo("demo-cloud");
        assertThat(signed.apiKey()).isEqualTo("test-api-key");
    }

    @Test
    void sign_stripsReservedParamsBeforeSigning() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("public_id", "sample");
        params.put("file", "@/tmp/should-be-ignored.jpg");
        params.put("api_key", "should-be-ignored");
        params.put("cloud_name", "should-be-ignored");
        params.put("signature", "should-be-ignored");
        params.put("resource_type", "should-be-ignored");

        SignedUploadRequest signed = service.sign(params);

        // Same signature as the canonical test — reserved params were stripped.
        assertThat(signed.signature())
                .isEqualTo("c3470533147774275dd37996cc4d0e68fd03cd4f");
    }

    @Test
    void sign_isDeterministicForSameInputs() {
        Map<String, Object> params = Map.of("folder", "trips/42", "public_id", "hero");

        SignedUploadRequest first = service.sign(new LinkedHashMap<>(params));
        SignedUploadRequest second = service.sign(new LinkedHashMap<>(params));

        assertThat(first.signature()).isEqualTo(second.signature());
    }

    @Test
    void sign_flattensCollectionValuesWithCommaJoin() {
        // Cloudinary's convention for array-valued params (e.g. `tags`, `eager`).
        Map<String, Object> params = Map.of("tags", List.of("trip", "cover", "featured"));

        SignedUploadRequest signed = service.sign(params);

        assertThat(signed.uploadParams()).containsEntry("tags", List.of("trip", "cover", "featured"));
        // Signature must be over "tags=trip,cover,featured&timestamp=1315060510"
        String expected = CloudinarySigningService.buildStringToSign(
                Map.of("tags", List.of("trip", "cover", "featured"),
                       "timestamp", 1_315_060_510L)
        );
        assertThat(expected).isEqualTo("tags=trip,cover,featured&timestamp=1315060510");
    }

    @Test
    void sign_neverIncludesApiSecretInResponse() {
        SignedUploadRequest signed = service.sign(Map.of("public_id", "x"));

        assertThat(signed.uploadParams()).doesNotContainKey("api_secret");
        assertThat(signed.toString()).doesNotContain("abcd");
    }

    @Test
    void propertiesToString_masksSecrets() {
        assertThat(PROPS.toString())
                .doesNotContain("abcd")
                .doesNotContain("test-api-key")
                .contains("cloudName=demo-cloud");
    }

    @Test
    void sign_acceptsNullOrEmptyCallerParams() {
        assertThatCode(() -> service.sign(null)).doesNotThrowAnyException();
        assertThatCode(() -> service.sign(Map.of())).doesNotThrowAnyException();

        SignedUploadRequest signed = service.sign(null);
        assertThat(signed.uploadParams()).containsKey("timestamp");
        assertThat(signed.uploadParams()).containsKey("signature");
    }
}