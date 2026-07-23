package com.tripflow.backend.client.gemini;

import java.util.List;

public record GeminiGenerateContentRequest(
        List<Content> contents,
        GenerationConfig generationConfig
) {

    public record Content(List<Part> parts) {}

    public record Part(String text) {}

    /**
     * responseMimeType=application/json is Gemini's native JSON-mode enforcement —
     * used together with (not instead of) the prompt's own "JSON only" instructions
     * from SCRUM-64b. Belt and suspenders.
     */
    public record GenerationConfig(Double temperature, String responseMimeType) {}

    public static GeminiGenerateContentRequest ofPrompt(String promptText) {
        return new GeminiGenerateContentRequest(
                List.of(new Content(List.of(new Part(promptText)))),
                new GenerationConfig(0.4, "application/json"));
    }
}
