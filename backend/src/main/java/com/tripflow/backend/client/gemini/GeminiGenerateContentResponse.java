package com.tripflow.backend.client.gemini;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Partial mapping of Gemini's actual response envelope — we only care about
 * candidates[].content.parts[].text. The real response includes several more
 * top-level fields (usageMetadata, modelVersion, etc.) and per-candidate
 * fields (safetyRatings, etc.) we don't model, so every record here MUST
 * tolerate unknown properties — omitting @JsonIgnoreProperties(ignoreUnknown)
 * would make the very first real API call throw, even though every test using
 * a hand-written fixture (which naturally only has the fields we declared)
 * would keep passing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiGenerateContentResponse(List<Candidate> candidates) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Candidate(Content content, String finishReason) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(List<Part> parts, String role) {}

    public record Part(String text) {}

    /** Concatenates every part's text in the first candidate. Gemini almost always returns one part. */
    public String firstCandidateText() {
        if (candidates == null || candidates.isEmpty()
                || candidates.get(0).content() == null
                || candidates.get(0).content().parts() == null) {
            return null;
        }
        return candidates.get(0).content().parts().stream()
                .map(Part::text)
                .filter(Objects::nonNull)
                .reduce("", String::concat);
    }
}