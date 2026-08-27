package com.tripflow.backend.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.tripflow.backend.dto.PhotoSignatureResponse;
import com.tripflow.backend.ratelimit.RateLimitExceededException;
import com.tripflow.backend.ratelimit.RateLimitProperties;
import com.tripflow.backend.ratelimit.RateLimiterService;
import com.tripflow.backend.security.UserPrincipal;
import com.tripflow.backend.service.StopPhotoService;

/**
 * SCRUM-410: proves POST /api/stops/{id}/photo-signature checks the rate limiter
 * (keyed per-user) before delegating to the service, and that an exceeded limit
 * short-circuits without calling it. Plain unit test rather than @WebMvcTest —
 * see TripControllerRateLimitTest for why.
 */
@ExtendWith(MockitoExtension.class)
class StopPhotoControllerRateLimitTest {

	@Mock
	private StopPhotoService stopPhotoService;
	@Mock
	private RateLimiterService rateLimiterService;
	@Mock
	private RateLimitProperties rateLimitProperties;

	private final UserPrincipal principal = new UserPrincipal(42L, "user@example.com");

	private StopPhotoController controller() {
		return new StopPhotoController(stopPhotoService, rateLimiterService, rateLimitProperties);
	}

	@Test
	void getUploadSignature_withinLimit_checksRateLimiterKeyedOnUserIdBeforeCallingService() {
		when(stopPhotoService.getUploadSignature(eq(3L), eq(42L)))
				.thenReturn(new PhotoSignatureResponse("demo", "key", 1L, "sig", Map.of()));

		controller().getUploadSignature(3L, principal);

		verify(rateLimiterService).checkLimit(eq("photo-signature:42"), any());
		verify(stopPhotoService).getUploadSignature(eq(3L), eq(42L));
	}

	@Test
	void getUploadSignature_rateLimitExceeded_propagatesWithoutCallingService() {
		doThrow(new RateLimitExceededException("Rate limit exceeded, try again in 30 second(s)", 30))
				.when(rateLimiterService).checkLimit(eq("photo-signature:42"), any());

		assertThatThrownBy(() -> controller().getUploadSignature(3L, principal))
				.isInstanceOf(RateLimitExceededException.class);

		verifyNoInteractions(stopPhotoService);
	}
}
