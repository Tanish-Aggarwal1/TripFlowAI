package com.tripflow.backend.client.ors;

import java.util.List;

/**
 * Request body for POST /optimization (VROOM engine).
 * Location arrays are [longitude, latitude].
 */
public record OrsOptimizationRequest(
		List<Job> jobs,
        List<Vehicle> vehicles
) {
    public record Job(long id, List<Double> location) {}

    public record Vehicle(long id, String profile, List<Double> start, List<Double> end) {}
}