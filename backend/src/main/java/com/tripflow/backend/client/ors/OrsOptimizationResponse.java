package com.tripflow.backend.client.ors;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response from POST /optimization. code 0 = success.
 * steps[].type: "start" | "job" | "end"; steps[].job holds the job id for type "job".
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrsOptimizationResponse() {

	@JsonIgnoreProperties(ignoreUnknown = true)
    public record Summary(Double cost, Double duration) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Route(Long vehicle, Double duration, List<Step> steps) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Step(String type, Long job, List<Double> location) {}
}
