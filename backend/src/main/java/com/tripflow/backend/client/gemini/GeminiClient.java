package com.tripflow.backend.client.gemini;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.tripflow.backend.exception.GeminiClientException;
import com.tripflow.backend.exception.GeminiRateLimitException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Wrapper around the Gemini generateContent REST endpoint.
 * The API key never appears below — not in logs, not in exception messages —
 * it's sent once as a request header by GeminiClientConfig and never touched here.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiClient {

    private final RestClient geminiRestClient;
    private final GeminiProperties properties;

    public GeminiGenerateContentResponse generateContent(String promptText) {
        return execute(() -> {
            GeminiGenerateContentRequest request = GeminiGenerateContentRequest.ofPrompt(promptText);
            GeminiGenerateContentResponse response = geminiRestClient.post()
                    .uri("/v1beta/models/{model}:generateContent", properties.model())
                    .body(request)
                    .retrieve()
                    .body(GeminiGenerateContentResponse.class);

            if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
                throw new GeminiClientException("Gemini returned an empty response");
            }
            log.debug("Gemini generateContent ok model={} promptChars={}", properties.model(), promptText.length());
            return response;
        });
    }

    /**
     * Shared call + exception translation (SCRUM-494, mirroring OrsClient.execute /
     * SCRUM-221): a 429 from Gemini means the shared free-tier quota is exhausted, not
     * that the service is down — always translate to {@link GeminiRateLimitException}
     * (429), never let it fall through to the generic {@link GeminiClientException} (502).
     */
    private <T> T execute(Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpClientErrorException.TooManyRequests ex) {
            log.warn("Gemini call rate-limited (429) model={}: {}", properties.model(), ex.getMessage());
            throw new GeminiRateLimitException("Gemini quota exceeded", ex);
        } catch (RestClientException ex) {
            log.warn("Gemini generateContent call failed model={}: {}", properties.model(), ex.getMessage());
            throw new GeminiClientException("Gemini request failed", ex);
        }
    }
}