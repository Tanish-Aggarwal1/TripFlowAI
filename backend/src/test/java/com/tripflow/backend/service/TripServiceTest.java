package com.tripflow.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.tripflow.backend.domain.Place;
import com.tripflow.backend.domain.Stop;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.CreateStopRequest;
import com.tripflow.backend.dto.CreateTripRequest;
import com.tripflow.backend.dto.TripResponse;
import com.tripflow.backend.dto.TripSummaryResponse;
import com.tripflow.backend.dto.UpdateTripRequest;
import com.tripflow.backend.exception.ForbiddenException;
import com.tripflow.backend.exception.ResourceNotFoundException;
import com.tripflow.backend.mapper.StopMapper;
import com.tripflow.backend.mapper.TripMapper;
import com.tripflow.backend.repository.TripRepository;
import com.tripflow.backend.repository.UserRepository;

/**
 * Trip CRUD only — stop CRUD moved to {@link StopServiceTest}, place resolution to
 * {@link PlaceResolutionServiceTest} (SCRUM-215/239).
 */
@ExtendWith(MockitoExtension.class)
public class TripServiceTest {
	@Mock private TripRepository tripRepository;
    @Mock private UserRepository userRepository;
    @Mock private StopService stopService;

    private TripService tripService;

    @BeforeEach
    void setUp() {
        StopMapper stopMapper = new StopMapper();
        TripMapper tripMapper = new TripMapper(stopMapper);
        // Real TripOwnershipService (not mocked) — it's a thin delegate to tripRepository,
        // so every existing `when(tripRepository.findWithStopsById(...))` stub below still
        // drives the ownership check unchanged.
        TripOwnershipService tripOwnershipService = new TripOwnershipService(tripRepository);
        tripService = new TripService(tripRepository, userRepository, tripMapper, tripOwnershipService, stopService);
    }

    /** Builds the Stop list {@code stopService.buildStops(...)} would return for the given requests. */
    private List<Stop> stubbedStops(List<CreateStopRequest> requests, Trip trip) {
        List<Stop> stops = new ArrayList<>();
        int order = 0;
        for (CreateStopRequest req : requests) {
            Place place = new Place();
            place.setName(req.name());
            place.setLatitude(req.latitude());
            place.setLongitude(req.longitude());
            Stop stop = new Stop();
            stop.setPlace(place);
            stop.setStopOrder(order++);
            stop.setTrip(trip);
            stops.add(stop);
        }
        return stops;
    }

