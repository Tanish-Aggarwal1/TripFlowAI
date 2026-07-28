package com.tripflow.backend.client.ors;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * GeoJSON FeatureCollection response from /v2/directions/{profile}/geojson.
 * Only the fields we consume are mapped.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrsDirectionsResponse(List<Feature> features) {

	@JsonIgnoreProperties(ignoreUnknown = true)
    public record Feature(Geometry geometry, Properties properties) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Geometry(String type, List<List<Double>> coordinates) {}

    /**
     * segments is one entry per leg between consecutive waypoints (SCRUM-244a),
     * ORS's own per-leg breakdown of the aggregate summary — used by
     * ItineraryScheduler to assign a plannedTime to each stop. The single-arg
     * constructor exists so existing call sites that only care about summary
     * (older tests) don't need updating; Jackson still uses the canonical
     * (both-args) constructor when segments is present in the response body.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Properties(Summary summary, List<Segment> segments) {
        public Properties(Summary summary) {
            this(summary, null);
        }
    }

    /** distance in metres, duration in seconds */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Summary(Double distance, Double duration) {}

    /** One leg between consecutive waypoints; distance in metres, duration in seconds. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Segment(Double distance, Double duration) {}
}
