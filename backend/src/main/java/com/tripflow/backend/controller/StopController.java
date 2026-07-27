package com.tripflow.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
import com.tripflow.backend.security.UserPrincipal;
import com.tripflow.backend.service.StopService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@Tag(name = "Stops", description = "Nested stop CRUD under a trip — owner only, no public read")
@RestController
@RequestMapping("/api/trips/{tripId}/stops")
@AllArgsConstructor
public class StopController {

    private final StopService stopService;

    @Operation(summary = "List a trip's stops")
    @GetMapping
    public ResponseEntity<List<StopResponse>> listStops(
            @PathVariable Long tripId, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(stopService.listStops(tripId, principal.userId()));
    }

    @Operation(summary = "Add a stop", description = "Appends a stop at the next stopOrder.")
    @PostMapping
    public ResponseEntity<StopResponse> addStop(
            @PathVariable Long tripId,
            @RequestBody @Valid CreateStopRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return new ResponseEntity<>(stopService.addStop(tripId, principal.userId(), request), HttpStatus.CREATED);
    }

    @Operation(summary = "Get a stop")
    @GetMapping("/{stopId}")
    public ResponseEntity<StopResponse> getStop(
            @PathVariable Long tripId, @PathVariable Long stopId, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(stopService.getStop(tripId, stopId, principal.userId()));
    }

    @Operation(summary = "Update a stop", description = "status is optional — omit to leave unchanged.")
    @PutMapping("/{stopId}")
    public ResponseEntity<StopResponse> updateStop(
            @PathVariable Long tripId, @PathVariable Long stopId,
            @RequestBody @Valid UpdateStopRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(stopService.updateStop(tripId, stopId, principal.userId(), request));
    }

    @Operation(summary = "Delete a stop", description = "Remaining stops are automatically renumbered.")
    @DeleteMapping("/{stopId}")
    public ResponseEntity<Void> deleteStop(
            @PathVariable Long tripId, @PathVariable Long stopId, @AuthenticationPrincipal UserPrincipal principal) {
        stopService.deleteStop(tripId, stopId, principal.userId());
        return ResponseEntity.noContent().build();
    }
}