package com.tripflow.backend.ai;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import com.tripflow.backend.exception.PromptTooLargeException;

/**
 * Loads and renders src/main/resources/prompts/itinerary.txt.
 * Kept isolated from GeminiClient (SCRUM-64a) so prompt wording iterates
 * without touching client/transport code — see SCRUM-64b.
 */
@Component
public class ItineraryPromptTemplate {

	/**
	 * Defensive second layer behind {@code ItineraryPreferencesRequest}'s per-field
	 * {@code @Size} caps (SCRUM-217/AUDIT-08): interests/budget/pace are individually
	 * bounded, but a trip's stop count — and therefore total destination text — isn't
	 * capped anywhere, so the rendered prompt's total size is checked here as a backstop
	 * against an unbounded prompt reaching the metered Gemini API.
	 */
	static final int MAX_PROMPT_LENGTH = 8_000;

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

	private final String rawTemplate;

    public ItineraryPromptTemplate(@Value("classpath:prompts/itinerary.txt") Resource resource) {
        try {
            this.rawTemplate = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to load itinerary prompt template", ex);
        }
    }

    public String render(ItineraryPromptInput input) {
        Map<String, String> values = Map.of(
                "interests", joinOrNone(input.interests()),
                "budget", input.budget() == null ? "not specified" : input.budget(),
                "pace", input.pace() == null ? "not specified" : input.pace(),
                "destinations", joinOrNone(input.destinations()));

        String rendered = substitute(values);

        if (rendered.length() > MAX_PROMPT_LENGTH) {
            throw new PromptTooLargeException(
                    "Rendered itinerary prompt is " + rendered.length()
                            + " characters, exceeding the " + MAX_PROMPT_LENGTH + " character limit");
        }
        return rendered;
    }

    private static String joinOrNone(List<String> values) {
        return (values == null || values.isEmpty()) ? "none specified" : String.join(", ", values);
    }

    /**
     * Single-pass placeholder substitution. Chained {@code String.replace()} calls
     * re-scan the *entire current string* on each call, including whatever the
     * previous call just substituted in — so user-supplied text (e.g. an
     * {@code interests} entry that happens to contain the literal text
     * {@code "{{budget}}"}) could be found and replaced by a later call in the
     * chain, landing another field's value inside what was meant to be free-text
     * content. Scanning the raw template exactly once and looking up each match
     * avoids that entirely: a value is never itself re-scanned for placeholders.
     */
    private String substitute(Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(rawTemplate);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String replacement = values.getOrDefault(matcher.group(1), matcher.group(0));
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

}
