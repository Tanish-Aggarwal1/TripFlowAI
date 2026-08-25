package com.tripflow.backend.client.ors;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.tripflow.backend.exception.OrsClientException;
import org.springframework.web.client.HttpClientErrorException;
import com.tripflow.backend.exception.OrsRateLimitException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Wrapper around OpenRouteService directions and optimization (VROOM) endpoints.
 * Free-tier quota is 500 requests/day — callers must not invoke this in loops.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrsClient {

	private static final String DEFAULT_PROFILE = "driving-car";

    private final RestClient orsRestClient;

    public OrsDirectionsResponse getDirections(OrsDirectionsRequest request) {
        return getDirections(request, DEFAULT_PROFILE);
    }

    public OrsDirectionsResponse getDirections(OrsDirectionsRequest request, String profile) {
        return execute(() -> {
            OrsDirectionsResponse response = orsRestClient.post()
                    .uri("/v2/directions/{profile}/geojson", profile)
                    .body(request)
                    .retrieve()
                    .body(OrsDirectionsResponse.class);

            if (response == null || response.features() == null || response.features().isEmpty()) {
                throw new OrsClientException("ORS directions returned an empty response");
            }
            if (response.features().get(0).properties() == null) {
                throw new OrsClientException("ORS directions response is missing properties on its first feature");
            }
            log.debug("ORS directions ok profile={} waypoints={}", profile, request.coordinates().size());
            return response;
        }, "OpenRouteService directions request failed");
    }

    public OrsOptimizationResponse optimize(OrsOptimizationRequest request) {
        return execute(() -> {
            OrsOptimizationResponse response = orsRestClient.post()
                    .uri("/optimization")
                    .body(request)
                    .retrieve()
                    .body(OrsOptimizationResponse.class);

            if (response == null || response.code() == null || response.code() != 0) {
                throw new OrsClientException("ORS optimization returned error code "
                        + (response == null ? "null" : response.code()));
            }
            if (response.routes() == null || response.routes().isEmpty()) {
                throw new OrsClientException("ORS optimization returned no routes for "
                        + request.jobs().size() + " job(s)");
            }
            if (response.routes().get(0).steps() == null) {
                throw new OrsClientException("ORS optimization response is missing steps on its first route");
            }
            log.debug("ORS optimization ok jobs={}", request.jobs().size());
            return response;
        }, "OpenRouteService optimization request failed");
    }

    /**
     * Shared call + exception translation for every ORS endpoint (SCRUM-221): both
     * {@code getDirections} and {@code optimize} share the same ORS account and the same
     * 500 requests/day free-tier quota, so a 429 means the same thing regardless of which
     * endpoint hits it — always translate to {@link OrsRateLimitException} (429), never let
     * one endpoint fall through to the generic {@link OrsClientException} (502) the other
     * doesn't. Centralizing this means a third ORS call added later inherits the same
     * handling automatically instead of needing its own copy.
     */
    private <T> T execute(Supplier<T> call, String failureMessage) {
        try {
            return call.get();
        } catch (HttpClientErrorException.TooManyRequests ex) {
            log.warn("ORS call rate-limited (429): {}", ex.getMessage());
            throw new OrsRateLimitException("OpenRouteService quota exceeded", ex);
        } catch (RestClientException ex) {
            log.warn("ORS call failed: {}", ex.getMessage());
            throw new OrsClientException(failureMessage, ex);
        }
    }

}
