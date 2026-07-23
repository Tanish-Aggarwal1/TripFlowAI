package com.tripflow.backend.ai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * Loads and renders src/main/resources/prompts/itinerary.txt.
 * Kept isolated from GeminiClient (SCRUM-64a) so prompt wording iterates
 * without touching client/transport code — see SCRUM-64b.
 */
@Component
public class ItineraryPromptTemplate {
	
	private final String rawTemplate;

    public ItineraryPromptTemplate(@Value("classpath:prompts/itinerary.txt") Resource resource) {
        try {
            this.rawTemplate = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to load itinerary prompt template", ex);
        }
    }

    public String render(ItineraryPromptInput input) {
        return rawTemplate
                .replace("{{interests}}", joinOrNone(input.interests()))
                .replace("{{budget}}", input.budget() == null ? "not specified" : input.budget())
                .replace("{{pace}}", input.pace() == null ? "not specified" : input.pace())
                .replace("{{destinations}}", joinOrNone(input.destinations()));
    }

    private static String joinOrNone(List<String> values) {
        return (values == null || values.isEmpty()) ? "none specified" : String.join(", ", values);
    }

}
