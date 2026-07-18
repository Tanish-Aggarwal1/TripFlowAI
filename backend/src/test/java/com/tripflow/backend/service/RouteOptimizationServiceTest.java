package com.tripflow.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripflow.backend.client.ors.OrsClient;
import com.tripflow.backend.client.ors.OrsDirectionsRequest;
import com.tripflow.backend.client.ors.OrsDirectionsResponse;
import com.tripflow.backend.client.ors.OrsOptimizationRequest;
import com.tripflow.backend.client.ors.OrsOptimizationResponse;
import com.tripflow.backend.domain.Place;
import com.tripflow.backend.domain.Stop;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.domain.enums.TripStatus;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.TripResponse;
import com.tripflow.backend.exception.ForbiddenException;
import com.tripflow.backend.exception.ResourceNotFoundException;
import com.tripflow.backend.mapper.StopMapper;
import com.tripflow.backend.mapper.TripMapper;
import com.tripflow.backend.repository.TripRepository;

@ExtendWith(MockitoExtension.class)
class RouteOptimizationServiceTest {

    @Mock private TripRepository tripRepository;
    @Mock private OrsClient orsClient;
    @Spy  private ObjectMapper objectMapper;

    private TripMapper tripMapper;

    private RouteOptimizationService service;

    private static final Long OWNER_ID = 1L;
    private static final Long TRIP_ID = 10L;
    private static final Long OTHER_USER_ID = 99L;

    @BeforeEach
    void setUp() {
        // Real mapper (no mock) so we can assert the full TripResponse shape
        tripMapper = new TripMapper(new StopMapper());
        service = new RouteOptimizationService(
                tripRepository, orsClient, tripMapper, objectMapper);
    }

    // ── test data builders ───────────────────────────────────────────────────

    private User owner() {
        User user = new User();
        user.setUsername("testowner");
        user.setEmail("owner@test.com");
        user.setPasswordHash("hashed");
        setId(user, OWNER_ID);
        return user;
    }

    private Place place(Long id, String name, double lat, double lng) {
        Place place = new Place();
        place.setName(name);
        place.setLatitude(lat);
        place.setLongitude(lng);
        setId(place, id);
        return place;
    }

    private Stop stop(Long id, Place place, int order, Trip trip) {
        Stop stop = new Stop();
        stop.setPlace(place);
        stop.setStopOrder(order);
        stop.setTrip(trip);
        setId(stop, id);
        return stop;
    }

    private Trip tripWith3Stops() {
        Trip trip = new Trip();
        trip.setUser(owner());
        trip.setTitle("Test Trip");
        trip.setVisibility(TripVisibility.PRIVATE);
        trip.setStatus(TripStatus.DRAFT);
        setId(trip, TRIP_ID);
        setTimestamps(trip);

        Place p1 = place(100L, "Toronto",    43.65, -79.38);
        Place p2 = place(101L, "Ottawa",     45.42, -75.70);
        Place p3 = place(102L, "Montreal",   45.50, -73.57);

        // Original order: Toronto(0) → Ottawa(1) → Montreal(2)
        List<Stop> stops = new ArrayList<>();
        stops.add(stop(200L, p1, 0, trip));
        stops.add(stop(201L, p2, 1, trip));
        stops.add(stop(202L, p3, 2, trip));
        trip.setStops(stops);
        return trip;
    }

    /** VROOM says optimal order is: Toronto → Montreal → Ottawa (job 200, 202, 201) */
    private OrsOptimizationResponse optimizationResponse_reordered() {
        return new OrsOptimizationResponse(
                0,
                new OrsOptimizationResponse.Summary(500.0, 7200.0),
                List.of(new OrsOptimizationResponse.Route(1L, 7200.0, List.of(
                        new OrsOptimizationResponse.Step("start", null, List.of(-79.38, 43.65)),
                        new OrsOptimizationResponse.Step("job",   200L, List.of(-79.38, 43.65)),
                        new OrsOptimizationResponse.Step("job",   202L, List.of(-73.57, 45.50)),
                        new OrsOptimizationResponse.Step("job",   201L, List.of(-75.70, 45.42)),
                        new OrsOptimizationResponse.Step("end",   null, List.of(-79.38, 43.65))
                ))));
    }

    private OrsDirectionsResponse directionsResponse() {
        return new OrsDirectionsResponse(List.of(
                new OrsDirectionsResponse.Feature(
                        new OrsDirectionsResponse.Geometry("LineString", List.of(
                                List.of(-79.38, 43.65),
                                List.of(-73.57, 45.50),
                                List.of(-75.70, 45.42))),
                        new OrsDirectionsResponse.Properties(
                                new OrsDirectionsResponse.Summary(450000.0, 14400.0)))));
    }

