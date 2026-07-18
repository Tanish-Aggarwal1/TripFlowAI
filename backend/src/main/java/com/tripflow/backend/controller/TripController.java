package com.tripflow.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripflow.backend.dto.CreateTripRequest;
import com.tripflow.backend.dto.TripResponse;
import com.tripflow.backend.dto.UpdateTripRequest;
import com.tripflow.backend.service.RouteOptimizationService;
import com.tripflow.backend.service.TripService;
import com.tripflow.backend.util.AuthUtils;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/trips")
@AllArgsConstructor
public class TripController {

    private final TripService tripService;
    private final RouteOptimizationService routeOptimizationService;

    
    @GetMapping
    public ResponseEntity<List<TripResponse>> listTrips(Authentication authentication) {
        Long requesterId = AuthUtils.currentUserId(authentication);
        return ResponseEntity.ok(tripService.listTrips(requesterId));
    }

    @PostMapping
    public ResponseEntity<TripResponse> createTrip(
            @RequestBody @Valid CreateTripRequest request,
            Authentication authentication) {
        Long requesterId = AuthUtils.currentUserId(authentication);
        return new ResponseEntity<>(tripService.createTrip(requesterId, request), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TripResponse> getTrip(
            @PathVariable Long id, Authentication authentication) {
        Long requesterId = AuthUtils.currentUserId(authentication);
        return ResponseEntity.ok(tripService.getTrip(id, requesterId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TripResponse> updateTrip(
            @PathVariable Long id,
            @RequestBody @Valid UpdateTripRequest request,
            Authentication authentication) {
        Long requesterId = AuthUtils.currentUserId(authentication);
        return ResponseEntity.ok(tripService.updateTrip(id, requesterId, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTrip(
            @PathVariable Long id, Authentication authentication) {
        Long requesterId = AuthUtils.currentUserId(authentication);
        tripService.deleteTrip(id, requesterId);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{id}/optimize")
    public ResponseEntity<TripResponse> optimizeTrip(
            @PathVariable Long id, Authentication authentication) {
        Long requesterId = AuthUtils.currentUserId(authentication);
        return ResponseEntity.ok(routeOptimizationService.optimize(id, requesterId));
    }
}