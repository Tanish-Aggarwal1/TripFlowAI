package com.tripflow.backend.client.mapbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.tripflow.backend.exception.MapboxClientException;

class MapboxClientTest {

	private static final String BASE_URL = "https://mapbox.test";
	private static final byte[] IMAGE_BYTES = "fake-png-bytes".getBytes(StandardCharsets.UTF_8);

	private MockRestServiceServer server;
	private RestClient.Builder builder;

	@BeforeEach
	void setUp() {
		builder = RestClient.builder().baseUrl(BASE_URL);
		server = MockRestServiceServer.bindTo(builder).build();
	}

	private MapboxProperties props(String accessToken) {
		return new MapboxProperties(BASE_URL, accessToken, "streets-v12", Duration.ofSeconds(5), Duration.ofSeconds(10));
	}

	private MapboxClient client(String accessToken) {
		return new MapboxClient(builder.build(), props(accessToken));
	}

	@Test
	void staticSnapshot_success_returnsRawImageBytes() {
		MapboxClient client = client("test-token-1234");
		server.expect(requestTo(containsString("/styles/v1/mapbox/streets-v12/static/")))
				.andExpect(method(HttpMethod.GET))
				.andExpect(requestTo(containsString("access_token=test-token-1234")))
				.andRespond(withSuccess(IMAGE_BYTES, MediaType.IMAGE_PNG));

		Optional<byte[]> result = client.staticSnapshot(null, List.of(new double[] { -79.4, 43.7 }));

		assertThat(result).isPresent();
		assertThat(result.get()).isEqualTo(IMAGE_BYTES);
		server.verify();
	}

	@Test
	void staticSnapshot_serverError_throwsMapboxClientExceptionNeverRawRestClientException() {
		MapboxClient client = client("test-token-1234");
		server.expect(requestTo(containsString("/styles/v1/mapbox/streets-v12/static/")))
				.andRespond(withServerError());

		assertThatThrownBy(() -> client.staticSnapshot(null, List.of(new double[] { -79.4, 43.7 })))
				.isInstanceOf(MapboxClientException.class);
	}

	@Test
	void staticSnapshot_blankToken_returnsEmptyAndMakesZeroHttpCalls() {
		MapboxClient client = client("");

		Optional<byte[]> result = client.staticSnapshot(null, List.of(new double[] { -79.4, 43.7 }));

		assertThat(result).isEmpty();
		server.verify(); // no expectations were set up — any request would fail this test
	}

	@Test
	void staticSnapshot_absentToken_returnsEmptyAndMakesZeroHttpCalls() {
		MapboxClient client = client(null);

		Optional<byte[]> result = client.staticSnapshot(null, List.of(new double[] { -79.4, 43.7 }));

		assertThat(result).isEmpty();
		server.verify();
	}

	@Test
	void staticSnapshot_noRouteAndNoStops_returnsEmptyAndMakesZeroHttpCalls() {
		MapboxClient client = client("test-token-1234");

		Optional<byte[]> result = client.staticSnapshot(null, List.of());

		assertThat(result).isEmpty();
		server.verify();
	}

	@Test
	void staticSnapshot_withRouteGeometry_requestUriIsSinglyEncodedNotDoubleEncoded() {
		MapboxClient client = client("test-token-1234");
		String routeGeometry = "{\"type\":\"LineString\",\"coordinates\":[[-79.4,43.7],[-79.5,43.8]]}";
		String expectedFeature = "{\"type\":\"Feature\",\"properties\":{},\"geometry\":" + routeGeometry + "}";
		String expectedOverlay = "geojson(" + java.net.URLEncoder.encode(expectedFeature, StandardCharsets.UTF_8) + ")";
		String expectedUri = BASE_URL + "/styles/v1/mapbox/streets-v12/static/" + expectedOverlay
				+ "/auto/600x400?access_token=test-token-1234";
		// An exact match here (rather than containsString) is the point: if the request
		// URI were double-encoded, every literal '%' in expectedOverlay would come back
		// as '%25' and this exact-string match would fail.
		server.expect(requestTo(expectedUri))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(IMAGE_BYTES, MediaType.IMAGE_PNG));