    // ── Reflection helpers for setting entity IDs in tests ────────────────

    private void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getSuperclass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set id on " + entity.getClass().getSimpleName(), e);
        }
    }

    private void setTimestamps(Object entity) {
        try {
            var clazz = entity.getClass().getSuperclass();
            var created = clazz.getDeclaredField("createdAt");
            created.setAccessible(true);
            created.set(entity, Instant.now());
            var updated = clazz.getDeclaredField("updatedAt");
            updated.setAccessible(true);
            updated.set(entity, Instant.now());
        } catch (Exception e) {
            throw new RuntimeException("Failed to set timestamps", e);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Tests
    // ═════════════════════════════════════════════════════════════════════════

    @Nested
    class HappyPath {

        @Test
        void optimize_reordersStopsAndPersistsGeometry() {
            Trip trip = tripWith3Stops();
            given(tripRepository.findWithStopsById(TRIP_ID)).willReturn(Optional.of(trip));
            given(orsClient.optimize(any(OrsOptimizationRequest.class)))
                    .willReturn(optimizationResponse_reordered());
            given(orsClient.getDirections(any(OrsDirectionsRequest.class)))
                    .willReturn(directionsResponse());
            given(tripRepository.save(any(Trip.class))).willAnswer(inv -> inv.getArgument(0));

            TripResponse result = service.optimize(TRIP_ID, OWNER_ID);

            // Stops should be reordered: Toronto(0) → Montreal(1) → Ottawa(2)
            assertThat(result.stops()).hasSize(3);
            assertThat(result.stops().get(0).name()).isEqualTo("Toronto");
            assertThat(result.stops().get(0).stopOrder()).isZero();
            assertThat(result.stops().get(1).name()).isEqualTo("Montreal");
            assertThat(result.stops().get(1).stopOrder()).isEqualTo(1);
            assertThat(result.stops().get(2).name()).isEqualTo("Ottawa");
            assertThat(result.stops().get(2).stopOrder()).isEqualTo(2);
        }

        @Test
        void optimize_persistsRouteGeometry() {
            Trip trip = tripWith3Stops();
            given(tripRepository.findWithStopsById(TRIP_ID)).willReturn(Optional.of(trip));
            given(orsClient.optimize(any())).willReturn(optimizationResponse_reordered());
            given(orsClient.getDirections(any())).willReturn(directionsResponse());
            given(tripRepository.save(any(Trip.class))).willAnswer(inv -> inv.getArgument(0));

            service.optimize(TRIP_ID, OWNER_ID);

            ArgumentCaptor<Trip> captor = ArgumentCaptor.forClass(Trip.class);
            verify(tripRepository).save(captor.capture());
            String geometry = captor.getValue().getRouteGeometry();
            assertThat(geometry).isNotNull();
            assertThat(geometry).contains("LineString");
            assertThat(geometry).contains("-79.38");
        }

        @Test
        void optimize_sendsCorrectVroomRequest() {
            Trip trip = tripWith3Stops();
            given(tripRepository.findWithStopsById(TRIP_ID)).willReturn(Optional.of(trip));
            given(orsClient.optimize(any())).willReturn(optimizationResponse_reordered());
            given(orsClient.getDirections(any())).willReturn(directionsResponse());
            given(tripRepository.save(any(Trip.class))).willAnswer(inv -> inv.getArgument(0));

            service.optimize(TRIP_ID, OWNER_ID);

            ArgumentCaptor<OrsOptimizationRequest> captor =
                    ArgumentCaptor.forClass(OrsOptimizationRequest.class);
            verify(orsClient).optimize(captor.capture());

            OrsOptimizationRequest request = captor.getValue();
            assertThat(request.jobs()).hasSize(3);
            assertThat(request.vehicles()).hasSize(1);
            assertThat(request.vehicles().get(0).profile()).isEqualTo("driving-car");
            // Vehicle starts and ends at first stop (Toronto: lng=-79.38, lat=43.65)
            assertThat(request.vehicles().get(0).start()).containsExactly(-79.38, 43.65);
            // Round-trip: end = start
            assertThat(request.vehicles().get(0).end()).containsExactly(-79.38, 43.65);
        }

        @Test
        void optimize_callsDirectionsWithOptimizedOrder() {
            Trip trip = tripWith3Stops();
            given(tripRepository.findWithStopsById(TRIP_ID)).willReturn(Optional.of(trip));
            given(orsClient.optimize(any())).willReturn(optimizationResponse_reordered());
            given(orsClient.getDirections(any())).willReturn(directionsResponse());
            given(tripRepository.save(any(Trip.class))).willAnswer(inv -> inv.getArgument(0));

            service.optimize(TRIP_ID, OWNER_ID);

            ArgumentCaptor<OrsDirectionsRequest> captor =
                    ArgumentCaptor.forClass(OrsDirectionsRequest.class);
            verify(orsClient).getDirections(captor.capture());

            // Directions should use optimized order: Toronto → Montreal → Ottawa
            List<List<Double>> coords = captor.getValue().coordinates();
            assertThat(coords).hasSize(3);
            assertThat(coords.get(0)).containsExactly(-79.38, 43.65); // Toronto
            assertThat(coords.get(1)).containsExactly(-73.57, 45.50); // Montreal
            assertThat(coords.get(2)).containsExactly(-75.70, 45.42); // Ottawa
        }
    }

    @Nested
    class Validation {

        @Test
        void optimize_tripNotFound_throwsResourceNotFoundException() {
            given(tripRepository.findWithStopsById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.optimize(999L, OWNER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("999");

            verify(orsClient, never()).optimize(any());
        }

        @Test
        void optimize_nonOwner_throwsForbiddenException() {
            Trip trip = tripWith3Stops();
            given(tripRepository.findWithStopsById(TRIP_ID)).willReturn(Optional.of(trip));

            assertThatThrownBy(() -> service.optimize(TRIP_ID, OTHER_USER_ID))
                    .isInstanceOf(ForbiddenException.class);

            verify(orsClient, never()).optimize(any());
        }

        @Test
        void optimize_singleStop_throwsIllegalStateException() {
            Trip trip = tripWith3Stops();
            // Keep only one stop
            trip.getStops().subList(1, 3).clear();
            given(tripRepository.findWithStopsById(TRIP_ID)).willReturn(Optional.of(trip));

            assertThatThrownBy(() -> service.optimize(TRIP_ID, OWNER_ID))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("at least 2 stops");

            verify(orsClient, never()).optimize(any());
        }
    }

    @Nested
    class BuildOptimizationRequest {

        @Test
        void buildOptimizationRequest_mapsStopIdsToJobIds() {
            Trip trip = tripWith3Stops();
            OrsOptimizationRequest request = service.buildOptimizationRequest(trip.getStops());

            assertThat(request.jobs())
                    .extracting(OrsOptimizationRequest.Job::id)
                    .containsExactly(200L, 201L, 202L);
        }

        @Test
        void buildOptimizationRequest_usesLonLatOrder() {
            Trip trip = tripWith3Stops();
            OrsOptimizationRequest request = service.buildOptimizationRequest(trip.getStops());

            // Toronto: lat=43.65, lng=-79.38 → VROOM [lng, lat] = [-79.38, 43.65]
            assertThat(request.jobs().get(0).location()).containsExactly(-79.38, 43.65);
        }
    }

    @Nested
    class ReorderStops {

        @Test
        void reorderStops_appliesOptimizedOrder() {
            Trip trip = tripWith3Stops();
            List<Stop> stops = trip.getStops();
            OrsOptimizationResponse response = optimizationResponse_reordered();

            service.reorderStops(stops, response);

            // After reorder: Toronto=0, Montreal=1, Ottawa=2
            assertThat(stops.get(0).getId()).isEqualTo(200L); // Toronto
            assertThat(stops.get(0).getStopOrder()).isZero();
            assertThat(stops.get(1).getId()).isEqualTo(202L); // Montreal
            assertThat(stops.get(1).getStopOrder()).isEqualTo(1);
            assertThat(stops.get(2).getId()).isEqualTo(201L); // Ottawa
            assertThat(stops.get(2).getStopOrder()).isEqualTo(2);
        }

        @Test
        void reorderStops_unknownJobId_throwsIllegalStateException() {
            Trip trip = tripWith3Stops();
            OrsOptimizationResponse badResponse = new OrsOptimizationResponse(
                    0, null,
                    List.of(new OrsOptimizationResponse.Route(1L, 100.0, List.of(
                            new OrsOptimizationResponse.Step("start", null, List.of(0.0, 0.0)),
                            new OrsOptimizationResponse.Step("job", 9999L, List.of(0.0, 0.0)),
                            new OrsOptimizationResponse.Step("end", null, List.of(0.0, 0.0))
                    ))));

            assertThatThrownBy(() -> service.reorderStops(trip.getStops(), badResponse))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("9999");
        }
    }
}