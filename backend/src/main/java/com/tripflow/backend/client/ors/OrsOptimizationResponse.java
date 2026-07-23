package com.tripflow.backend.client.ors;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response from POST /optimization. code 0 = success.
 * steps[].type: "start" | "job" | "end"; steps[].job holds the job id for type "job".
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrsOptimizationResponse(Integer code, Summary summary, List<Route> routes, List<Unassigned> unassigned) {
	// Convenience constructor so existing 3-arg call sites (tests) keep compiling.
	public OrsOptimizationResponse(Integer code, Summary summary, List<Route> routes) {
		this(code, summary, routes, List.of());
	}
	@JsonIgnoreProperties(ignoreUnknown = true)
    public record Summary(Double cost, Double duration) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Route(Long vehicle, Double duration, List<Step> steps) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Step(String type, Long job, List<Double> location) {}
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Unassigned(Long id, List<Double> location) {}
    
    
}
