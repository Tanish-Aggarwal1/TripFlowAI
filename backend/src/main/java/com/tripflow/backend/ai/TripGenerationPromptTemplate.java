package com.tripflow.backend.ai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import com.tripflow.backend.exception.PromptTooLargeException;

/**
 * Loads and renders src/main/resources/prompts/generate-trip.txt — the
 * "build a whole trip from scratch" prompt, kept separate from
 * {@link ItineraryPromptTemplate} because that template's wording is framed
 * around augmenting a trip's existing stops, not generating one from nothing.
 */
@Component
public class TripGenerationPromptTemplate {

	static final int MAX_PROMPT_LENGTH = 8_000;

	private final String rawTemplate;

    public TripGenerationPromptTemplate(@Value("classpath:prompts/generate-trip.txt") Resource resource) {
        try {
            this.rawTemplate = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to load trip generation prompt template", ex);
        }
    }

    public String render(TripGenerationPromptInput input) {
        String rendered = rawTemplate.replace("{{prompt}}", input.prompt());

        if (rendered.length() > MAX_PROMPT_LENGTH) {
            throw new PromptTooLargeException(
                    "Rendered trip generation prompt is " + rendered.length()
                            + " characters, exceeding the " + MAX_PROMPT_LENGTH + " character limit");
        }
        return rendered;
    }
}
