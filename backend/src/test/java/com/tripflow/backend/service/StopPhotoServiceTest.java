package com.tripflow.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.tripflow.backend.client.cloudinary.CloudinaryProperties;
import com.tripflow.backend.client.cloudinary.CloudinarySigningService;
import com.tripflow.backend.client.cloudinary.SignedUploadRequest;
import com.tripflow.backend.domain.Stop;
import com.tripflow.backend.domain.StopPhoto;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.CreateStopPhotoRequest;
import com.tripflow.backend.dto.PhotoSignatureResponse;
import com.tripflow.backend.dto.StopPhotoResponse;
import com.tripflow.backend.exception.ForbiddenException;
import com.tripflow.backend.exception.InvalidPhotoUrlException;
import com.tripflow.backend.exception.ResourceNotFoundException;
import com.tripflow.backend.repository.StopPhotoRepository;
import com.tripflow.backend.repository.StopRepository;

/**
 * Mirrors {@link TripOwnershipServiceTest}/{@link StopServiceTest}: fast Mockito-only
 * coverage of the owner-vs-public-visibility predicates ({@code loadOwnedStop} vs
 * {@code loadVisibleStop}) that {@link StopPhotoServiceTest}'s IT sibling
 * ({@code StopPhotoControllerIT}) only exercises indirectly through full HTTP + Testcontainers.
 */
@ExtendWith(MockitoExtension.class)
class StopPhotoServiceTest {

    @Mock private StopRepository stopRepository;
    @Mock private StopPhotoRepository stopPhotoRepository;
    @Mock private CloudinarySigningService signingService;

    private final CloudinaryProperties cloudinaryProperties =
            new CloudinaryProperties("demo", "key", "secret");

    private StopPhotoService stopPhotoService;

    @BeforeEach
    void setUp() {
        stopPhotoService = new StopPhotoService(
                stopRepository, stopPhotoRepository, signingService, cloudinaryProperties);
    }

    private Stop stopOwnedBy(Long stopId, Long ownerId, TripVisibility visibility) {
        User owner = new User();
        owner.setId(ownerId);

        Trip trip = new Trip();
        trip.setId(50L);
        trip.setUser(owner);
        trip.setVisibility(visibility);

        Stop stop = new Stop();
        stop.setId(stopId);
        stop.setTrip(trip);
        return stop;
    }

    // ---------- getUploadSignature (owner-only) ----------

    @Test
    void getUploadSignature_owner_returnsSignature() {
        Stop stop = stopOwnedBy(1L, 10L, TripVisibility.PRIVATE);
        when(stopRepository.findWithTripAndOwnerById(1L)).thenReturn(Optional.of(stop));
        when(signingService.sign(anyMap())).thenReturn(
                new SignedUploadRequest("cloud", "key", 123L, "sig", Map.of("folder", "stops/1")));

        PhotoSignatureResponse response = stopPhotoService.getUploadSignature(1L, 10L);

        assertThat(response.signature()).isEqualTo("sig");
        assertThat(response.uploadParams()).containsEntry("folder", "stops/1");
    }

