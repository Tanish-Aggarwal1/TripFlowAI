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

import com.tripflow.backend.client.cloudinary.SignedUploadRequest;
import com.tripflow.backend.dto.CreateStopPhotoRequest;
import com.tripflow.backend.dto.StopPhotoResponse;
import com.tripflow.backend.security.UserPrincipal;
import com.tripflow.backend.service.StopPhotoService;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;

@RestController
@RequestMapping("/api/stops/{stopId}")
@AllArgsConstructor
public class StopPhotoController {

    private final StopPhotoService stopPhotoService;

    @PostMapping("/photo-signature")
    public ResponseEntity<SignedUploadRequest> getUploadSignature(
            @PathVariable Long stopId, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(stopPhotoService.getUploadSignature(stopId, principal.userId()));
    }

    @PostMapping("/photos")
    public ResponseEntity<StopPhotoResponse> addPhoto(
            @PathVariable Long stopId,
            @RequestBody @Valid CreateStopPhotoRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return new ResponseEntity<>(
                stopPhotoService.addPhoto(stopId, principal.userId(), request), HttpStatus.CREATED);
    }

    @GetMapping("/photos")
    public ResponseEntity<List<StopPhotoResponse>> listPhotos(
            @PathVariable Long stopId, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(stopPhotoService.listPhotos(stopId, principal.userId()));
    }

    @DeleteMapping("/photos/{photoId}")
    public ResponseEntity<Void> deletePhoto(
            @PathVariable Long stopId, @PathVariable Long photoId,
            @AuthenticationPrincipal UserPrincipal principal) {
        stopPhotoService.deletePhoto(stopId, photoId, principal.userId());
        return ResponseEntity.noContent().build();
    }
}