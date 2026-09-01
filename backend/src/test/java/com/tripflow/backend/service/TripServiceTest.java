package com.tripflow.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
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
import org.springframework.data.domain.Sort;

import com.tripflow.backend.domain.Place;
import com.tripflow.backend.domain.Stop;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.CreateStopRequest;
import com.tripflow.backend.dto.CreateTripRequest;
import com.tripflow.backend.dto.TripOwnerSummaryResponse;
import com.tripflow.backend.dto.TripResponse;
import com.tripflow.backend.dto.TripSearchFilters;
import com.tripflow.backend.dto.UpdateTripRequest;
import com.tripflow.backend.dto.UpsertStopRequest;
import com.tripflow.backend.exception.ForbiddenException;
import com.tripflow.backend.exception.InvalidRequestException;
import com.tripflow.backend.exception.ResourceNotFoundException;
import com.tripflow.backend.mapper.FeedTripMapper;
import com.tripflow.backend.mapper.StopMapper;
import com.tripflow.backend.mapper.TripMapper;
import com.tripflow.backend.repository.StopPhotoRepository;
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
    @Mock private StopPhotoRepository stopPhotoRepository;

    private TripService tripService;

    @BeforeEach
    void setUp() {
        StopMapper stopMapper = new StopMapper();
        TripMapper tripMapper = new TripMapper(stopMapper);
        // Real TripOwnershipService (not mocked) — it's a thin delegate to tripRepository,
        // so every existing `when(tripRepository.findWithStopsById(...))` stub below still
        // drives the ownership check unchanged.
        TripOwnershipService tripOwnershipService = new TripOwnershipService(tripRepository);
        FeedTripMapper feedTripMapper = new FeedTripMapper();
        tripService = new TripService(tripRepository, userRepository, tripMapper, tripOwnershipService, stopService,
                stopPhotoRepository, feedTripMapper);
    }

    /** A PRIVATE trip with no stops, owned by {@code ownerId}, matching the id every stub below uses. */
    private Trip ownedTrip(Long ownerId) {
        User owner = new User();
        owner.setId(ownerId);

        Trip trip = new Trip();
        trip.setId(50L);
        trip.setUser(owner);
        trip.setVisibility(TripVisibility.PRIVATE);
        trip.setStops(new ArrayList<>());
        return trip;
    }

    /**
     * Emulates the real {@link StopService#mergeStops} against the mock: it mutates the trip's
     * stop list in place rather than returning one. The merge's own behaviour (identity
     * matching, preserved photos/scheduling, cross-trip id rejection) is tested for real in
     * {@link StopServiceTest}.
     */
    private void stubMergeStops() {
        doAnswer(inv -> {
            List<UpsertStopRequest> requests = inv.getArgument(0);
            Trip trip = inv.getArgument(1);
            trip.getStops().clear();
            trip.getStops().addAll(stubbedStops(
                    requests.stream().map(UpsertStopRequest::toCreateRequest).toList(), trip));
            return null;
        }).when(stopService).mergeStops(anyList(), any(Trip.class));
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
    void getTrip_privateTripNonOwner_throwsNotFound() {
        // SCRUM-71a: 404, not 403 — a 403 would confirm the trip id exists to someone
        // who isn't allowed to see it.
        User owner = new User();
        owner.setId(1L);

        Trip trip = new Trip();
        trip.setId(50L);
        trip.setUser(owner);
        trip.setVisibility(TripVisibility.PRIVATE);

        when(tripRepository.findWithStopsById(50L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.getTrip(50L, 2L))
                .isInstanceOf(ResourceNotFoundException.class);
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

    @Test
    void getTrip_publicTripNonOwner_succeeds() {
        User owner = new User();
        owner.setId(1L);

        Trip trip = new Trip();
        trip.setId(50L);
        trip.setUser(owner);
        trip.setVisibility(TripVisibility.PUBLIC);
        trip.setTitle("Shared Trip");
        trip.setStops(List.of());

        when(tripRepository.findWithStopsById(50L)).thenReturn(Optional.of(trip));

        TripResponse response = tripService.getTrip(50L, 2L);

        assertThat(response.title()).isEqualTo("Shared Trip");
        assertThat(response.visibility()).isEqualTo(TripVisibility.PUBLIC);
    }

    // ---------- toggleVisibility ----------

    @Test
    void toggleVisibility_owner_flipsPrivateToPublic() {
        User owner = new User();
        owner.setId(1L);

        Trip trip = new Trip();
        trip.setId(50L);
        trip.setUser(owner);
        trip.setVisibility(TripVisibility.PRIVATE);

        when(tripRepository.findWithStopsById(50L)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Trip.class));

        TripResponse response = tripService.toggleVisibility(50L, 1L);

        assertThat(response.visibility()).isEqualTo(TripVisibility.PUBLIC);
    }

    @Test
    void toggleVisibility_owner_flipsPublicToPrivate() {
        User owner = new User();
        owner.setId(1L);

        Trip trip = new Trip();
        trip.setId(50L);
        trip.setUser(owner);
        trip.setVisibility(TripVisibility.PUBLIC);

        when(tripRepository.findWithStopsById(50L)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Trip.class));

        TripResponse response = tripService.toggleVisibility(50L, 1L);

        assertThat(response.visibility()).isEqualTo(TripVisibility.PRIVATE);
    }

    @Test
    void toggleVisibility_nonOwner_throwsForbidden_andNeverSaves() {
        User owner = new User();
        owner.setId(1L);

        Trip trip = new Trip();
        trip.setId(50L);
        trip.setUser(owner);
        trip.setVisibility(TripVisibility.PRIVATE);

        when(tripRepository.findWithStopsById(50L)).thenReturn(Optional.of(trip));

        assertThatThrownBy(() -> tripService.toggleVisibility(50L, 2L))
                .isInstanceOf(ForbiddenException.class);

        verify(tripRepository, never()).save(any());
    }

    @Test
    void toggleVisibility_missingTrip_throwsNotFound() {
        when(tripRepository.findWithStopsById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tripService.toggleVisibility(999L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- listTrips ----------

    @Test
    void listTrips_returnsPagedSummariesFromRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        TripOwnerSummaryResponse summary = new TripOwnerSummaryResponse(
                11L, "Trip B", TripVisibility.PRIVATE, null, null, null, 5L, null, 3L);
        Page<TripOwnerSummaryResponse> page = new PageImpl<>(List.of(summary), pageable, 1);

        when(tripRepository.findSummariesByUserId(1L, pageable)).thenReturn(page);

        Page<TripOwnerSummaryResponse> result = tripService.listTrips(1L, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("Trip B");
        assertThat(result.getContent().get(0).stopCount()).isEqualTo(5L);
        assertThat(result.getContent().get(0).visitedStopCount()).isEqualTo(3L);
        assertThat(result.getContent().get(0).completionPercentage()).isEqualTo(0.6);
    }

    // ---------- searchOwnedTrips ----------

    @Test
    void searchOwnedTrips_nullSearch_passesNullPatternToRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        TripSearchFilters filters = TripSearchFilters.none();
        when(tripRepository.searchOwnedTrips(eq(1L), isNull(), eq(filters), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        tripService.searchOwnedTrips(1L, null, filters, pageable);

        verify(tripRepository).searchOwnedTrips(eq(1L), isNull(), eq(filters), eq(pageable));
    }

    @Test
    void searchOwnedTrips_blankSearch_passesNullPatternToRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        TripSearchFilters filters = TripSearchFilters.none();
        when(tripRepository.searchOwnedTrips(eq(1L), isNull(), eq(filters), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        tripService.searchOwnedTrips(1L, "", filters, pageable);

        verify(tripRepository).searchOwnedTrips(eq(1L), isNull(), eq(filters), eq(pageable));
    }

    @Test
    void searchOwnedTrips_whitespaceOnlySearch_passesNullPatternToRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        TripSearchFilters filters = TripSearchFilters.none();
        when(tripRepository.searchOwnedTrips(eq(1L), isNull(), eq(filters), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        tripService.searchOwnedTrips(1L, "   ", filters, pageable);

        verify(tripRepository).searchOwnedTrips(eq(1L), isNull(), eq(filters), eq(pageable));
    }

    @Test
    void searchOwnedTrips_populatedSearch_passesWildcardedPatternToRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        TripSearchFilters filters = TripSearchFilters.none();
        when(tripRepository.searchOwnedTrips(eq(1L), eq("%paris%"), eq(filters), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        tripService.searchOwnedTrips(1L, "  paris  ", filters, pageable);

        verify(tripRepository).searchOwnedTrips(eq(1L), eq("%paris%"), eq(filters), eq(pageable));
    }

    @Test
    void searchOwnedTrips_searchContainingWildcardChars_escapesThemInThePattern() {
        Pageable pageable = PageRequest.of(0, 20);
        TripSearchFilters filters = TripSearchFilters.none();
        when(tripRepository.searchOwnedTrips(eq(1L), eq("%50\\% off\\_deal%"), eq(filters), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        tripService.searchOwnedTrips(1L, "50% off_deal", filters, pageable);

        verify(tripRepository).searchOwnedTrips(eq(1L), eq("%50\\% off\\_deal%"), eq(filters), eq(pageable));
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
        stubMergeStops();
        when(tripRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Trip.class));

        UpsertStopRequest newStop = new UpsertStopRequest(
                null, "Niagara Falls", 43.0962, -79.0377, null, null, null);
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

    /**
     * A record can't distinguish an omitted JSON field from an explicit null, and the 5-arg
     * convenience constructor passes startDate = null — so treating null as "clear it" wiped
     * the trip's date on every update that didn't restate it. Absent means unchanged.
     */
    @Test
    void updateTrip_absentStartDate_leavesExistingDateUnchanged() {
        Trip trip = ownedTrip(1L);
        trip.setStartDate(LocalDate.of(2026, 6, 1));

        when(tripRepository.findWithStopsById(50L)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Trip.class));

        TripResponse response = tripService.updateTrip(50L, 1L,
                new UpdateTripRequest("Retitled", null, null, TripVisibility.PRIVATE, List.of()));

        assertThat(response.startDate()).isEqualTo(LocalDate.of(2026, 6, 1));
    }

    @Test
    void updateTrip_suppliedStartDate_overwritesExistingDate() {
        Trip trip = ownedTrip(1L);
        trip.setStartDate(LocalDate.of(2026, 6, 1));

        when(tripRepository.findWithStopsById(50L)).thenReturn(Optional.of(trip));
        when(tripRepository.save(any())).thenAnswer(inv -> inv.getArgument(0, Trip.class));

        TripResponse response = tripService.updateTrip(50L, 1L, new UpdateTripRequest(
                "Retitled", null, null, TripVisibility.PRIVATE, List.of(), LocalDate.of(2027, 1, 15)));

        assertThat(response.startDate()).isEqualTo(LocalDate.of(2027, 1, 15));
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

    // ---------- searchPublicTrips (SCRUM-415) ----------

    @Test
    void searchPublicTrips_blankQuery_throwsInvalidRequest() {
        assertThatThrownBy(() -> tripService.searchPublicTrips("   ", PageRequest.of(0, 20)))
                .isInstanceOf(InvalidRequestException.class);

        verify(tripRepository, never()).searchPublicTrips(any(), any());
    }

    @Test
    void searchPublicTrips_defaultSort_delegatesToRepository() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(tripRepository.searchPublicTrips(eq("paris"), eq(pageable)))
                .thenReturn(Page.empty());

        tripService.searchPublicTrips("paris", pageable);

        verify(tripRepository).searchPublicTrips("paris", pageable);
    }

    @Test
    void searchPublicTrips_noSort_delegatesToRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        when(tripRepository.searchPublicTrips(eq("paris"), eq(pageable)))
                .thenReturn(Page.empty());

        tripService.searchPublicTrips("paris", pageable);

        verify(tripRepository).searchPublicTrips("paris", pageable);
    }

    @Test
    void searchPublicTrips_nonDefaultSort_throwsInvalidRequest_withoutCallingRepository() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "title"));

        assertThatThrownBy(() -> tripService.searchPublicTrips("paris", pageable))
                .isInstanceOf(InvalidRequestException.class);

        verify(tripRepository, never()).searchPublicTrips(any(), any());
    }

    @Test
    void searchPublicTrips_defaultSortButAscendingDirection_throwsInvalidRequest() {
        Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "createdAt"));

        assertThatThrownBy(() -> tripService.searchPublicTrips("paris", pageable))
                .isInstanceOf(InvalidRequestException.class);

        verify(tripRepository, never()).searchPublicTrips(any(), any());
    }
}