    @Test
    void getUploadSignature_constrainsFormatSizeAndPinsAPublicId() {
        Stop stop = stopOwnedBy(1L, 10L, TripVisibility.PRIVATE);
        when(stopRepository.findWithTripAndOwnerById(1L)).thenReturn(Optional.of(stop));
        when(signingService.sign(anyMap())).thenReturn(
                new SignedUploadRequest("cloud", "key", 123L, "sig", Map.of("folder", "stops/1")));

        stopPhotoService.getUploadSignature(1L, 10L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(signingService).sign(captor.capture());
        Map<String, Object> signedParams = captor.getValue();

        assertThat(signedParams).containsEntry("allowed_formats", List.of("jpg", "jpeg", "png", "webp", "gif"));
        assertThat(signedParams).containsEntry("max_file_size", 10_000_000L);
        assertThat(signedParams).containsKey("public_id");
        assertThat(signedParams.get("public_id")).asString().isNotBlank();
    }

    @Test
    void getUploadSignature_nonOwner_throwsForbidden() {
        Stop stop = stopOwnedBy(1L, 10L, TripVisibility.PRIVATE);
        when(stopRepository.findWithTripAndOwnerById(1L)).thenReturn(Optional.of(stop));

        assertThatThrownBy(() -> stopPhotoService.getUploadSignature(1L, 99L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getUploadSignature_missingStop_throwsNotFound() {
        when(stopRepository.findWithTripAndOwnerById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stopPhotoService.getUploadSignature(999L, 10L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- addPhoto (owner-only) ----------

    @Test
    void addPhoto_owner_persistsAndReturnsResponse() {
        Stop stop = stopOwnedBy(1L, 10L, TripVisibility.PRIVATE);
        when(stopRepository.findWithTripAndOwnerById(1L)).thenReturn(Optional.of(stop));
        when(stopPhotoRepository.save(any(StopPhoto.class))).thenAnswer(inv -> {
            StopPhoto photo = inv.getArgument(0, StopPhoto.class);
            photo.setId(7L);
            return photo;
        });

        CreateStopPhotoRequest request = new CreateStopPhotoRequest(
                "https://res.cloudinary.com/demo/image/upload/v1/mock.jpg", "pub-id", "A view");

        StopPhotoResponse response = stopPhotoService.addPhoto(1L, 10L, request);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.stopId()).isEqualTo(1L);
        assertThat(response.url()).isEqualTo(request.url());
        assertThat(response.caption()).isEqualTo("A view");
    }

    @Test
    void addPhoto_nonOwner_throwsForbidden() {
        Stop stop = stopOwnedBy(1L, 10L, TripVisibility.PRIVATE);
        when(stopRepository.findWithTripAndOwnerById(1L)).thenReturn(Optional.of(stop));

        CreateStopPhotoRequest request = new CreateStopPhotoRequest(
                "https://res.cloudinary.com/demo/image/upload/v1/mock.jpg", null, null);

        assertThatThrownBy(() -> stopPhotoService.addPhoto(1L, 99L, request))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void addPhoto_fetchDeliveryUrl_throwsInvalidPhotoUrl() {
        // SCRUM-496: a prefix match alone lets Cloudinary's fetch delivery type through,
        // which proxies arbitrary attacker-controlled remote content.
        Stop stop = stopOwnedBy(1L, 10L, TripVisibility.PRIVATE);
        when(stopRepository.findWithTripAndOwnerById(1L)).thenReturn(Optional.of(stop));

        CreateStopPhotoRequest request = new CreateStopPhotoRequest(
                "https://res.cloudinary.com/demo/image/fetch/https://attacker.example/beacon.png", null, null);

        assertThatThrownBy(() -> stopPhotoService.addPhoto(1L, 10L, request))
                .isInstanceOf(InvalidPhotoUrlException.class);
    }

    @Test
    void addPhoto_wrongCloudName_throwsInvalidPhotoUrl() {
        Stop stop = stopOwnedBy(1L, 10L, TripVisibility.PRIVATE);
        when(stopRepository.findWithTripAndOwnerById(1L)).thenReturn(Optional.of(stop));

        CreateStopPhotoRequest request = new CreateStopPhotoRequest(
                "https://res.cloudinary.com/some-other-cloud/image/upload/v1/mock.jpg", null, null);

        assertThatThrownBy(() -> stopPhotoService.addPhoto(1L, 10L, request))
                .isInstanceOf(InvalidPhotoUrlException.class);
    }

    // ---------- listPhotos (owner OR public-trip) ----------

    @Test
    void listPhotos_owner_returnsPhotos() {
        Stop stop = stopOwnedBy(1L, 10L, TripVisibility.PRIVATE);
        when(stopRepository.findWithTripAndOwnerById(1L)).thenReturn(Optional.of(stop));
        StopPhoto photo = new StopPhoto();
        photo.setId(5L);
        photo.setStop(stop);
        photo.setUrl("https://res.cloudinary.com/demo/image/upload/v1/a.jpg");
        when(stopPhotoRepository.findByStopIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(photo));

        List<StopPhotoResponse> photos = stopPhotoService.listPhotos(1L, 10L);

        assertThat(photos).hasSize(1);
        assertThat(photos.get(0).id()).isEqualTo(5L);
    }

    @Test
    void listPhotos_nonOwnerOnPublicTrip_returnsPhotos() {
        Stop stop = stopOwnedBy(1L, 10L, TripVisibility.PUBLIC);
        when(stopRepository.findWithTripAndOwnerById(1L)).thenReturn(Optional.of(stop));
        when(stopPhotoRepository.findByStopIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());

        List<StopPhotoResponse> photos = stopPhotoService.listPhotos(1L, 99L);

        assertThat(photos).isEmpty();
    }

    /**
     * Not-found, not forbidden: a 403 would confirm the stop id exists, leaking the existence
     * of stops on other people's private trips. Same SCRUM-71a rule as TripService#getTrip.
     * The owner-only paths above/below keep throwing ForbiddenException.
     */
    @Test
    void listPhotos_nonOwnerOnPrivateTrip_throwsNotFoundNotForbidden() {
        Stop stop = stopOwnedBy(1L, 10L, TripVisibility.PRIVATE);
        when(stopRepository.findWithTripAndOwnerById(1L)).thenReturn(Optional.of(stop));

        assertThatThrownBy(() -> stopPhotoService.listPhotos(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .isNotInstanceOf(ForbiddenException.class)
                .hasMessage("Stop not found: 1");
    }

    @Test
    void listPhotos_missingStop_throwsNotFound() {
        when(stopRepository.findWithTripAndOwnerById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stopPhotoService.listPhotos(999L, 10L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- deletePhoto (owner-only) ----------

    @Test
    void deletePhoto_owner_deletes() {
        Stop stop = stopOwnedBy(1L, 10L, TripVisibility.PRIVATE);
        when(stopRepository.findWithTripAndOwnerById(1L)).thenReturn(Optional.of(stop));
        StopPhoto photo = new StopPhoto();
        photo.setId(5L);
        photo.setStop(stop);
        when(stopPhotoRepository.findByIdAndStopId(5L, 1L)).thenReturn(Optional.of(photo));

        stopPhotoService.deletePhoto(1L, 5L, 10L);

        org.mockito.Mockito.verify(stopPhotoRepository).delete(photo);
    }

    @Test
    void deletePhoto_nonOwner_throwsForbidden() {
        Stop stop = stopOwnedBy(1L, 10L, TripVisibility.PRIVATE);
        when(stopRepository.findWithTripAndOwnerById(1L)).thenReturn(Optional.of(stop));

        assertThatThrownBy(() -> stopPhotoService.deletePhoto(1L, 5L, 99L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deletePhoto_missingStop_throwsNotFound() {
        when(stopRepository.findWithTripAndOwnerById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stopPhotoService.deletePhoto(999L, 5L, 10L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deletePhoto_photoBelongsToDifferentStop_throwsNotFound() {
        // Mirrors StopPhotoControllerIT.deletePhoto_photoBelongsToDifferentStop_returns404
        // at the unit level: the photo exists but under a different stop id, so the
        // findByIdAndStopId lookup (not a separate "wrong stop" check) is what must 404 it.
        Stop stop = stopOwnedBy(1L, 10L, TripVisibility.PRIVATE);
        when(stopRepository.findWithTripAndOwnerById(1L)).thenReturn(Optional.of(stop));
        when(stopPhotoRepository.findByIdAndStopId(5L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stopPhotoService.deletePhoto(1L, 5L, 10L))
                .isInstanceOf(ResourceNotFoundException.class);

        org.mockito.Mockito.verify(stopPhotoRepository, org.mockito.Mockito.never()).delete(any());
    }
}
