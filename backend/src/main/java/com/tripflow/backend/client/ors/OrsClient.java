package com.tripflow.backend.client.ors;
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
        try {
            OrsDirectionsResponse response = orsRestClient.post()
                    .uri("/v2/directions/{profile}/geojson", profile)
                    .body(request)
                    .retrieve()
                    .body(OrsDirectionsResponse.class);

            if (response == null || response.features() == null || response.features().isEmpty()) {
                throw new OrsClientException("ORS directions returned an empty response");
            }
            log.debug("ORS directions ok profile={} waypoints={}", profile, request.coordinates().size());
            return response;
        } catch (RestClientException ex) {
            log.warn("ORS directions call failed profile={}: {}", profile, ex.getMessage());
            throw new OrsClientException("OpenRouteService directions request failed", ex);
        }
    }

    public OrsOptimizationResponse optimize(OrsOptimizationRequest request) {
        try {
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
            log.debug("ORS optimization ok jobs={}", request.jobs().size());
            return response;
        } catch (HttpClientErrorException.TooManyRequests ex) {
            log.warn("ORS optimization rate-limited (429): {}", ex.getMessage());
            throw new OrsRateLimitException("OpenRouteService optimization quota exceeded", ex);
        } catch (RestClientException ex) {
            log.warn("ORS optimization call failed: {}", ex.getMessage());
            throw new OrsClientException("OpenRouteService optimization request failed", ex);
        }
    }

}
