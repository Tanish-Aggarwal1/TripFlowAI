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

import com.tripflow.backend.dto.CreateStopRequest;
import com.tripflow.backend.dto.StopResponse;
import com.tripflow.backend.dto.UpdateStopRequest;
import com.tripflow.backend.service.TripService;
import com.tripflow.backend.util.AuthUtils;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/trips/{tripId}/stops")
@AllArgsConstructor
public class StopController {

    private final TripService tripService;

    @GetMapping
    public ResponseEntity<List<StopResponse>> listStops(
            @PathVariable Long tripId, Authentication authentication) {
        Long requesterId = AuthUtils.currentUserId(authentication);
        return ResponseEntity.ok(tripService.listStops(tripId, requesterId));
    }

    @PostMapping
    public ResponseEntity<StopResponse> addStop(
            @PathVariable Long tripId,
            @RequestBody @Valid CreateStopRequest request,
            Authentication authentication) {
        Long requesterId = AuthUtils.currentUserId(authentication);
        return new ResponseEntity<>(tripService.addStop(tripId, requesterId, request), HttpStatus.CREATED);
    }

    @GetMapping("/{stopId}")
    public ResponseEntity<StopResponse> getStop(
            @PathVariable Long tripId, @PathVariable Long stopId, Authentication authentication) {
        Long requesterId = AuthUtils.currentUserId(authentication);
        return ResponseEntity.ok(tripService.getStop(tripId, stopId, requesterId));
    }

    @PutMapping("/{stopId}")
    public ResponseEntity<StopResponse> updateStop(
            @PathVariable Long tripId, @PathVariable Long stopId,
            @RequestBody @Valid UpdateStopRequest request,
            Authentication authentication) {
        Long requesterId = AuthUtils.currentUserId(authentication);
        return ResponseEntity.ok(tripService.updateStop(tripId, stopId, requesterId, request));
    }

    @DeleteMapping("/{stopId}")
    public ResponseEntity<Void> deleteStop(
            @PathVariable Long tripId, @PathVariable Long stopId, Authentication authentication) {
        Long requesterId = AuthUtils.currentUserId(authentication);
        tripService.deleteStop(tripId, stopId, requesterId);
        return ResponseEntity.noContent().build();
    }
}