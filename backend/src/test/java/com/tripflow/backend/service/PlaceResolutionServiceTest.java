package com.tripflow.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.tripflow.backend.domain.Place;
import com.tripflow.backend.dto.CreateStopRequest;
import com.tripflow.backend.repository.PlaceRepository;

/**
 * Place resolution/dedup only — extracted out of {@link TripServiceTest} (SCRUM-215/239).
 */
@ExtendWith(MockitoExtension.class)
class PlaceResolutionServiceTest {

    @Mock private PlaceRepository placeRepository;

    private PlaceResolutionService placeResolutionService;

    @BeforeEach
    void setUp() {
        placeResolutionService = new PlaceResolutionService(placeRepository);
        // No Spring context here, so wire the self-proxy field to `this` directly —
        // see PlaceResolutionService.self for why it exists.
        placeResolutionService.self = placeResolutionService;
    }

    @Test
    void resolvePlace_existingByExternalPlaceId_reusesRow() {
        Place existing = new Place();
        existing.setId(10L);
        existing.setExternalPlaceId("ext-1");
        when(placeRepository.findByExternalPlaceId("ext-1")).thenReturn(Optional.of(existing));

        Place result = placeResolutionService.resolvePlace("Cafe", 1.0, 2.0, null, "ext-1");

        assertThat(result).isSameAs(existing);
        verify(placeRepository, times(0)).save(any());
    }

    @Test
    void resolvePlace_existingByNameAndCoordinates_reusesRow() {
        Place existing = new Place();
        existing.setId(11L);
        when(placeRepository.findFirstByNameAndLatitudeAndLongitudeOrderById("Cafe", 1.0, 2.0))
                .thenReturn(Optional.of(existing));

        Place result = placeResolutionService.resolvePlace("Cafe", 1.0, 2.0, null, null);

        assertThat(result).isSameAs(existing);
        verify(placeRepository, times(0)).save(any());
    }

    @Test
    void resolvePlace_noExistingRow_savesNewPlace() {
        when(placeRepository.findFirstByNameAndLatitudeAndLongitudeOrderById("Cafe", 1.0, 2.0))
                .thenReturn(Optional.empty());
        when(placeRepository.save(any())).thenAnswer(inv -> {
            Place p = inv.getArgument(0, Place.class);
            p.setId(12L);
            return p;
        });

        Place result = placeResolutionService.resolvePlace("Cafe", 1.0, 2.0, "123 Main St", null);

        assertThat(result.getId()).isEqualTo(12L);
        assertThat(result.getName()).isEqualTo("Cafe");
        assertThat(result.getAddress()).isEqualTo("123 Main St");
    }

    @Test
    void resolvePlace_convenienceOverload_delegatesUsingRequestFields() {
        when(placeRepository.findFirstByNameAndLatitudeAndLongitudeOrderById("Cottage", 45.0, -79.9))
                .thenReturn(Optional.empty());
        when(placeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Place.class));

        CreateStopRequest request = new CreateStopRequest("Cottage", 45.0, -79.9, null, null, null);
        Place result = placeResolutionService.resolvePlace(request);

