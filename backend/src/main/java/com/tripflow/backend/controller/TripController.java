package com.tripflow.backend.controller;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripflow.backend.domain.enums.TripStatus;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.CreateTripRequest;
import com.tripflow.backend.dto.RateTripRequest;
import com.tripflow.backend.dto.TripOwnerSummaryResponse;
import com.tripflow.backend.dto.TripResponse;
import com.tripflow.backend.dto.TripSearchFilters;
import com.tripflow.backend.dto.UpdateTripRequest;
import com.tripflow.backend.ratelimit.RateLimitProperties;
import com.tripflow.backend.ratelimit.RateLimiterService;
import com.tripflow.backend.security.UserPrincipal;
import com.tripflow.backend.service.RouteOptimizationService;
import com.tripflow.backend.service.TripCloneService;
import com.tripflow.backend.service.TripLikeService;
import com.tripflow.backend.service.TripRatingService;
import com.tripflow.backend.service.TripSaveService;
import com.tripflow.backend.service.TripService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@Tag(name = "Trips", description = "Trip CRUD, route optimization")
@RestController
@RequestMapping("/api/trips")
@AllArgsConstructor
public class TripController {

    private final TripService tripService;
    private final RouteOptimizationService routeOptimizationService;
    private final TripCloneService tripCloneService;
    private final TripLikeService tripLikeService;
    private final TripSaveService tripSaveService;
    private final TripRatingService tripRatingService;
    private final RateLimiterService rateLimiterService;
    private final RateLimitProperties rateLimitProperties;


