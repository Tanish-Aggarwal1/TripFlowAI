package com.tripflow.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripflow.backend.dto.ProfileResponse;
import com.tripflow.backend.dto.UpdateInterestsRequest;
import com.tripflow.backend.security.UserPrincipal;
import com.tripflow.backend.service.ProfileService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * SOCIAL-05 (D-07): every handler resolves its target user from the authenticated principal's
 * user id — this controller declares no request-supplied identity parameter of any kind, so
 * nothing exists that could retarget a read or write at another account (T-06-05-01).
 */
@Tag(name = "Profile", description = "User profile: username, join date, stored interests")
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @Operation(summary = "Get the caller's own profile",
            description = "Username, join date and stored interests for the authenticated user.")
    @GetMapping
    public ResponseEntity<ProfileResponse> getProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(profileService.getProfile(principal.userId()));
    }

    // PATCH on a sub-path, not a whole-profile PUT, keeps username/joinedAt structurally
    // unwritable through this API (T-06-05-03).
    @Operation(summary = "Replace the caller's stored interests",
            description = "Replaces the interests array wholesale, max 20 elements of max 50 characters each.")
    @PatchMapping("/interests")
    public ResponseEntity<ProfileResponse> updateInterests(
            @Valid @RequestBody UpdateInterestsRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(profileService.updateInterests(principal.userId(), request));
    }
}
