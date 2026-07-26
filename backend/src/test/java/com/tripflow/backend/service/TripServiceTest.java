package com.tripflow.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.tripflow.backend.domain.Place;
import com.tripflow.backend.domain.Stop;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.domain.enums.StopStatus;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.CreateStopRequest;
import com.tripflow.backend.dto.CreateTripRequest;
import com.tripflow.backend.dto.StopResponse;
import com.tripflow.backend.dto.TripResponse;
import com.tripflow.backend.dto.TripSummaryResponse;
import com.tripflow.backend.dto.UpdateStopRequest;
import com.tripflow.backend.dto.UpdateTripRequest;
import com.tripflow.backend.exception.ForbiddenException;
import com.tripflow.backend.exception.ResourceNotFoundException;
import com.tripflow.backend.mapper.StopMapper;
import com.tripflow.backend.mapper.TripMapper;
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
        StopMapper stopMapper = new StopMapper();
        TripMapper tripMapper = new TripMapper(stopMapper);
        tripService = new TripService(tripRepository, userRepository, placeRepository, tripMapper, stopMapper);
    }

    @Test
    void createTrip_happyPath_savesAndReturnsResponse() {
        User owner = new User();
        owner.setId(1L);
        owner.setUsername("tanish");
        when(userRepository.getReferenceById(1L)).thenReturn(owner);
        when(placeRepository.findByNameAndLatitudeAndLongitude(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(placeRepository.save(any())).thenAnswer(inv -> {
            Place place = inv.getArgument(0, Place.class);
            place.setId(10L);
            return place;
        });
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
        when(placeRepository.findByNameAndLatitudeAndLongitude(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(placeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Place.class));
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
        when(placeRepository.findByNameAndLatitudeAndLongitude(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(placeRepository.save(any())).thenAnswer(inv -> {
            Place p = inv.getArgument(0, Place.class);
            p.setId(20L);
            return p;
        });
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

    // ---------- stop CRUD ----------

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
        when(placeRepository.findByNameAndLatitudeAndLongitude(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(placeRepository.save(any())).thenAnswer(inv -> {
            Place p = inv.getArgument(0, Place.class);
            p.setId(21L);
            return p;
        });
        when(tripRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Trip.class));

        CreateStopRequest request = new CreateStopRequest(
                "Gas Station", 44.5, -79.6, null, null, null);

        StopResponse response = tripService.addStop(50L, 1L, request);

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
        when(placeRepository.findByNameAndLatitudeAndLongitude(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(placeRepository.save(any())).thenAnswer(inv -> {
            Place p = inv.getArgument(0, Place.class);
            p.setId(22L);
            return p;
        });
        when(tripRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Trip.class));

        UpdateStopRequest request = new UpdateStopRequest(
                "New Place", 1.0, 2.0, null, null, "Visit at sunset", StopStatus.VISITED);

        StopResponse response = tripService.updateStop(50L, 5L, 1L, request);

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

        assertThatThrownBy(() -> tripService.updateStop(50L, 999L, 1L, request))
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

        tripService.deleteStop(50L, 2L, 1L); // remove the middle stop

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

        assertThatThrownBy(() -> tripService.deleteStop(50L, 1L, 2L))
                .isInstanceOf(ForbiddenException.class);
    }

    // ---------- resolvePlace race retry ----------

    @Test
    void addStop_placeSaveRacesUniqueIndex_reusesWinningRow() {
        User owner = new User();
        owner.setId(1L);

        Trip trip = new Trip();
        trip.setId(50L);
        trip.setUser(owner);
        trip.setStops(new ArrayList<>());

        Place winningRow = new Place();
        winningRow.setId(30L);
        winningRow.setName("Shared Cafe");
        winningRow.setExternalPlaceId("ext-123");

        when(tripRepository.findWithStopsById(50L)).thenReturn(Optional.of(trip));
        when(placeRepository.findByExternalPlaceId("ext-123"))
                .thenReturn(Optional.empty(), Optional.of(winningRow));
        when(placeRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));
        when(tripRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Trip.class));

        CreateStopRequest request = new CreateStopRequest(
                "Shared Cafe", 44.0, -79.0, null, "ext-123", null);

        StopResponse response = tripService.addStop(50L, 1L, request);

        assertThat(response.name()).isEqualTo("Shared Cafe");
        verify(placeRepository).save(any());
        verify(placeRepository, times(2)).findByExternalPlaceId("ext-123");
    }

    @Test
    void addStop_placeSaveFailsForUnrelatedReason_propagatesWhenNoExistingRowFound() {
        User owner = new User();
        owner.setId(1L);

        Trip trip = new Trip();
        trip.setId(50L);
        trip.setUser(owner);
        trip.setStops(new ArrayList<>());

        when(tripRepository.findWithStopsById(50L)).thenReturn(Optional.of(trip));
        when(placeRepository.findByNameAndLatitudeAndLongitude(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(placeRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        CreateStopRequest request = new CreateStopRequest(
                "New Place", 44.0, -79.0, null, null, null);

        assertThatThrownBy(() -> tripService.addStop(50L, 1L, request))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
