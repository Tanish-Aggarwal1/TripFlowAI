package com.tripflow.backend.service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tripflow.backend.client.cloudinary.CloudinaryProperties;
import com.tripflow.backend.client.cloudinary.CloudinarySigningService;
import com.tripflow.backend.client.cloudinary.SignedUploadRequest;
import com.tripflow.backend.domain.Stop;
import com.tripflow.backend.domain.StopPhoto;
import com.tripflow.backend.domain.Trip;
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
    private final TripOwnershipService tripOwnershipService;

    // Constrains what a signature authorizes: format is Cloudinary-validated against actual
    // file content (not just extension), so this is the real backstop against a signature
    // meant for a photo being reused to upload video/raw content. public_id is server-generated
    // per signature so one signature can create exactly one asset, not an arbitrary number.
    private static final List<String> ALLOWED_PHOTO_FORMATS = List.of("jpg", "jpeg", "png", "webp", "gif");
    private static final long MAX_UPLOAD_BYTES = 10_000_000; // 10MB

    @Transactional(readOnly = true)
    public PhotoSignatureResponse getUploadSignature(Long stopId, Long requesterId) {
        Stop stop = loadOwnedStop(stopId, requesterId);
        log.info("Issued photo-upload signature stopId={} requesterId={}", stop.getId(), requesterId);
        SignedUploadRequest signed = signingService.sign(Map.of(
                "folder", "stops/" + stop.getId(),
                "public_id", UUID.randomUUID().toString(),
                "allowed_formats", ALLOWED_PHOTO_FORMATS,
                "max_file_size", MAX_UPLOAD_BYTES));
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
     *
     * <p>SCRUM-496: a plain prefix match ("starts with .../{cloud}/") is not enough —
     * Cloudinary's fetch delivery type (".../{cloud}/image/fetch/https://attacker...")
     * satisfies any such prefix while proxying arbitrary attacker-controlled content.
     * The signed-upload flow above only ever produces {@code upload}-delivery URLs
     * (it always signs {@code folder}, and the frontend's upload call hits
     * {@code /image/upload}), so the delivery-type path segment is checked structurally
     * and only {@code upload} is allowlisted.
     */
    private void validateCloudinaryUrl(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException ex) {
            throw new InvalidPhotoUrlException("Photo url is not a valid URL");
        }

        String path = uri.getPath() == null ? "" : uri.getPath();
        String[] segments = path.split("/");
        // path is "/{cloud}/{resourceType}/{deliveryType}/..." -> split gives
        // ["", cloud, resourceType, deliveryType, ...]
        boolean valid = "https".equals(uri.getScheme())
                && "res.cloudinary.com".equals(uri.getHost())
                && segments.length >= 4
                && cloudinaryProperties.cloudName().equals(segments[1])
                && "upload".equals(segments[3]);

        if (!valid) {
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
     *
     * <p>The rule itself is delegated to {@code TripOwnershipService.isVisible} (SCRUM-419)
     * rather than a load-and-throw variant, since the {@link Trip} here is already in hand
     * via the parent {@link Stop} fetch — a second repository call would be redundant.
     */
    private Stop loadVisibleStop(Long stopId, Long requesterId) {
        Stop stop = loadStop(stopId);
        if (!tripOwnershipService.isVisible(stop.getTrip(), requesterId)) {
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