package com.tripflow.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
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
import com.tripflow.backend.domain.enums.StopType;
import com.tripflow.backend.dto.CreateStopRequest;
import com.tripflow.backend.dto.StopResponse;
import com.tripflow.backend.dto.UpdateStopRequest;
import com.tripflow.backend.dto.UpsertStopRequest;
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

    // ---------- mergeStops (full-itinerary replace, by identity) ----------

    /** A stop already persisted on a trip, with the server-owned state a merge must preserve. */
    private Stop scheduledStop(Long id, int order, String placeName) {
        Place place = new Place();
        place.setName(placeName);

        Stop stop = new Stop();
        stop.setId(id);
        stop.setStopOrder(order);
        stop.setPlace(place);
        stop.setStatus(StopStatus.VISITED);
        stop.setDayNumber(2);
        stop.setPlannedTime(LocalTime.of(14, 30));
        stop.setStopType(StopType.LODGING);
        return stop;
    }

    private Trip tripWith(Stop... stops) {
        Trip trip = new Trip();
        trip.setId(50L);
        trip.setStops(new ArrayList<>(List.of(stops)));
        for (Stop stop : stops) {
            stop.setTrip(trip);
        }
        return trip;
    }

    private void stubPlaces(Place... places) {
        when(placeResolutionService.resolvePlaces(any())).thenReturn(List.of(places));
    }

    private static Place namedPlace(String name) {
        Place place = new Place();
        place.setName(name);
        return place;
    }

    /**
     * The regression this whole merge exists for: an edit that restates an existing stop must
     * not delete and recreate it. A recreated stop is a new row, and stop_photos cascades on
     * delete — so a title-only update used to destroy every photo on the trip.
     */
    @Test
    void mergeStops_existingId_updatesInPlaceAndPreservesServerOwnedState() {
        Stop existing = scheduledStop(7L, 0, "Old Name");
        Trip trip = tripWith(existing);
        stubPlaces(namedPlace("Renamed"));

        stopService.mergeStops(
                List.of(new UpsertStopRequest(7L, "Renamed", 1.0, 1.0, null, null, "new notes")), trip);

        assertThat(trip.getStops()).hasSize(1);
        Stop merged = trip.getStops().get(0);
        // Same entity instance — never deleted, so its row (and its photos) survive.
        assertThat(merged).isSameAs(existing);
        assertThat(merged.getId()).isEqualTo(7L);
        assertThat(merged.getPlace().getName()).isEqualTo("Renamed");
        assertThat(merged.getNotes()).isEqualTo("new notes");
        assertThat(merged.getStatus()).isEqualTo(StopStatus.VISITED);
        assertThat(merged.getDayNumber()).isEqualTo(2);
        assertThat(merged.getPlannedTime()).isEqualTo(LocalTime.of(14, 30));
        assertThat(merged.getStopType()).isEqualTo(StopType.LODGING);
    }

    @Test
    void mergeStops_nullId_insertsNewStop() {
        Stop existing = scheduledStop(7L, 0, "Kept");
        Trip trip = tripWith(existing);
        stubPlaces(namedPlace("Kept"), namedPlace("Added"));

        stopService.mergeStops(List.of(
                new UpsertStopRequest(7L, "Kept", 1.0, 1.0, null, null, null),
                new UpsertStopRequest(null, "Added", 2.0, 2.0, null, null, null)), trip);

        assertThat(trip.getStops()).hasSize(2);
        assertThat(trip.getStops().get(0)).isSameAs(existing);
        Stop inserted = trip.getStops().get(1);
        assertThat(inserted.getId()).isNull();
        assertThat(inserted.getTrip()).isSameAs(trip);
        assertThat(inserted.getStopOrder()).isEqualTo(1);
        // A brand-new stop starts unscheduled with the entity defaults, not the sibling's state.
        assertThat(inserted.getStatus()).isEqualTo(StopStatus.PLANNED);
        assertThat(inserted.getDayNumber()).isNull();
    }

    @Test
    void mergeStops_omittedId_dropsThatStopOnly() {
        Stop kept = scheduledStop(7L, 0, "Kept");
        Stop dropped = scheduledStop(8L, 1, "Dropped");
        Trip trip = tripWith(kept, dropped);
        stubPlaces(namedPlace("Kept"));

        stopService.mergeStops(
                List.of(new UpsertStopRequest(7L, "Kept", 1.0, 1.0, null, null, null)), trip);

        assertThat(trip.getStops()).containsExactly(kept).doesNotContain(dropped);
    }

    @Test
    void mergeStops_reordersByPayloadPosition() {
        Stop first = scheduledStop(7L, 0, "First");
        Stop second = scheduledStop(8L, 1, "Second");
        Trip trip = tripWith(first, second);
        stubPlaces(namedPlace("Second"), namedPlace("First"));

        stopService.mergeStops(List.of(
                new UpsertStopRequest(8L, "Second", 2.0, 2.0, null, null, null),
                new UpsertStopRequest(7L, "First", 1.0, 1.0, null, null, null)), trip);

        assertThat(second.getStopOrder()).isZero();
        assertThat(first.getStopOrder()).isEqualTo(1);
        assertThat(trip.getStops()).containsExactly(second, first);
    }

    /**
     * Security-critical: an id that exists but belongs to someone else's trip must never be
     * re-parented onto this one. Reported as not-found rather than forbidden so the response
     * doesn't confirm the id exists elsewhere.
     */
    @Test
    void mergeStops_idFromAnotherTrip_isRejectedAndNotAdopted() {
        Stop ownStop = scheduledStop(7L, 0, "Mine");
        Trip trip = tripWith(ownStop);

        Stop foreignStop = scheduledStop(999L, 0, "Someone else's");
        Trip otherTrip = tripWith(foreignStop);
        otherTrip.setId(51L); // distinct from trip's 50 — Trip.equals is id-based
        stubPlaces(namedPlace("Mine"), namedPlace("Stolen"));

        assertThatThrownBy(() -> stopService.mergeStops(List.of(
                new UpsertStopRequest(7L, "Mine", 1.0, 1.0, null, null, null),
                new UpsertStopRequest(999L, "Stolen", 2.0, 2.0, null, null, null)), trip))
                .isInstanceOf(ResourceNotFoundException.class)
                .isNotInstanceOf(ForbiddenException.class);

        assertThat(foreignStop.getTrip()).isSameAs(otherTrip);
        assertThat(foreignStop.getPlace().getName()).isEqualTo("Someone else's");
        assertThat(trip.getStops()).doesNotContain(foreignStop);
    }

    @Test
    void mergeStops_sameIdTwiceInOnePayload_isRejected() {
        Stop existing = scheduledStop(7L, 0, "Once");
        Trip trip = tripWith(existing);
        stubPlaces(namedPlace("Once"), namedPlace("Twice"));

        assertThatThrownBy(() -> stopService.mergeStops(List.of(
                new UpsertStopRequest(7L, "Once", 1.0, 1.0, null, null, null),
                new UpsertStopRequest(7L, "Twice", 2.0, 2.0, null, null, null)), trip))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
