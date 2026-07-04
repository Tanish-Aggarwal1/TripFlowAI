package com.tripflow.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripflow.backend.dto.CreateTripRequest;
import com.tripflow.backend.dto.TripResponse;
import com.tripflow.backend.service.TripService;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/trips")
@AllArgsConstructor
public class TripController {
	private final TripService tripService;


    @PostMapping
    public ResponseEntity<TripResponse> createTrip(
            @RequestBody @Valid CreateTripRequest request,
            Authentication authentication) {
        Long requesterId = getCurrentUserId(authentication);
        TripResponse response = tripService.createTrip(requesterId, request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TripResponse> getTrip(
            @PathVariable Long id,
            Authentication authentication) {
        Long requesterId = getCurrentUserId(authentication);
        TripResponse response = tripService.getTrip(id, requesterId);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    private Long getCurrentUserId(Authentication authentication) {
    	if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user");
        }
        // TODO: dev stub — Pratham's SecurityConfig/JWT filter will populate authentication.getPrincipal()
        // with a real User principal. For now, this accepts userId from test principal() stubs.
        // Swap point: replace the line below with: return ((UserPrincipal) authentication.getPrincipal()).getId();
        // once Pratham's JWT filter is in place. Integration test is blocked until auth is implemented.
        String principal = authentication.getPrincipal().toString();
        return Long.parseLong(principal);
    }

}