        assertThat(result.getName()).isEqualTo("Cottage");
    }

    @Test
    void resolvePlace_saveRacesUniqueIndex_reusesWinningRow() {
        Place winningRow = new Place();
        winningRow.setId(30L);
        winningRow.setName("Shared Cafe");
        winningRow.setExternalPlaceId("ext-123");

        when(placeRepository.findByExternalPlaceId("ext-123"))
                .thenReturn(Optional.empty(), Optional.of(winningRow));
        when(placeRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        Place result = placeResolutionService.resolvePlace("Shared Cafe", 44.0, -79.0, null, "ext-123");

        assertThat(result).isSameAs(winningRow);
        verify(placeRepository).save(any());
        verify(placeRepository, times(2)).findByExternalPlaceId("ext-123");
    }

    @Test
    void resolvePlace_saveFailsForUnrelatedReason_propagatesWhenNoExistingRowFound() {
        when(placeRepository.findFirstByNameAndLatitudeAndLongitudeOrderById(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(placeRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> placeResolutionService.resolvePlace("New Place", 44.0, -79.0, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- coordinate rounding (SCRUM-216) ----------

    @Test
    void resolvePlace_roundsCoordinatesTo5DecimalPlacesBeforeLookup() {
        when(placeRepository.findFirstByNameAndLatitudeAndLongitudeOrderById("Cafe", 45.00001, -79.00001))
                .thenReturn(Optional.empty());
        when(placeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Place.class));

        // 6th decimal place onward must be rounded away before the lookup runs.
        placeResolutionService.resolvePlace("Cafe", 45.000009, -79.000011, null, null);

        verify(placeRepository).findFirstByNameAndLatitudeAndLongitudeOrderById("Cafe", 45.00001, -79.00001);
    }

    @Test
    void resolvePlace_roundsCoordinatesBeforePersistingNewPlace() {
        when(placeRepository.findFirstByNameAndLatitudeAndLongitudeOrderById(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(placeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Place.class));

        Place result = placeResolutionService.resolvePlace("Cafe", 45.000009, -79.000011, null, null);

        assertThat(result.getLatitude()).isEqualTo(45.00001);
        assertThat(result.getLongitude()).isEqualTo(-79.00001);
    }

    @Test
    void resolvePlace_nearIdenticalCoordinatesWithinPrecision_dedupToSameRow() {
        Place existing = new Place();
        existing.setId(40L);
        // Both 45.0 and 45.000000001 round to the same 45.0 at 5 decimal places.
        when(placeRepository.findFirstByNameAndLatitudeAndLongitudeOrderById("Cafe", 45.0, -79.0))
                .thenReturn(Optional.of(existing));

        Place result = placeResolutionService.resolvePlace("Cafe", 45.000000001, -79.0, null, null);

        assertThat(result).isSameAs(existing);
    }

    // ---------- resolvePlaces (batch, SCRUM-267) ----------

    @Test
    void resolvePlaces_emptyList_returnsEmptyWithoutQuerying() {
        List<Place> result = placeResolutionService.resolvePlaces(List.of());

        assertThat(result).isEmpty();
        verify(placeRepository, times(0)).findByExternalPlaceIdIn(any());
        verify(placeRepository, times(0)).findByNameIn(any());
    }

    @Test
    void resolvePlaces_matchesByExternalIdAndByNameCoordinates_inOneQueryEach() {
        Place byExternal = new Place();
        byExternal.setId(1L);
        byExternal.setExternalPlaceId("ext-1");
        Place byNameCoords = new Place();
        byNameCoords.setId(2L);
        byNameCoords.setName("Cafe");
        byNameCoords.setLatitude(1.0);
        byNameCoords.setLongitude(2.0);

        when(placeRepository.findByExternalPlaceIdIn(any())).thenReturn(List.of(byExternal));
        when(placeRepository.findByNameIn(any())).thenReturn(List.of(byNameCoords));

        List<CreateStopRequest> requests = List.of(
                new CreateStopRequest("Museum", 10.0, 20.0, null, "ext-1", null),
                new CreateStopRequest("Cafe", 1.0, 2.0, null, null, null));

        List<Place> results = placeResolutionService.resolvePlaces(requests);

        assertThat(results).containsExactly(byExternal, byNameCoords);
        verify(placeRepository, times(1)).findByExternalPlaceIdIn(any());
        verify(placeRepository, times(1)).findByNameIn(any());
        verify(placeRepository, times(0)).save(any());
    }

    @Test
    void resolvePlaces_noMatch_createsNewPlacePerRequest() {
        when(placeRepository.findByNameIn(any())).thenReturn(List.of());
        when(placeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Place.class));

        List<CreateStopRequest> requests = List.of(
                new CreateStopRequest("A", 1.0, 1.0, null, null, null),
                new CreateStopRequest("B", 2.0, 2.0, null, null, null));

        List<Place> results = placeResolutionService.resolvePlaces(requests);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getName()).isEqualTo("A");
        assertThat(results.get(1).getName()).isEqualTo("B");
        verify(placeRepository, times(2)).save(any());
    }

    @Test
    void resolvePlaces_duplicateRequestsWithinSameBatch_reuseTheSameNewlyCreatedPlace() {
        when(placeRepository.findByNameIn(any())).thenReturn(List.of());
        when(placeRepository.save(any())).thenAnswer(inv -> {
            Place p = inv.getArgument(0, Place.class);
            p.setId(99L);
            return p;
        });

        List<CreateStopRequest> requests = List.of(
                new CreateStopRequest("Same Spot", 5.0, 5.0, null, null, null),
                new CreateStopRequest("Same Spot", 5.0, 5.0, null, null, null));

        List<Place> results = placeResolutionService.resolvePlaces(requests);

        assertThat(results.get(0)).isSameAs(results.get(1));
        // Only the first duplicate should hit save() — the second must reuse the
        // in-batch cache instead of racing its own insert.
        verify(placeRepository, times(1)).save(any());
    }

    @Test
    void resolvePlaces_roundsCoordinatesBeforeMatchingByNameCoordinates() {
        Place existing = new Place();
        existing.setId(5L);
        existing.setName("Cafe");
        existing.setLatitude(45.00001);
        existing.setLongitude(-79.00001);

        when(placeRepository.findByNameIn(any())).thenReturn(List.of(existing));

        List<CreateStopRequest> requests = List.of(
                new CreateStopRequest("Cafe", 45.000009, -79.000011, null, null, null));

        List<Place> results = placeResolutionService.resolvePlaces(requests);

        assertThat(results).containsExactly(existing);
        verify(placeRepository, times(0)).save(any());
    }
}
