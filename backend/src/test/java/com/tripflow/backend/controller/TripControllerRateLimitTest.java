package com.tripflow.backend.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.tripflow.backend.domain.enums.TripStatus;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.CreateStopRequest;
import com.tripflow.backend.dto.CreateTripRequest;
import com.tripflow.backend.dto.TripResponse;
import com.tripflow.backend.ratelimit.RateLimitExceededException;
import com.tripflow.backend.ratelimit.RateLimitProperties;
import com.tripflow.backend.ratelimit.RateLimiterService;
import com.tripflow.backend.security.UserPrincipal;
import com.tripflow.backend.service.RouteOptimizationService;
import com.tripflow.backend.service.TripCloneService;
import com.tripflow.backend.service.TripLikeService;
import com.tripflow.backend.service.TripRatingService;
import com.tripflow.backend.service.TripSaveService;
import com.tripflow.backend.service.TripService;

/**
 * SCRUM-410: proves POST /api/trips and POST /api/trips/{id}/clone check the rate
 * limiter (keyed per-user) before delegating to the service, and that an exceeded
 * limit short-circuits without calling it. Plain unit test rather than @WebMvcTest:
 * @AuthenticationPrincipal doesn't resolve in a narrow web slice since it doesn't load
 * SecurityConfig's @EnableWebSecurity — GlobalExceptionHandlerIntegrationTest already
 * covers the generic RateLimitExceededException -> 429 mapping.
 */
@ExtendWith(MockitoExtension.class)
class TripControllerRateLimitTest {

	@Mock
	private TripService tripService;
	@Mock
	private RouteOptimizationService routeOptimizationService;
	@Mock
	private TripCloneService tripCloneService;
	@Mock
	private TripLikeService tripLikeService;
	@Mock
	private TripSaveService tripSaveService;
	@Mock
	private TripRatingService tripRatingService;
	@Mock
	private RateLimiterService rateLimiterService;
	@Mock
	private RateLimitProperties rateLimitProperties;

	private final UserPrincipal principal = new UserPrincipal(42L, "user@example.com");

	private TripController controller() {
		return new TripController(tripService, routeOptimizationService, tripCloneService, tripLikeService,
				tripSaveService, tripRatingService, rateLimiterService, rateLimitProperties);
	}

	private CreateTripRequest sampleCreateRequest() {
		return new CreateTripRequest("Trip", null, null, TripVisibility.PRIVATE,
				List.of(new CreateStopRequest("Stop", 45.0, -79.9, null, null, null)));
	}

	private TripResponse sampleTripResponse(Long id) {
		return new TripResponse(id, "Trip", null, null, TripVisibility.PRIVATE, TripStatus.PLANNED, 42L, List.of(),
				null, null, null, null, 0, 0);
	}

	@Test
	void createTrip_withinLimit_checksRateLimiterKeyedOnUserIdBeforeCallingService() {
		when(tripService.createTrip(eq(42L), any())).thenReturn(sampleTripResponse(1L));

		controller().createTrip(sampleCreateRequest(), principal);

		verify(rateLimiterService).checkLimit(eq("trip-create:42"), any());
		verify(tripService).createTrip(eq(42L), any());
	}

	@Test
	void createTrip_rateLimitExceeded_propagatesWithoutCallingService() {
		doThrow(new RateLimitExceededException("Rate limit exceeded, try again in 30 second(s)", 30))
				.when(rateLimiterService).checkLimit(eq("trip-create:42"), any());

		assertThatThrownBy(() -> controller().createTrip(sampleCreateRequest(), principal))
				.isInstanceOf(RateLimitExceededException.class);

		verifyNoInteractions(tripService);
	}

	@Test
	void cloneTrip_withinLimit_checksRateLimiterKeyedOnUserIdBeforeCallingService() {
		when(tripCloneService.cloneTrip(eq(7L), eq(42L))).thenReturn(sampleTripResponse(99L));

		controller().cloneTrip(7L, principal);

		verify(rateLimiterService).checkLimit(eq("trip-clone:42"), any());
		verify(tripCloneService).cloneTrip(eq(7L), eq(42L));
	}

	@Test
	void cloneTrip_rateLimitExceeded_propagatesWithoutCallingService() {
		doThrow(new RateLimitExceededException("Rate limit exceeded, try again in 30 second(s)", 30))
				.when(rateLimiterService).checkLimit(eq("trip-clone:42"), any());

		assertThatThrownBy(() -> controller().cloneTrip(7L, principal))
				.isInstanceOf(RateLimitExceededException.class);

		verify(tripCloneService, never()).cloneTrip(any(), any());
	}
}
