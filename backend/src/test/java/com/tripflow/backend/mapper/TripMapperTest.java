package com.tripflow.backend.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.ArrayList;
import java.util.List;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import com.tripflow.backend.domain.Place;
import com.tripflow.backend.domain.Stop;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.domain.enums.StopStatus;
import com.tripflow.backend.domain.enums.TripStatus;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.CreateStopRequest;
import com.tripflow.backend.dto.CreateTripRequest;
import com.tripflow.backend.dto.TripResponse;

public class TripMapperTest {
	private final TripMapper tripMapper = new TripMapper(new StopMapper());

	/** A trip with one Stop per given status, in the file's existing fixture style. */
	private Trip tripWithStops(StopStatus... statuses) {
		Trip trip = new Trip();
		trip.setId(1L);
		User owner = new User();
		owner.setId(1L);
		trip.setUser(owner);

		List<Stop> stops = new ArrayList<>();
		for (StopStatus status : statuses) {
			Place place = new Place();
			place.setName("Some Place");
			place.setLatitude(45.0);
			place.setLongitude(-79.9);

			Stop stop = new Stop();
			stop.setPlace(place);
			stop.setStopOrder(stops.size());
			stop.setStatus(status);
			stop.setTrip(trip);
			stops.add(stop);
		}
		trip.setStops(stops);
		return trip;
	}

    @Test
    void toEntity_neverSetsServerOwnedFields() {
        CreateTripRequest request = new CreateTripRequest(
                "Weekend Trip",
                "A nice trip",
                List.of("cottage"),
                TripVisibility.PRIVATE,
                List.of(new CreateStopRequest("Cottage", 45.0, -79.9, null, null, null))
        );

        User owner = new User();
        owner.setUsername("tanish");

        Trip trip = tripMapper.toEntity(request, owner);

        // id is server-owned (auto-generated) — must be null before persistence,
        // proving nothing in the request can set it
        assertThat(trip.getId()).isNull();

        // status must always be DRAFT on create, regardless of request content
        assertThat(trip.getStatus()).isEqualTo(TripStatus.DRAFT);

        // routeGeometry is server-owned, populated later by route optimization —
        // must be null immediately after mapping, never settable from the request
        assertThat(trip.getRouteGeometry()).isNull();

        // owner comes from the authenticated caller (passed in), not the DTO
        assertThat(trip.getUser()).isEqualTo(owner);

        assertThat(trip.getTitle()).isEqualTo("Weekend Trip");
    }

    @Test
    void toEntity_nullTags_defaultsToEmptyListNotNull() {
        CreateTripRequest request = new CreateTripRequest(
                "Weekend Trip",
                null,
                null,
                TripVisibility.PRIVATE,
                List.of(new CreateStopRequest("Cottage", 45.0, -79.9, null, null, null))
        );

        Trip trip = tripMapper.toEntity(request, new User());

        assertThat(trip.getTags()).isNotNull().isEmpty();
    }

    // ---------- toResponse: completion (EXPORT-03) ----------

    @Test
    void toResponse_fiveStopsThreeVisited_completionIsThreeFifths() {
        Trip trip = tripWithStops(
                StopStatus.VISITED, StopStatus.VISITED, StopStatus.VISITED,
                StopStatus.PLANNED, StopStatus.SKIPPED);

        TripResponse response = tripMapper.toResponse(trip);

        assertThat(response.visitedStopCount()).isEqualTo(3L);
        assertThat(response.completionPercentage()).isCloseTo(0.6, Offset.offset(0.0001));
    }

    @Test
    void toResponse_allSkipped_visitedZeroButStopCountNonZero() {
        // D-06: SKIPPED stops count in the denominator only.
        Trip trip = tripWithStops(StopStatus.SKIPPED, StopStatus.SKIPPED, StopStatus.SKIPPED, StopStatus.SKIPPED);

        TripResponse response = tripMapper.toResponse(trip);

        assertThat(response.visitedStopCount()).isZero();
        assertThat(response.completionPercentage()).isEqualTo(0.0);
        assertThat(response.stops()).hasSize(4);
    }

    @Test
    void toResponse_allVisited_completionIsOne() {
        Trip trip = tripWithStops(StopStatus.VISITED, StopStatus.VISITED);

        TripResponse response = tripMapper.toResponse(trip);

        assertThat(response.completionPercentage()).isEqualTo(1.0);
    }

    @Test
    void toResponse_zeroStops_completionIsZero_noException() {
        // D-07: zero stops must never null-pointer or divide-by-zero.
        Trip trip = tripWithStops();

        assertThatNoException().isThrownBy(() -> tripMapper.toResponse(trip));

        TripResponse response = tripMapper.toResponse(trip);
        assertThat(response.visitedStopCount()).isZero();
        assertThat(response.completionPercentage()).isEqualTo(0.0);
    }

    @Test
    void toResponse_mixOfAllThreeStatuses_onlyVisitedCountsInNumerator() {
        Trip trip = tripWithStops(StopStatus.PLANNED, StopStatus.VISITED, StopStatus.SKIPPED);

        TripResponse response = tripMapper.toResponse(trip);

        assertThat(response.visitedStopCount()).isEqualTo(1L);
        assertThat(response.completionPercentage()).isCloseTo(1.0 / 3.0, Offset.offset(0.0001));
    }
}
