package com.tripflow.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class GeminiResponseParserFixtureTest {
	private final GeminiResponseParser parser = new GeminiResponseParser();

	@ParameterizedTest
	@ValueSource(strings = {
			"itinerary-response-1.json",
			"itinerary-response-2.json",
			"itinerary-response-3.json",
			"itinerary-response-4.json",
			"itinerary-response-5.json"
	})
	void variedFixtures_parseIntoValidSchema(String fixtureName) throws IOException {
		String raw = readFixture(fixtureName);

		SuggestedItinerary result = parser.parse(raw);

		assertThat(result.summary()).isNotBlank();
		assertThat(result.stops()).isNotEmpty();
		result.stops().forEach(stop -> {
			assertThat(stop.name()).isNotBlank();
			assertThat(stop.order()).isNotNull();
			assertThat(stop.latitude()).isNotNull();
			assertThat(stop.longitude()).isNotNull();
		});
	}

	private String readFixture(String name) throws IOException {
		try (InputStream in = getClass().getClassLoader().getResourceAsStream("gemini-fixtures/" + name)) {
			if (in == null) {
				throw new IOException("Fixture not found: " + name);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
