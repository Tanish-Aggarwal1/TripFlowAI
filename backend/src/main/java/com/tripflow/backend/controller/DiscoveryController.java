package com.tripflow.backend.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripflow.backend.dto.FeedTripResponse;
import com.tripflow.backend.dto.TripSummaryResponse;
import com.tripflow.backend.ratelimit.RateLimitProperties;
import com.tripflow.backend.ratelimit.RateLimiterService;
import com.tripflow.backend.security.UserPrincipal;
import com.tripflow.backend.service.TripService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@Tag(name = "Discovery", description = "Public trip discovery feed")
@RestController
@RequestMapping("/api/discovery")
@RequiredArgsConstructor
@Validated
public class DiscoveryController {

    private final TripService tripService;
    private final RateLimiterService rateLimiterService;
    private final RateLimitProperties rateLimitProperties;

    @Operation(summary = "Search public trips", description = "Case-insensitive substring match on trip title and "
            + "tags, PUBLIC trips only. 'q' is required, cannot be blank, and cannot exceed 100 characters. "
            + "Results are always ordered createdAt desc — a non-default 'sort' is rejected with 400. "
            + "Authentication required; IP-keyed rate limited.")
    @GetMapping("/search")
    public ResponseEntity<PagedModel<TripSummaryResponse>> searchPublicTrips(
            @RequestParam(required = false) @Size(max = 100, message = "q must not exceed 100 characters") String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            HttpServletRequest httpRequest) {
        rateLimiterService.checkLimit("discovery-search:" + httpRequest.getRemoteAddr(),
                rateLimitProperties.discoverySearch());
        Page<TripSummaryResponse> page = tripService.searchPublicTrips(q, pageable);
        return ResponseEntity.ok(new PagedModel<>(page));
    }

    @Operation(summary = "List public trips", description = "Paginated card-projection list of PUBLIC trips. "
            + "Authentication required.")
    @GetMapping("/trips")
    public ResponseEntity<PagedModel<TripSummaryResponse>> listPublicTrips(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<TripSummaryResponse> page = tripService.listPublicTrips(pageable);
        return ResponseEntity.ok(new PagedModel<>(page));
    }

    @Operation(summary = "Authenticated feed of PUBLIC trips",
            description = "Full-card feed for the TikTok-style For You feed (SOCIAL-01): each item carries "
                    + "owner username, description, tags and an ordered stops array with per-stop photos/text. "
                    + "Ordered by recency only — interest-based ranking lands in a later plan. "
                    + "Authentication required.")
    @GetMapping("/feed")
    public ResponseEntity<PagedModel<FeedTripResponse>> listFeed(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        Page<FeedTripResponse> page = tripService.listFeed(principal.userId(), pageable);
        return ResponseEntity.ok(new PagedModel<>(page));
    }
}
