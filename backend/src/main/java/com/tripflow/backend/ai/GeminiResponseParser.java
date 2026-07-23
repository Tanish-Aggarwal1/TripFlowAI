package com.tripflow.backend.ai;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.tripflow.backend.exception.GeminiParsingException;

/**
 * Parses Gemini's raw text output into the strict {@link SuggestedItinerary} schema.
 * Uses a dedicated, locally-configured ObjectMapper rather than the shared app-wide
 * bean, so this parse stays strict regardless of what JacksonConfig sets globally.
 */
@Component
public class GeminiResponseParser {

	private static final ObjectMapper STRICT_MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
            .build();

    public SuggestedItinerary parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            throw new GeminiParsingException("Gemini returned an empty response body");
        }

        String candidate = stripCodeFences(rawText.trim());

        try {
            return STRICT_MAPPER.readValue(candidate, SuggestedItinerary.class);
        } catch (JsonProcessingException ex) {
            throw new GeminiParsingException(
                    "Gemini response was not valid JSON matching the itinerary schema", ex);
        }
    }

    /**
     * Gemini occasionally wraps JSON in ```json fences despite the prompt's "JSON
     * only" instruction. Strip defensively — genuine non-JSON output still fails
     * the readValue call above and still throws GeminiParsingException.
     */
    private static String stripCodeFences(String text) {
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewline != -1 && lastFence > firstNewline) {
                return text.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return text;
    }
}