    @Test
    void createTrip_happyPath_savesAndReturnsResponse() {
        User owner = new User();
        owner.setId(1L);
        owner.setUsername("tanish");
        when(userRepository.getReferenceById(1L)).thenReturn(owner);
        when(stopService.buildStops(anyList(), any(Trip.class)))
                .thenAnswer(inv -> stubbedStops(inv.getArgument(0), inv.getArgument(1)));
        when(tripRepository.save(any())).thenAnswer(inv -> {
            Trip t = inv.getArgument(0, Trip.class);
            t.setId(100L);
            return t;
        });

        CreateStopRequest stopReq = new CreateStopRequest("Cottage", 45.0, -79.9, null, null, null);
        CreateTripRequest request = new CreateTripRequest(
                "Weekend Trip", null, null, TripVisibility.PRIVATE, List.of(stopReq));

        TripResponse response = tripService.createTrip(1L, request);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.title()).isEqualTo("Weekend Trip");
        assertThat(response.stops()).hasSize(1);
        assertThat(response.stops().get(0).stopOrder()).isEqualTo(0);
    }

    @Test
    void createTrip_doesNotQueryUserRepositoryByFindById() {
        User owner = new User();
        owner.setId(1L);
        when(userRepository.getReferenceById(1L)).thenReturn(owner);
        when(stopService.buildStops(anyList(), any(Trip.class)))
                .thenAnswer(inv -> stubbedStops(inv.getArgument(0), inv.getArgument(1)));
        when(tripRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Trip.class));

        CreateStopRequest stopReq = new CreateStopRequest("Cottage", 45.0, -79.9, null, null, null);
        CreateTripRequest request = new CreateTripRequest(
                "Weekend Trip", null, null, TripVisibility.PRIVATE, List.of(stopReq));

        tripService.createTrip(1L, request);

        verify(userRepository, never()).findById(any());
    }

    @Test
    void getTrip_privateTripNonOwner_throwsForbidden() {
        User owner = new User();
        owner.setId(1L);

        Trip trip = new Trip();
        trip.setId(50L);
        trip.setUser(owner);
        trip.setVisibility(TripVisibility.PRIVATE);

        when(tripRepository.findWithStopsById(50L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.getTrip(50L, 2L))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getTrip_missingTrip_throwsNotFound() {
        when(tripRepository.findWithStopsById(999L)).thenReturn(Optional.empty());

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

        when(tripRepository.findWithStopsById(50L)).thenReturn(Optional.of(trip));

        TripResponse response = tripService.getTrip(50L, 1L);

        assertThat(response.title()).isEqualTo("My Trip");
    }

    // ---------- listTrips ----------

    @Test
    void listTrips_returnsPagedSummariesFromRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        TripSummaryResponse summary = new TripSummaryResponse(
                11L, "Trip B", TripVisibility.PRIVATE, null, null, null, 2L, null);
        Page<TripSummaryResponse> page = new PageImpl<>(List.of(summary), pageable, 1);

        when(tripRepository.findSummariesByUserId(1L, pageable)).thenReturn(page);

        Page<TripSummaryResponse> result = tripService.listTrips(1L, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("Trip B");
        assertThat(result.getContent().get(0).stopCount()).isEqualTo(2L);
    }

    // ---------- updateTrip ----------

    @Test
    void updateTrip_owner_replacesFieldsAndStops() {
        User owner = new User();
        owner.setId(1L);

        Trip trip = new Trip();
        trip.setId(50L);
        trip.setUser(owner);
        trip.setTitle("Old Title");
        trip.setVisibility(TripVisibility.PRIVATE);
        trip.setStops(new ArrayList<>());

        when(tripRepository.findWithStopsById(50L)).thenReturn(Optional.of(trip));
        when(stopService.buildStops(anyList(), any(Trip.class)))
                .thenAnswer(inv -> stubbedStops(inv.getArgument(0), inv.getArgument(1)));
        when(tripRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Trip.class));

        CreateStopRequest newStop = new CreateStopRequest(
                "Niagara Falls", 43.0962, -79.0377, null, null, null);
        UpdateTripRequest request = new UpdateTripRequest(
                "New Title", null, null, TripVisibility.PUBLIC, List.of(newStop));

        TripResponse response = tripService.updateTrip(50L, 1L, request);

        assertThat(response.title()).isEqualTo("New Title");
        assertThat(response.visibility()).isEqualTo(TripVisibility.PUBLIC);
        assertThat(response.stops()).hasSize(1);
        assertThat(response.stops().get(0).name()).isEqualTo("Niagara Falls");
    }

    @Test
    void updateTrip_nullTags_defaultsToEmptyListNotNull() {
        User owner = new User();
        owner.setId(1L);

        Trip trip = new Trip();
        trip.setId(50L);
        trip.setUser(owner);
        trip.setTags(new ArrayList<>(List.of("old-tag")));
        trip.setStops(new ArrayList<>());

        when(tripRepository.findWithStopsById(50L)).thenReturn(Optional.of(trip));
        when(stopService.buildStops(anyList(), any(Trip.class))).thenReturn(new ArrayList<>());
        when(tripRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Trip.class));

        UpdateTripRequest request = new UpdateTripRequest(
                "Title", null, null, TripVisibility.PRIVATE, List.of());

        TripResponse response = tripService.updateTrip(50L, 1L, request);

        assertThat(response.tags()).isNotNull().isEmpty();
    }

    @Test
    void updateTrip_nonOwner_throwsForbidden() {
        User owner = new User();
        owner.setId(1L);

        Trip trip = new Trip();
        trip.setId(50L);
        trip.setUser(owner);
        trip.setStops(new ArrayList<>());

        when(tripRepository.findWithStopsById(50L)).thenReturn(Optional.of(trip));

        UpdateTripRequest request = new UpdateTripRequest(
                "Hijacked", null, null, TripVisibility.PRIVATE, List.of());

        assertThatThrownBy(() -> tripService.updateTrip(50L, 2L, request))
                .isInstanceOf(ForbiddenException.class);

        verify(tripRepository, never()).save(any());
    }

    @Test
    void updateTrip_missingTrip_throwsNotFound() {
        when(tripRepository.findWithStopsById(999L)).thenReturn(Optional.empty());

        UpdateTripRequest request = new UpdateTripRequest(
                "X", null, null, TripVisibility.PRIVATE, List.of());

        assertThatThrownBy(() -> tripService.updateTrip(999L, 1L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- deleteTrip ----------

    @Test
    void deleteTrip_owner_deletesTrip() {
        User owner = new User();
        owner.setId(1L);

        Trip trip = new Trip();
        trip.setId(50L);
        trip.setUser(owner);

        when(tripRepository.findWithStopsById(50L)).thenReturn(Optional.of(trip));

        tripService.deleteTrip(50L, 1L);

        verify(tripRepository).delete(trip);
    }

    @Test
    void deleteTrip_nonOwner_throwsForbidden_andNeverDeletes() {
        User owner = new User();
        owner.setId(1L);

        Trip trip = new Trip();
        trip.setId(50L);
        trip.setUser(owner);

        when(tripRepository.findWithStopsById(50L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.deleteTrip(50L, 2L))
                .isInstanceOf(ForbiddenException.class);

        verify(tripRepository, never()).delete(any());
    }
}
