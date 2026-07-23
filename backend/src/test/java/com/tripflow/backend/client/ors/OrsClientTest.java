package com.tripflow.backend.client.ors;


import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import org.springframework.http.HttpStatus;

import com.tripflow.backend.exception.OrsRateLimitException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.tripflow.backend.exception.OrsClientException;

public class OrsClientTest {
	private static final String BASE_URL = "https://ors.test";

    private MockRestServiceServer server;
    private OrsClient orsClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "test-key")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        server = MockRestServiceServer.bindTo(builder).build();
        orsClient = new OrsClient(builder.build());
    }

    @Test
    void getDirections_success_mapsSummaryAndGeometry() {
        String body = """
                {
                  "features": [{
                    "geometry": { "type": "LineString",
                                  "coordinates": [[-79.9, 45.0], [-79.6, 44.5]] },
                    "properties": { "summary": { "distance": 61234.5, "duration": 2712.3 } }
                  }]
                }
                """;

        server.expect(requestTo(BASE_URL + "/v2/directions/driving-car/geojson"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "test-key"))
                .andExpect(jsonPath("$.coordinates[0][0]").value(-79.9))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        OrsDirectionsRequest request = OrsDirectionsRequest.of(List.of(
                new double[]{-79.9, 45.0},
                new double[]{-79.6, 44.5}));

        OrsDirectionsResponse response = orsClient.getDirections(request);

        assertThat(response.features()).hasSize(1);
        assertThat(response.features().get(0).properties().summary().distance()).isEqualTo(61234.5);
        assertThat(response.features().get(0).geometry().coordinates()).hasSize(2);
        server.verify();
    }

    @Test
    void getDirections_serverError_throwsOrsClientException() {
        server.expect(requestTo(BASE_URL + "/v2/directions/driving-car/geojson"))
                .andRespond(withServerError());

        OrsDirectionsRequest request = OrsDirectionsRequest.of(List.of(new double[]{-79.9, 45.0}));

        assertThatThrownBy(() -> orsClient.getDirections(request))
                .isInstanceOf(OrsClientException.class);
    }

    @Test
    void optimize_success_returnsOrderedSteps() {
        String body = """
                {
                  "code": 0,
                  "summary": { "cost": 100.0, "duration": 3600.0 },
                  "routes": [{
                    "vehicle": 1, "duration": 3600.0,
                    "steps": [
                      { "type": "start", "location": [-79.4, 43.7] },
                      { "type": "job", "job": 2, "location": [-79.6, 44.5] },
                      { "type": "job", "job": 1, "location": [-79.9, 45.0] },
                      { "type": "end", "location": [-79.4, 43.7] }
                    ]
                  }]
                }
                """;

        server.expect(requestTo(BASE_URL + "/optimization"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        OrsOptimizationRequest request = new OrsOptimizationRequest(
                List.of(new OrsOptimizationRequest.Job(1, List.of(-79.9, 45.0)),
                        new OrsOptimizationRequest.Job(2, List.of(-79.6, 44.5))),
                List.of(new OrsOptimizationRequest.Vehicle(1, "driving-car",
                        List.of(-79.4, 43.7), List.of(-79.4, 43.7))));

        OrsOptimizationResponse response = orsClient.optimize(request);

        assertThat(response.code()).isZero();
        assertThat(response.routes().get(0).steps())
                .extracting(OrsOptimizationResponse.Step::job)
                .containsExactly(null, 2L, 1L, null); // start, job 2, job 1, end
        server.verify();
    }

    @Test
    void optimize_nonZeroCode_throwsOrsClientException() {
        server.expect(requestTo(BASE_URL + "/optimization"))
                .andRespond(withSuccess("{ \"code\": 3 }", MediaType.APPLICATION_JSON));

        OrsOptimizationRequest request = new OrsOptimizationRequest(List.of(), List.of());

        assertThatThrownBy(() -> orsClient.optimize(request))
                .isInstanceOf(OrsClientException.class)
                .hasMessageContaining("code 3");
    }
    
    @Test
    void optimize_emptyRoutes_throwsOrsClientException() {
        server.expect(requestTo(BASE_URL + "/optimization"))
                .andRespond(withSuccess("""
                        { "code": 0, "summary": { "cost": 0.0, "duration": 0.0 }, "routes": [] }
                        """, MediaType.APPLICATION_JSON));

        OrsOptimizationRequest request = new OrsOptimizationRequest(
                List.of(new OrsOptimizationRequest.Job(1, List.of(-79.9, 45.0))),
                List.of(new OrsOptimizationRequest.Vehicle(1, "driving-car",
                        List.of(-79.4, 43.7), List.of(-79.4, 43.7))));

        assertThatThrownBy(() -> orsClient.optimize(request))
                .isInstanceOf(OrsClientException.class)
                .hasMessageContaining("no routes");
        server.verify();
    }

    @Test
    void optimize_tooManyRequests_throwsOrsRateLimitException() {
        server.expect(requestTo(BASE_URL + "/optimization"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .body("{\"error\":\"quota exceeded\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        OrsOptimizationRequest request = new OrsOptimizationRequest(
                List.of(new OrsOptimizationRequest.Job(1, List.of(-79.9, 45.0))),
                List.of(new OrsOptimizationRequest.Vehicle(1, "driving-car",
                        List.of(-79.4, 43.7), List.of(-79.4, 43.7))));

        assertThatThrownBy(() -> orsClient.optimize(request))
                .isInstanceOf(OrsRateLimitException.class);
        server.verify();
    }
}
