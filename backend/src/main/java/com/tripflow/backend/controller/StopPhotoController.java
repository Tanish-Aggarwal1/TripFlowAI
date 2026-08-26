package com.tripflow.backend.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripflow.backend.dto.PhotoSignatureResponse;
import com.tripflow.backend.dto.CreateStopPhotoRequest;
import com.tripflow.backend.dto.StopPhotoResponse;
import com.tripflow.backend.ratelimit.RateLimitProperties;
import com.tripflow.backend.ratelimit.RateLimiterService;
import com.tripflow.backend.security.UserPrincipal;
import com.tripflow.backend.service.StopPhotoService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@Tag(name = "Stop Photos", description = "Direct-to-Cloudinary photo upload — signature issuance and photo reference CRUD")
@RestController
@RequestMapping("/api/stops/{stopId}")
@AllArgsConstructor
public class StopPhotoController {

    private final StopPhotoService stopPhotoService;
    private final RateLimiterService rateLimiterService;
    private final RateLimitProperties rateLimitProperties;

    @Operation(summary = "Get a Cloudinary upload signature", description = "Owner-only. Issues signed upload parameters for the client to upload directly to Cloudinary.")
    @PostMapping("/photo-signature")
    public ResponseEntity<PhotoSignatureResponse> getUploadSignature(
            @PathVariable Long stopId, @AuthenticationPrincipal UserPrincipal principal) {
        rateLimiterService.checkLimit("photo-signature:" + principal.userId(), rateLimitProperties.photoSignature());
        return ResponseEntity.ok(stopPhotoService.getUploadSignature(stopId, principal.userId()));
    }

    @Operation(summary = "Add a photo", description = "Owner-only. Persists a photo reference after the client has uploaded directly to Cloudinary using the signature above.")
    @PostMapping("/photos")
    public ResponseEntity<StopPhotoResponse> addPhoto(
            @PathVariable Long stopId,
            @RequestBody @Valid CreateStopPhotoRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return new ResponseEntity<>(
                stopPhotoService.addPhoto(stopId, principal.userId(), request), HttpStatus.CREATED);
    }

    @Operation(summary = "List a stop's photos", description = "Owner sees any stop's photos; non-owner only if the stop's parent trip is PUBLIC.")
    @GetMapping("/photos")
    public ResponseEntity<List<StopPhotoResponse>> listPhotos(
            @PathVariable Long stopId, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(stopPhotoService.listPhotos(stopId, principal.userId()));
    }

    @Operation(summary = "Delete a photo", description = "Owner-only.")
    @DeleteMapping("/photos/{photoId}")
    public ResponseEntity<Void> deletePhoto(
            @PathVariable Long stopId, @PathVariable Long photoId,
            @AuthenticationPrincipal UserPrincipal principal) {
        stopPhotoService.deletePhoto(stopId, photoId, principal.userId());
        return ResponseEntity.noContent().build();
    }
}