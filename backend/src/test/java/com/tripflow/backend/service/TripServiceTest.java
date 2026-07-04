package com.tripflow.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.tripflow.backend.beans.Trip;
import com.tripflow.backend.beans.User;
import com.tripflow.backend.beans.enums.TripVisibility;
import com.tripflow.backend.dto.CreateStopRequest;
import com.tripflow.backend.dto.CreateTripRequest;
import com.tripflow.backend.dto.TripResponse;
import com.tripflow.backend.exception.ForbiddenException;
import com.tripflow.backend.exception.ResourceNotFoundException;
import com.tripflow.backend.repository.PlaceRepository;
import com.tripflow.backend.repository.TripRepository;
import com.tripflow.backend.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
public class TripServiceTest {

	@Mock private TripRepository tripRepository;
    @Mock private UserRepository userRepository;
    @Mock private PlaceRepository placeRepository;

    private TripService tripService;

    @BeforeEach
    void setUp() {
        tripService = new TripService(tripRepository, userRepository, placeRepository);
    }

    @Test
    void createTrip_happyPath_savesAndReturnsResponse() {
        User owner = new User();
        owner.setId(1L);
        owner.setUsername("tanish");
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(placeRepository.findByNameAndLatitudeAndLongitude(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(placeRepository.save(any())).thenAnswer(inv -> {
            var place = inv.getArgument(0, com.tripflow.backend.beans.Place.class);
            place.setId(10L);
            return place;
        });
        when(tripRepository.save(any())).thenAnswer(inv -> {
            Trip t = inv.getArgument(0, Trip.class);
            t.setId(100L);
            return t;
        });

        CreateStopRequest stopReq = new CreateStopRequest();
        stopReq.setName("Cottage");
        stopReq.setLatitude(45.0);
        stopReq.setLongitude(-79.9);

        CreateTripRequest request = new CreateTripRequest();
        request.setTitle("Weekend Trip");
        request.setVisibility(TripVisibility.PRIVATE);
        request.setStops(List.of(stopReq));

        TripResponse response = tripService.createTrip(1L, request);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getTitle()).isEqualTo("Weekend Trip");
        assertThat(response.getStops()).hasSize(1);
        assertThat(response.getStops().get(0).getStopOrder()).isEqualTo(0);
    }

    @Test
    void getTrip_privateTripNonOwner_throwsForbidden() {
        User owner = new User();
        owner.setId(1L);

        Trip trip = new Trip();
        trip.setId(50L);
        trip.setUser(owner);
        trip.setVisibility(TripVisibility.PRIVATE);

        when(tripRepository.findById(50L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.getTrip(50L, 2L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getTrip_missingTrip_throwsNotFound() {
        when(tripRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.getTrip(999L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getTrip_privateTripOwner_succeeds() {
        User owner = new User();
        owner.setId(1L);
        owner.setUsername("tanish");

        Trip trip = new Trip();
        trip.setId(50L);
        trip.setUser(owner);
        trip.setVisibility(TripVisibility.PRIVATE);
        trip.setTitle("My Trip");
        trip.setStops(List.of());

        when(tripRepository.findById(50L)).thenReturn(Optional.of(trip));

        TripResponse response = tripService.getTrip(50L, 1L);

        assertThat(response.getTitle()).isEqualTo("My Trip");
    }
}
