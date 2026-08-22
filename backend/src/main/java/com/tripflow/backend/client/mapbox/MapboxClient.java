package com.tripflow.backend.client.mapbox;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.tripflow.backend.exception.MapboxClientException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Wrapper around the Mapbox Static Images API — renders a route-map snapshot for the
 * PDF export (EXPORT-02 / D-03). Always off the critical path: a blank token, a request
 * over Mapbox's URL-length cap, or any HTTP failure degrades to a smaller overlay or an
 * empty result rather than throwing back into a failed PDF download.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MapboxClient {

	private static final String MAPBOX_USERNAME = "mapbox";
	// Margin under Mapbox's documented 8,192-character request URL cap.
	private static final int MAX_URL_LENGTH = 8000;
	private static final int SNAPSHOT_WIDTH = 600;
	private static final int SNAPSHOT_HEIGHT = 400;
	// Matches trip-map.component.ts's route line color (#3b82f6) for visual parity.
	private static final String MARKER_COLOR_HEX = "3b82f6";

	private final RestClient mapboxRestClient;
	private final MapboxProperties props;

	/**
	 * Renders a static snapshot of the trip's route (when {@code routeGeometryJson} is
	 * present) or plain stop pins (D-04, when the trip has never been optimized).
	 *
	 * @param routeGeometryJson the trip's stored GeoJSON LineString, or null
	 * @param stopCoordinates   {@code [longitude, latitude]} pairs, used for the marker
	 *                          fallback and as the "is there anything to render" check
	 * @return the raw image bytes, or empty when there's nothing to render, no token is
	 *         configured, or even a marker-only request would exceed the URL length cap
	 */
	public Optional<byte[]> staticSnapshot(String routeGeometryJson, List<double[]> stopCoordinates) {
		if (props.accessToken() == null || props.accessToken().isBlank()) {
			return Optional.empty();
		}
		boolean hasRoute = routeGeometryJson != null && !routeGeometryJson.isBlank();
		boolean hasStops = stopCoordinates != null && !stopCoordinates.isEmpty();
		if (!hasRoute && !hasStops) {
			return Optional.empty();
		}

		String overlay = hasRoute ? geojsonOverlay(routeGeometryJson) : markerOverlay(stopCoordinates);
		String path = requestPath(overlay);

		if ((props.baseUrl() + path).length() > MAX_URL_LENGTH) {
			log.warn("Mapbox snapshot request too long with route overlay, falling back to markers "
					+ "stopCount={}", stopCoordinates == null ? 0 : stopCoordinates.size());
			if (!hasStops) {
				return Optional.empty();
			}
			overlay = markerOverlay(stopCoordinates);
			path = requestPath(overlay);
			if ((props.baseUrl() + path).length() > MAX_URL_LENGTH) {
				log.warn("Mapbox snapshot request still too long marker-only, skipping "
						+ "stopCount={}", stopCoordinates.size());
				return Optional.empty();
			}
		}

		// Built as a URI object, not a String, so RestClient's UriBuilderFactory never sees
		// this as a template to re-encode — geojsonOverlay's URLEncoder pass is the ONLY
		// encoding pass the '%'/'{'/'}' characters in a GeoJSON payload ever go through.
		// .uri(String) would run it through UriComponentsBuilder's template encoder too,
		// double-encoding every '%' (and mis-parsing '{'/'}' as template variable syntax).
		URI uri = URI.create(props.baseUrl() + path);
		byte[] bytes = execute(() -> mapboxRestClient.get().uri(uri).retrieve().body(byte[].class),
				"Mapbox static image request failed");
		return Optional.ofNullable(bytes);
	}

	/**
	 * Wraps the stored route geometry (a bare GeoJSON Geometry — {@code {"type":"LineString",...}},
	 * never a Feature) in a minimal Feature before encoding — Mapbox's {@code auto} position/zoom,
	 * used on every request here, computes its bounding box from the overlay's {@code features};
	 * a bare Geometry has none.
	 *
	 * <p>{@link URLEncoder#encode} is {@code application/x-www-form-urlencoded}, not RFC 3986
	 * percent-encoding — it turns a space into a literal {@code +}, not {@code %20}. Mapbox's server
	 * decodes {@code %XX} triples but never form-decodes, so an un-fixed-up {@code +} passes straight
	 * into the JSON text where the space used to be, corrupting the syntax (e.g.
	 * {@code {"type":+"LineString"}). Real stored geometries are NOT compact — Jackson's default
	 * writer inserts a space after every {@code :} and {@code ,} — so this bites every real request
	 * (confirmed against a live optimized-trip payload; Mapbox rejects it with a 422 "Invalid
	 * GeoJSON" — UAT gap G-02-2). {@code +} is otherwise a legal, unreserved path-segment character,
	 * so re-encoding it to {@code %20} after the fact is the standard, minimal fix for this exact
	 * Java gotcha.
	 */
	private static String geojsonOverlay(String routeGeometryJson) {
		String feature = "{\"type\":\"Feature\",\"properties\":{},\"geometry\":" + routeGeometryJson + "}";
		String encoded = URLEncoder.encode(feature, StandardCharsets.UTF_8).replace("+", "%20");
		return "geojson(" + encoded + ")";
	}

	private static String markerOverlay(List<double[]> stopCoordinates) {
		return stopCoordinates.stream()
				.map(coord -> "pin-s+" + MARKER_COLOR_HEX + "(" + coord[0] + "," + coord[1] + ")")
				.collect(Collectors.joining(","));
	}

	private String requestPath(String overlay) {
		return "/styles/v1/" + MAPBOX_USERNAME + "/" + props.style() + "/static/" + overlay
				+ "/auto/" + SNAPSHOT_WIDTH + "x" + SNAPSHOT_HEIGHT
				+ "?access_token=" + props.accessToken();
	}

	private <T> T execute(Supplier<T> call, String failureMessage) {
		try {
			return call.get();
		} catch (RestClientException ex) {
			log.warn("Mapbox call failed: {}", ex.getMessage());
			throw new MapboxClientException(failureMessage, ex);
		}
	}
}
