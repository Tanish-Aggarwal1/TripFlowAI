package com.tripflow.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.tripflow.backend.domain.Place;
import com.tripflow.backend.domain.Stop;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.domain.enums.StopStatus;
import com.tripflow.backend.dto.CreateStopRequest;
import com.tripflow.backend.dto.StopResponse;
import com.tripflow.backend.dto.UpdateStopRequest;
import com.tripflow.backend.exception.ForbiddenException;
import com.tripflow.backend.exception.ResourceNotFoundException;
import com.tripflow.backend.mapper.StopMapper;
import com.tripflow.backend.repository.TripRepository;

/**
 * Nested stop CRUD only — trip CRUD moved to {@link TripServiceTest}, place resolution to
 * {@link PlaceResolutionServiceTest} (SCRUM-215/239).
 */
@ExtendWith(MockitoExtension.class)
class StopServiceTest {

    @Mock private TripRepository tripRepository;
    @Mock private PlaceResolutionService placeResolutionService;

    private StopService stopService;

    @BeforeEach
    void setUp() {
        StopMapper stopMapper = new StopMapper();
        // Real TripOwnershipService (not mocked) — it's a thin delegate to tripRepository,
        // so every existing `when(tripRepository.findWithStopsById(...))` stub below still
        // drives the ownership check unchanged.
        TripOwnershipService tripOwnershipService = new TripOwnershipService(tripRepository);
        stopService = new StopService(tripRepository, tripOwnershipService, placeResolutionService, stopMapper);
    }

    @Test
    void addStop_appendsWithNextOrder() {
        User owner = new User();
        owner.setId(1L);

        Stop existing = new Stop();
        existing.setId(1L);
        existing.setStopOrder(0);

        Trip trip = new Trip();
        trip.setId(50L);
        trip.setUser(owner);
        trip.setStops(new ArrayList<>(List.of(existing)));

        when(tripRepository.findWithStopsById(50L)).thenReturn(Optional.of(trip));
        Place place = new Place();
        place.setId(21L);
        place.setName("Gas Station");
        when(placeResolutionService.resolvePlace(any(CreateStopRequest.class))).thenReturn(place);
        when(tripRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Trip.class));

        CreateStopRequest request = new CreateStopRequest(
                "Gas Station", 44.5, -79.6, null, null, null);

        StopResponse response = stopService.addStop(50L, 1L, request);

        assertThat(response.stopOrder()).isEqualTo(1);
        assertThat(trip.getStops()).hasSize(2);
    }

    @Test
    void updateStop_changesPlaceNotesAndStatus() {
        User owner = new User();
        owner.setId(1L);

        Place oldPlace = new Place();
        oldPlace.setId(20L);
        oldPlace.setName("Old Place");

        Stop stop = new Stop();
        stop.setId(5L);
        stop.setPlace(oldPlace);
        stop.setStopOrder(0);
        stop.setStatus(StopStatus.PLANNED);

        Trip trip = new Trip();
        trip.setId(50L);
        trip.setUser(owner);
        trip.setStops(new ArrayList<>(List.of(stop)));
        stop.setTrip(trip);

        when(tripRepository.findWithStopsById(50L)).thenReturn(Optional.of(trip));
        Place newPlace = new Place();
        newPlace.setId(22L);
        newPlace.setName("New Place");
        when(placeResolutionService.resolvePlace(any(), any(), any(), any(), any())).thenReturn(newPlace);
        when(tripRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Trip.class));

        UpdateStopRequest request = new UpdateStopRequest(
                "New Place", 1.0, 2.0, null, null, "Visit at sunset", StopStatus.VISITED);

        StopResponse response = stopService.updateStop(50L, 5L, 1L, request);

        assertThat(response.name()).isEqualTo("New Place");
        assertThat(response.notes()).isEqualTo("Visit at sunset");
        assertThat(response.status()).isEqualTo(StopStatus.VISITED);
    }

    @Test
    void updateStop_missingStop_throwsNotFound() {
        User owner = new User();
        owner.setId(1L);

        Trip trip = new Trip();
        trip.setId(50L);
        trip.setUser(owner);
        trip.setStops(new ArrayList<>());

        when(tripRepository.findWithStopsById(50L)).thenReturn(Optional.of(trip));

        UpdateStopRequest request = new UpdateStopRequest(
                "X", 1.0, 2.0, null, null, null, null);

        assertThatThrownBy(() -> stopService.updateStop(50L, 999L, 1L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteStop_removesAndRenumbersRemaining() {
        User owner = new User();
        owner.setId(1L);

        Stop stop0 = new Stop();
        stop0.setId(1L);
        stop0.setStopOrder(0);

        Stop stop1 = new Stop();
        stop1.setId(2L);
        stop1.setStopOrder(1);

        Stop stop2 = new Stop();
        stop2.setId(3L);
        stop2.setStopOrder(2);

        Trip trip = new Trip();
        trip.setId(50L);
        trip.setUser(owner);
        trip.setStops(new ArrayList<>(List.of(stop0, stop1, stop2)));

        when(tripRepository.findWithStopsById(50L)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Trip.class));

        stopService.deleteStop(50L, 2L, 1L); // remove the middle stop

        assertThat(trip.getStops()).hasSize(2);
        assertThat(trip.getStops().get(0).getId()).isEqualTo(1L);
        assertThat(trip.getStops().get(0).getStopOrder()).isEqualTo(0);
        assertThat(trip.getStops().get(1).getId()).isEqualTo(3L);
        assertThat(trip.getStops().get(1).getStopOrder()).isEqualTo(1); // renumbered from 2 → 1
    }

    @Test
    void deleteStop_nonOwner_throwsForbidden() {
        User owner = new User();
        owner.setId(1L);

        Stop stop = new Stop();
        stop.setId(1L);
        stop.setStopOrder(0);

        Trip trip = new Trip();
        trip.setId(50L);
        trip.setUser(owner);
        trip.setStops(new ArrayList<>(List.of(stop)));

        when(tripRepository.findWithStopsById(50L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> stopService.deleteStop(50L, 1L, 2L))
                .isInstanceOf(ForbiddenException.class);
    }

    // ---------- buildStops ----------

    @Test
    void buildStops_assignsSequentialOrderAndResolvesPlaceForEach() {
        Trip trip = new Trip();
        trip.setId(50L);

        Place place1 = new Place();
        place1.setName("A");
        Place place2 = new Place();
        place2.setName("B");

        List<CreateStopRequest> requests = List.of(
                new CreateStopRequest("A", 1.0, 1.0, null, null, null),
                new CreateStopRequest("B", 2.0, 2.0, null, null, null));
        when(placeResolutionService.resolvePlaces(requests)).thenReturn(List.of(place1, place2));

        List<Stop> stops = stopService.buildStops(requests, trip);

        assertThat(stops).hasSize(2);
        assertThat(stops.get(0).getStopOrder()).isZero();
        assertThat(stops.get(0).getTrip()).isSameAs(trip);
        assertThat(stops.get(0).getPlace().getName()).isEqualTo("A");
        assertThat(stops.get(1).getStopOrder()).isEqualTo(1);
        assertThat(stops.get(1).getPlace().getName()).isEqualTo("B");
    }
}
