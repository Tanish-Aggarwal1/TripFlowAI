package com.tripflow.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        when(placeRepository.findByNameAndLatitudeAndLongitude("Cafe", 1.0, 2.0))
                .thenReturn(Optional.of(existing));

        Place result = placeResolutionService.resolvePlace("Cafe", 1.0, 2.0, null, null);

        assertThat(result).isSameAs(existing);
        verify(placeRepository, times(0)).save(any());
    }

    @Test
    void resolvePlace_noExistingRow_savesNewPlace() {
        when(placeRepository.findByNameAndLatitudeAndLongitude("Cafe", 1.0, 2.0))
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
        when(placeRepository.findByNameAndLatitudeAndLongitude("Cottage", 45.0, -79.9))
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
        when(placeRepository.findByNameAndLatitudeAndLongitude(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(placeRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> placeResolutionService.resolvePlace("New Place", 44.0, -79.0, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
