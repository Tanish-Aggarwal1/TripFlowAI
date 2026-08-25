package com.tripflow.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.tripflow.backend.dto.TripSummaryResponse;
import com.tripflow.backend.ratelimit.RateLimitExceededException;
import com.tripflow.backend.ratelimit.RateLimitProperties;
import com.tripflow.backend.ratelimit.RateLimiterService;
import com.tripflow.backend.security.JwtService;
import com.tripflow.backend.service.TripService;

/**
 * SCRUM-493: web-layer slice for the length cap and rate-limit wiring on
 * GET /api/discovery/search. DiscoveryControllerIT covers the full DB-backed flow
 * but needs Docker; these run locally and catch a wiring regression without it —
 * same reasoning as AuthControllerTest's refresh-cookie tests.
 */
@WebMvcTest(DiscoveryController.class)
@AutoConfigureMockMvc(addFilters = false)
class DiscoveryControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private TripService tripService;

	@MockitoBean
	private RateLimiterService rateLimiterService;

	@MockitoBean
	private RateLimitProperties rateLimitProperties;

	// JwtAuthFilter is a @Component Filter, always constructed by @WebMvcTest regardless
	// of classes={...} narrowing; addFilters=false above means it never actually runs.
	@MockitoBean
	private JwtService jwtService;

	@Test
	void search_qOverMaxLength_returns400WithoutCallingService() throws Exception {
		String tooLong = "a".repeat(101);

		mockMvc.perform(get("/api/discovery/search").param("q", tooLong))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.fieldErrors[0].field").value("q"));

		verify(tripService, never()).searchPublicTrips(any(), any());
		verify(rateLimiterService, never()).checkLimit(any(), any());
	}

	@Test
	void search_withinLimit_checksRateLimiterKeyedOnRemoteAddr() throws Exception {
		when(tripService.searchPublicTrips(eq("ottawa"), any())).thenReturn(Page.<TripSummaryResponse>empty());

		mockMvc.perform(get("/api/discovery/search").param("q", "ottawa"))
				.andExpect(status().isOk());

		verify(rateLimiterService).checkLimit(startsWith("discovery-search:"), any());
	}

	@Test
	void search_rateLimitExceeded_returns429WithoutCallingService() throws Exception {
		doThrow(new RateLimitExceededException("Rate limit exceeded, try again in 30 second(s)", 30))
				.when(rateLimiterService).checkLimit(any(), any());

		mockMvc.perform(get("/api/discovery/search").param("q", "ottawa"))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().string("Retry-After", "30"));

		verify(tripService, never()).searchPublicTrips(any(), any());
	}
}