		Optional<byte[]> result = client.staticSnapshot(routeGeometry, List.of());

		assertThat(result).isPresent();
		assertThat(result.get()).isEqualTo(IMAGE_BYTES);
		server.verify();
	}

	@Test
	void staticSnapshot_withRouteGeometry_overlayWrapsBareGeometryInAFeature() {
		// G-02-2: Mapbox's `auto` position/zoom (used on every request here) computes its
		// bounding box from the overlay's `features` — a bare Geometry has none, so Mapbox
		// rejects it with a 422 "Invalid GeoJSON". Confirmed against a real optimized-trip
		// payload. The overlay must wrap Trip.routeGeometry's bare Geometry in a Feature.
		MapboxClient client = client("test-token-1234");
		String routeGeometry = "{\"type\":\"LineString\",\"coordinates\":[[-79.4,43.7],[-79.5,43.8]]}";

		server.expect(requestTo(containsString(
				java.net.URLEncoder.encode("\"type\":\"Feature\"", StandardCharsets.UTF_8))))
				.andExpect(requestTo(containsString(
						java.net.URLEncoder.encode("\"geometry\":" + routeGeometry, StandardCharsets.UTF_8))))
				.andRespond(withSuccess(IMAGE_BYTES, MediaType.IMAGE_PNG));

		Optional<byte[]> result = client.staticSnapshot(routeGeometry, List.of());

		assertThat(result).isPresent();
		server.verify();
	}

	@Test
	void staticSnapshot_withSpacesInRouteGeometry_encodesSpacesAsPercentTwentyNotPlus() {
		// G-02-2 (round 2): Jackson's default writer inserts a space after every ':' and ','
		// — real stored route geometries are NOT compact, unlike this test file's other
		// hand-written compact fixtures. URLEncoder.encode is application/x-www-form-urlencoded,
		// which turns a space into a literal '+', not '%20'. Mapbox's server percent-decodes but
		// never form-decodes, so an un-fixed-up '+' lands in the JSON text as a literal '+'
		// character where a space used to be — corrupting the syntax and producing a real 422
		// "Invalid GeoJSON" in production, even though the double-encoding and Feature-wrapping
		// fixes were both already correct.
		MapboxClient client = client("test-token-1234");
		String routeGeometry = "{\"type\": \"LineString\", \"coordinates\": [[-79.4, 43.7], [-79.5, 43.8]]}";

		server.expect(requestTo(not(containsString("+"))))
				.andExpect(requestTo(containsString("%20")))
				.andRespond(withSuccess(IMAGE_BYTES, MediaType.IMAGE_PNG));

		Optional<byte[]> result = client.staticSnapshot(routeGeometry, List.of());

		assertThat(result).isPresent();
		server.verify();
	}

	@Test
	void staticSnapshot_overLengthGeometry_fallsBackToMarkerOnlyOverlay() {
		MapboxClient client = client("test-token-1234");
		// A route geometry with thousands of coordinate pairs pushes the request URL
		// well over the 8,192-char Mapbox cap once URL-encoded.
		StringBuilder coords = new StringBuilder("[");
		for (int i = 0; i < 2000; i++) {
			if (i > 0) coords.append(",");
			coords.append("[-79.4,43.7]");
		}
		coords.append("]");
		String hugeGeometry = "{\"type\":\"LineString\",\"coordinates\":" + coords + "}";

		server.expect(requestTo(containsString("pin-")))
				.andExpect(requestTo(not(containsString("geojson("))))
				.andRespond(withSuccess(IMAGE_BYTES, MediaType.IMAGE_PNG));

		Optional<byte[]> result = client.staticSnapshot(hugeGeometry, List.of(new double[] { -79.4, 43.7 }));

		assertThat(result).isPresent();
		server.verify();
	}

	@Test
	void mapboxProperties_toString_doesNotContainTheFullAccessToken() {
		MapboxProperties props = props("super-secret-token-value");

		assertThat(props.toString()).doesNotContain("super-secret-token-value");
	}
}
