package com.tripflow.backend.client.ors;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * GeoJSON FeatureCollection response from /v2/directions/{profile}/geojson.
 * Only the fields we consume are mapped.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrsDirectionsResponse() {

	@JsonIgnoreProperties(ignoreUnknown = true)
    public record Feature(Geometry geometry, Properties properties) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Geometry(String type, List<List<Double>> coordinates) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Properties(Summary summary) {}

    /** distance in metres, duration in seconds */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Summary(Double distance, Double duration) {}
}
