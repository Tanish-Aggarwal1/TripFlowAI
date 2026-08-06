package com.tripflow.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.tripflow.backend.exception.PromptTooLargeException;

class ItineraryPromptTemplateTest {

    private final ItineraryPromptTemplate template =
            new ItineraryPromptTemplate(new ClassPathResource("prompts/itinerary.txt"));

    @Test
    void render_normalInput_producesPromptUnderTheLimit() {
        ItineraryPromptInput input = new ItineraryPromptInput(
                List.of("food", "history"), "moderate", "relaxed", List.of("Toronto", "Ottawa"));

        String rendered = template.render(input);

        assertThat(rendered).contains("food, history", "moderate", "relaxed", "Toronto, Ottawa");
        assertThat(rendered.length()).isLessThan(ItineraryPromptTemplate.MAX_PROMPT_LENGTH);
    }

    @Test
    void render_nullPreferences_substitutesNotSpecified() {
        ItineraryPromptInput input = new ItineraryPromptInput(null, null, null, List.of());

        String rendered = template.render(input);

        assertThat(rendered).contains("not specified");
        assertThat(rendered).contains("none specified");
    }

    @Test
    void render_interestContainingPlaceholderSyntax_isNotReSubstitutedByLaterPlaceholders() {
        // Regression test: chained String.replace() calls used to re-scan the whole
        // string on each call, so an interest literally containing "{{budget}}" would
        // get overwritten by the budget substitution that runs after it. A single-pass
        // substitution must leave user-supplied text exactly as given.
        ItineraryPromptInput input = new ItineraryPromptInput(
                List.of("{{budget}}", "{{pace}}"), "luxury", "fast", List.of("Toronto"));

        String rendered = template.render(input);

        assertThat(rendered).contains("{{budget}}, {{pace}}");
        assertThat(rendered).contains("luxury");
        assertThat(rendered).contains("fast");
    }

    @Test
    void render_oversizedDestinationsList_throwsPromptTooLargeException() {
        // Each individual destination is well within any per-field cap, but there are
        // enough of them that the *rendered* prompt exceeds MAX_PROMPT_LENGTH — the
        // scenario this defensive check exists for, since stop count itself isn't capped.
        List<String> manyDestinations = IntStream.range(0, 500)
                .mapToObj(i -> "Destination Number " + i)
                .toList();
        ItineraryPromptInput input = new ItineraryPromptInput(
                List.of("food"), "moderate", "relaxed", manyDestinations);

        assertThatThrownBy(() -> template.render(input))
                .isInstanceOf(PromptTooLargeException.class)
                .hasMessageContaining(String.valueOf(ItineraryPromptTemplate.MAX_PROMPT_LENGTH));
    }
}
