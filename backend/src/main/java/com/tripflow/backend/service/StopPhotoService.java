package com.tripflow.backend.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripflow.backend.client.cloudinary.CloudinarySigningService;
import com.tripflow.backend.client.cloudinary.SignedUploadRequest;
import com.tripflow.backend.domain.Stop;
import com.tripflow.backend.domain.StopPhoto;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.CreateStopPhotoRequest;
import com.tripflow.backend.dto.StopPhotoResponse;
import com.tripflow.backend.exception.ForbiddenException;
import com.tripflow.backend.exception.ResourceNotFoundException;
import com.tripflow.backend.repository.StopPhotoRepository;
import com.tripflow.backend.repository.StopRepository;
import com.tripflow.backend.dto.PhotoSignatureResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles the presigned-upload flow for stop photos: signing (SCRUM-152),
 * persisting the resulting Cloudinary URL (SCRUM-153), listing, and deleting.
 * Ownership is always checked through the parent {@link Trip}, since a
 * {@link Stop} has no owner of its own.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StopPhotoService {

    private final StopRepository stopRepository;
    private final StopPhotoRepository stopPhotoRepository;
    private final CloudinarySigningService signingService;

 // change this method's signature and body:

    @Transactional(readOnly = true)
    public PhotoSignatureResponse getUploadSignature(Long stopId, Long requesterId) {
        Stop stop = loadOwnedStop(stopId, requesterId);
        log.info("Issued photo-upload signature stopId={} requesterId={}", stop.getId(), requesterId);
        SignedUploadRequest signed = signingService.sign(Map.of());
        return new PhotoSignatureResponse(
                signed.cloudName(),
                signed.apiKey(),
                signed.timestamp(),
                signed.signature(),
                signed.uploadParams()
        );
    }

    @Transactional
    public StopPhotoResponse addPhoto(Long stopId, Long requesterId, CreateStopPhotoRequest request) {
        Stop stop = loadOwnedStop(stopId, requesterId);

        StopPhoto photo = new StopPhoto();
        photo.setStop(stop);
        photo.setUrl(request.url());
        photo.setCloudinaryPublicId(request.cloudinaryPublicId());
        photo.setCaption(request.caption());

        StopPhoto saved = stopPhotoRepository.save(photo);
        log.info("Photo persisted stopId={} photoId={}", stop.getId(), saved.getId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<StopPhotoResponse> listPhotos(Long stopId, Long requesterId) {
        Stop stop = loadVisibleStop(stopId, requesterId);
        return stopPhotoRepository.findByStopId(stop.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deletePhoto(Long stopId, Long photoId, Long requesterId) {
        Stop stop = loadOwnedStop(stopId, requesterId);

        StopPhoto photo = stopPhotoRepository.findById(photoId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found: " + photoId));

        if (!photo.getStop().getId().equals(stop.getId())) {
            // Photo exists but belongs to a different stop — treat as not found
            // rather than leaking that the id exists elsewhere.
            throw new ResourceNotFoundException("Photo not found: " + photoId);
        }

        stopPhotoRepository.delete(photo);
        log.info("Photo deleted stopId={} photoId={}", stop.getId(), photoId);
    }

    // ---------- helpers ----------

    private Stop loadStop(Long stopId) {
        return stopRepository.findById(stopId)
                .orElseThrow(() -> new ResourceNotFoundException("Stop not found: " + stopId));
    }

    /** Owner-only access — used for signature, create, and delete. */
    private Stop loadOwnedStop(Long stopId, Long requesterId) {
        Stop stop = loadStop(stopId);
        Trip trip = stop.getTrip();
        if (!trip.getUser().getId().equals(requesterId)) {
            log.debug("Stop-photo ownership check failed stopId={} ownerId={} requesterId={}",
                    stopId, trip.getUser().getId(), requesterId);
            throw new ForbiddenException("You do not have access to this stop");
        }
        return stop;
    }

    /** Owner or public-trip access — used for listing only. */
    private Stop loadVisibleStop(Long stopId, Long requesterId) {
        Stop stop = loadStop(stopId);
        Trip trip = stop.getTrip();
        boolean isOwner = trip.getUser().getId().equals(requesterId);
        if (trip.getVisibility() == TripVisibility.PRIVATE && !isOwner) {
            log.debug("Private stop-photo list denied stopId={} requesterId={}", stopId, requesterId);
            throw new ForbiddenException("You do not have access to this stop");
        }
        return stop;
    }

    private StopPhotoResponse toResponse(StopPhoto photo) {
        return new StopPhotoResponse(
                photo.getId(),
                photo.getStop().getId(),
                photo.getUrl(),
                photo.getCloudinaryPublicId(),
                photo.getCaption(),
                photo.getCreatedAt()
        );
    }
}