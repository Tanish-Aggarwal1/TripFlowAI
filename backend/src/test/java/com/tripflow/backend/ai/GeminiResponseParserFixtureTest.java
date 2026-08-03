package com.tripflow.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

	@org.junit.jupiter.api.Test
	void genericOverload_parsesIntoGeneratedTripPlan() {
		String raw = """
				{"title":"Kyoto Food Tour","summary":"A foodie trip","stops":[
				  {"order":0,"name":"Nishiki Market","latitude":35.0051,"longitude":135.7649,"reason":"Great street food"}
				]}""";

		GeneratedTripPlan result = parser.parse(raw, GeneratedTripPlan.class);

		assertThat(result.title()).isEqualTo("Kyoto Food Tour");
		assertThat(result.stops()).hasSize(1);
		assertThat(result.stops().get(0).name()).isEqualTo("Nishiki Market");
	}

	@org.junit.jupiter.api.Test
	void genericOverload_codeFencedResponse_stillParses() {
		String raw = "```json\n{\"title\":\"T\",\"summary\":\"s\",\"stops\":[]}\n```";

		GeneratedTripPlan result = parser.parse(raw, GeneratedTripPlan.class);

		assertThat(result.title()).isEqualTo("T");
		assertThat(result.stops()).isEmpty();
	}

	@org.junit.jupiter.api.Test
	void genericOverload_unknownField_throwsGeminiParsingException() {
		String raw = "{\"title\":\"T\",\"summary\":\"s\",\"stops\":[],\"unexpected\":true}";

		assertThatThrownBy(() -> parser.parse(raw, GeneratedTripPlan.class))
				.isInstanceOf(com.tripflow.backend.exception.GeminiParsingException.class);
	}
}
