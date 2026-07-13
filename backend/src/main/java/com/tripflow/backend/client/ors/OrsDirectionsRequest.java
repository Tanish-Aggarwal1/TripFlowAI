package com.tripflow.backend.client.ors;

import java.util.List;

public record OrsDirectionsRequest(
		List<List<Double>> coordinates
) {
    public static OrsDirectionsRequest of(List<double[]> lonLatPairs) {
        return new OrsDirectionsRequest(
                lonLatPairs.stream().map(p -> List.of(p[0], p[1])).toList());
    }
}
