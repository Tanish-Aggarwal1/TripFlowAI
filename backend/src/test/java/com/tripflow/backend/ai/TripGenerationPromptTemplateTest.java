package com.tripflow.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.tripflow.backend.exception.PromptTooLargeException;

class TripGenerationPromptTemplateTest {

    private final TripGenerationPromptTemplate template =
            new TripGenerationPromptTemplate(new ClassPathResource("prompts/generate-trip.txt"));

    @Test
    void render_normalInput_substitutesPromptAndStaysUnderTheLimit() {
        TripGenerationPromptInput input = new TripGenerationPromptInput("3 days in Kyoto, food and temples");

        String rendered = template.render(input);

        assertThat(rendered).contains("3 days in Kyoto, food and temples");
        assertThat(rendered.length()).isLessThan(TripGenerationPromptTemplate.MAX_PROMPT_LENGTH);
    }

    @Test
    void render_oversizedPrompt_throwsPromptTooLargeException() {
        String hugePrompt = "x".repeat(TripGenerationPromptTemplate.MAX_PROMPT_LENGTH);
        TripGenerationPromptInput input = new TripGenerationPromptInput(hugePrompt);

        assertThatThrownBy(() -> template.render(input))
                .isInstanceOf(PromptTooLargeException.class)
                .hasMessageContaining(String.valueOf(TripGenerationPromptTemplate.MAX_PROMPT_LENGTH));
    }
}
