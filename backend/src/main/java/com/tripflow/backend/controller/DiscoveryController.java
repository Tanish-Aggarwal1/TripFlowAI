package com.tripflow.backend.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripflow.backend.dto.TripSummaryResponse;
import com.tripflow.backend.service.TripService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Discovery", description = "Public trip discovery feed")
@RestController
@RequestMapping("/api/discovery")
@RequiredArgsConstructor
public class DiscoveryController {

    private final TripService tripService;

    @Operation(summary = "Search public trips", description = "Case-insensitive substring match on trip title and "
            + "tags, PUBLIC trips only. 'q' is required and cannot be blank. No authentication required.")
    @GetMapping("/search")
    public ResponseEntity<PagedModel<TripSummaryResponse>> searchPublicTrips(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<TripSummaryResponse> page = tripService.searchPublicTrips(q, pageable);
        return ResponseEntity.ok(new PagedModel<>(page));
    }
  
    @Operation(summary = "List public trips", description = "Paginated feed of PUBLIC trips. No authentication required.")
    @GetMapping("/trips")
    public ResponseEntity<PagedModel<TripSummaryResponse>> listPublicTrips(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<TripSummaryResponse> page = tripService.listPublicTrips(pageable);
        return ResponseEntity.ok(new PagedModel<>(page));
    }
}