    @Operation(summary = "List (or search/filter) the authenticated user's trips",
            description = "Owner-only: each item includes visitedStopCount and completionPercentage, "
                    + "which are never exposed on the public discovery feed (D-08). `search` (title, "
                    + "tags, stop place-names), `status`, `visibility`, `startDateFrom`/`startDateTo` "
                    + "(Trip.startDate, not creation time) and `durationDays` (computed from stop "
                    + "day numbers, 0 for a stopless trip) are all optional and AND together; a blank "
                    + "search returns the full list, not a 400. When search or any filter is present, "
                    + "ordering is fixed at createdAt DESC, id DESC; otherwise the plain list path "
                    + "honours `sort` from the pageable params.")
    @GetMapping
    public ResponseEntity<PagedModel<TripOwnerSummaryResponse>> listTrips(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) TripStatus status,
            @RequestParam(required = false) TripVisibility visibility,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDateTo,
            @RequestParam(required = false) Integer durationDays,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        boolean hasSearchOrFilters = (search != null && !search.isBlank())
                || status != null || visibility != null
                || startDateFrom != null || startDateTo != null || durationDays != null;
        Page<TripOwnerSummaryResponse> page = hasSearchOrFilters
                ? tripService.searchOwnedTrips(principal.userId(), search,
                        new TripSearchFilters(status, visibility, startDateFrom, startDateTo, durationDays),
                        pageable)
                : tripService.listTrips(principal.userId(), pageable);
        return ResponseEntity.ok(new PagedModel<>(page));
    }

    @Operation(summary = "Create a trip", description = "Creates a trip with its initial stops in one call.")
    @PostMapping
    public ResponseEntity<TripResponse> createTrip(
            @RequestBody @Valid CreateTripRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        rateLimiterService.checkLimit("trip-create:" + principal.userId(), rateLimitProperties.tripCreate());
        return new ResponseEntity<>(tripService.createTrip(principal.userId(), request), HttpStatus.CREATED);
    }

    @Operation(summary = "List saved trips", description = "Paginated list of the authenticated user's own "
            + "saved/bookmarked trips (SOCIAL-04). Never includes another user's saves. Declared above "
            + "GET /{id} so Spring's literal-segment matching resolves 'saved' before the {id} template.")
    @GetMapping("/saved")
    public ResponseEntity<PagedModel<TripOwnerSummaryResponse>> listSavedTrips(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {
        Page<TripOwnerSummaryResponse> page = tripSaveService.listSaved(principal.userId(), pageable);
        return ResponseEntity.ok(new PagedModel<>(page));
    }

    @Operation(summary = "Get a trip",
            description = "Owner sees any trip; non-owners only see PUBLIC trips. A PRIVATE trip "
                    + "requested by a non-owner returns 404 (not 403) so its existence isn't leaked.")
    @GetMapping("/{id}")
    public ResponseEntity<TripResponse> getTrip(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(tripService.getTrip(id, principal.userId()));
    }

    @Operation(summary = "Toggle trip visibility", description = "Flips PRIVATE <-> PUBLIC. Owner only.")
    @PatchMapping("/{id}/visibility")
    public ResponseEntity<TripResponse> toggleVisibility(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(tripService.toggleVisibility(id, principal.userId()));
    }

    @Operation(summary = "Replace a trip", description = "Full itinerary replace — metadata and stops in one call. Owner only.")
    @PutMapping("/{id}")
    public ResponseEntity<TripResponse> updateTrip(
            @PathVariable Long id,
            @RequestBody @Valid UpdateTripRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(tripService.updateTrip(id, principal.userId(), request));
    }

    @Operation(summary = "Delete a trip", description = "Owner only.")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTrip(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        tripService.deleteTrip(id, principal.userId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Optimize trip route", description = "Reorders stops for shortest travel time via OpenRouteService VROOM. Requires >= 2 stops. Owner only.")
    @PostMapping("/{id}/optimize")
    public ResponseEntity<TripResponse> optimizeTrip(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        rateLimiterService.checkLimit("optimize:" + principal.userId(), rateLimitProperties.optimize());
        return ResponseEntity.ok(routeOptimizationService.optimize(id, principal.userId()));
    }

    @Operation(summary = "Clone a trip", description = "Deep-copies a PUBLIC trip (or your own) into your account as a "
            + "new PRIVATE trip. Stops are cloned in order; Places are shared, not duplicated. Photos and likes are not copied.")
    @PostMapping("/{id}/clone")
    public ResponseEntity<TripResponse> cloneTrip(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        rateLimiterService.checkLimit("trip-clone:" + principal.userId(), rateLimitProperties.tripClone());
        return new ResponseEntity<>(tripCloneService.cloneTrip(id, principal.userId()), HttpStatus.CREATED);
    }
    
    @Operation(summary = "Like a trip", description = "Idempotent: liking an already-liked trip still returns 200. "
            + "PUBLIC trips only, or your own PRIVATE trips.")
    @PostMapping("/{id}/like")
    public ResponseEntity<Void> likeTrip(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        tripLikeService.likeTrip(id, principal.userId());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Unlike a trip", description = "Idempotent: unliking a trip you haven't liked still returns 200.")
    @DeleteMapping("/{id}/like")
    public ResponseEntity<Void> unlikeTrip(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        tripLikeService.unlikeTrip(id, principal.userId());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Save a trip", description = "Idempotent: saving an already-saved trip still returns 200. "
            + "PUBLIC trips only, or your own PRIVATE trips.")
    @PostMapping("/{id}/save")
    public ResponseEntity<Void> saveTrip(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        tripSaveService.saveTrip(id, principal.userId());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Unsave a trip", description = "Idempotent: unsaving a trip you haven't saved still returns 200.")
    @DeleteMapping("/{id}/save")
    public ResponseEntity<Void> unsaveTrip(
            @PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        tripSaveService.unsaveTrip(id, principal.userId());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Rate a trip", description = "Sets or replaces the caller's 1-5 star rating "
            + "(SOCIAL-07). PUBLIC trips only, or your own PRIVATE trips. Re-rating replaces the "
            + "previous value — never creates a second row.")
    @PostMapping("/{id}/rate")
    public ResponseEntity<Void> rateTrip(
            @PathVariable Long id,
            @RequestBody @Valid RateTripRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        tripRatingService.rateTrip(id, principal.userId(), request.rating());
        return ResponseEntity.ok().build();
    }
}
