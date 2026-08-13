package com.tripflow.backend.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripflow.backend.client.cloudinary.CloudinaryProperties;
import com.tripflow.backend.client.cloudinary.CloudinarySigningService;
import com.tripflow.backend.client.cloudinary.SignedUploadRequest;
import com.tripflow.backend.domain.Stop;
import com.tripflow.backend.domain.StopPhoto;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.CreateStopPhotoRequest;
import com.tripflow.backend.dto.StopPhotoResponse;
import com.tripflow.backend.exception.ForbiddenException;
import com.tripflow.backend.exception.InvalidPhotoUrlException;
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
    private final CloudinaryProperties cloudinaryProperties;

    @Transactional(readOnly = true)
    public PhotoSignatureResponse getUploadSignature(Long stopId, Long requesterId) {
        Stop stop = loadOwnedStop(stopId, requesterId);
        log.info("Issued photo-upload signature stopId={} requesterId={}", stop.getId(), requesterId);
        SignedUploadRequest signed = signingService.sign(Map.of("folder", "stops/" + stop.getId()));
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
        validateCloudinaryUrl(request.url());

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
        return stopPhotoRepository.findByStopIdOrderByCreatedAtAsc(stop.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deletePhoto(Long stopId, Long photoId, Long requesterId) {
        Stop stop = loadOwnedStop(stopId, requesterId);

        StopPhoto photo = stopPhotoRepository.findByIdAndStopId(photoId, stop.getId())
                // Covers both "doesn't exist" and "belongs to a different stop" — the
                // latter is treated as not found rather than leaking that the id exists
                // elsewhere.
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found: " + photoId));

        stopPhotoRepository.delete(photo);
        log.info("Photo deleted stopId={} photoId={}", stop.getId(), photoId);
    }

    // ---------- helpers ----------

    /**
     * The signed-upload flow (getUploadSignature -> direct-to-Cloudinary) exists
     * specifically so the backend never has to trust an arbitrary client-supplied
     * URL — but nothing previously verified the persisted URL actually came from
     * that flow. Require it to at least point at the configured Cloudinary cloud,
     * so a caller can't attach an arbitrary hotlinked/tracking URL to a photo that
     * gets rendered to any viewer of a public trip.
     */
    private void validateCloudinaryUrl(String url) {
        String expectedHost = "res.cloudinary.com";
        String expectedPrefix = "https://" + expectedHost + "/" + cloudinaryProperties.cloudName() + "/";
        if (!url.startsWith(expectedPrefix)) {
            throw new InvalidPhotoUrlException(
                    "Photo url must be a Cloudinary-hosted URL under the configured cloud");
        }
    }

    private Stop loadStop(Long stopId) {
        return stopRepository.findWithTripAndOwnerById(stopId)
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

    /**
     * Owner or public-trip access — used for listing only.
     *
     * <p>A private trip requested by a non-owner is reported as 404, not 403, matching the
     * SCRUM-71a convention in {@code TripService#getTrip} / {@code TripCloneService} /
     * {@code TripLikeService}. A 403 here confirmed that the stop id exists, turning this
     * endpoint into an existence oracle for stops on other people's private trips.
     * {@link #loadOwnedStop} keeps its 403 — those are owner-only writes, a different case.
     */
    private Stop loadVisibleStop(Long stopId, Long requesterId) {
        Stop stop = loadStop(stopId);
        Trip trip = stop.getTrip();
        boolean isOwner = trip.getUser().getId().equals(requesterId);
        if (trip.getVisibility() == TripVisibility.PRIVATE && !isOwner) {
            log.debug("Private stop-photo list denied (404, existence not disclosed) stopId={} requesterId={}",
                    stopId, requesterId);
            throw new ResourceNotFoundException("Stop not found: " + stopId);
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