package com.tripflow.backend.service;



import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripflow.backend.client.ors.OrsClient;
import com.tripflow.backend.client.ors.OrsDirectionsRequest;
import com.tripflow.backend.client.ors.OrsDirectionsResponse;
import com.tripflow.backend.client.ors.OrsOptimizationRequest;
import com.tripflow.backend.client.ors.OrsOptimizationRequest.Job;
import com.tripflow.backend.client.ors.OrsOptimizationRequest.Vehicle;
import com.tripflow.backend.client.ors.OrsOptimizationResponse;
import com.tripflow.backend.domain.Stop;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.dto.TripResponse;
import com.tripflow.backend.exception.ForbiddenException;
import com.tripflow.backend.exception.ResourceNotFoundException;
import com.tripflow.backend.mapper.TripMapper;
import com.tripflow.backend.repository.TripRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates multi-stop route optimization via the OpenRouteService VROOM engine.
 *
 * <p>Flow:
 * <ol>
 *   <li>Load the trip (ownership-checked)</li>
 *   <li>Map stops → VROOM jobs (each stop's Place lat/lng → job location)</li>
 *   <li>Call {@link OrsClient#optimize} to get the optimal visiting order</li>
 *   <li>Parse the response and reorder each {@link Stop#setStopOrder}</li>
 *   <li>Call {@link OrsClient#getDirections} with the optimized order to get the
 *       driveable route GeoJSON geometry</li>
 *   <li>Persist the new stop order + route geometry on the trip</li>
 * </ol>
 *
 * <p>Error handling: {@link com.tripflow.backend.exception.OrsClientException} is
 * thrown by {@code OrsClient} and caught by
 * {@link com.tripflow.backend.exception.GlobalExceptionHandler#handleOrsFailure},
 * which returns a 502 with the canonical {@code ApiError} JSON. This service does
 * <strong>not</strong> add its own try/catch around ORS calls.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RouteOptimizationService {

	 private static final String DRIVING_PROFILE = "driving-car";
	 
	    private final TripRepository tripRepository;
	    private final OrsClient orsClient;
	    private final TripMapper tripMapper;
	    private final ObjectMapper objectMapper = new ObjectMapper();
	 
	    /**
	     * Optimize a trip's stop order and persist the result.
	     *
	     * @param tripId      the trip to optimize
	     * @param requesterId the authenticated user's id (ownership check)
	     * @return the updated {@link TripResponse} with reordered stops
	     * @throws ResourceNotFoundException if the trip does not exist
	     * @throws ForbiddenException        if the requester is not the trip owner
	     * @throws IllegalStateException     if the trip has fewer than 2 stops
	     */
	    @Transactional
	    public TripResponse optimize(Long tripId, Long requesterId) {
	        Trip trip = loadOwnedTrip(tripId, requesterId);
	        List<Stop> stops = trip.getStops();
	 
	        if (stops.size() < 2) {
	            throw new IllegalStateException(
	                    "Trip must have at least 2 stops to optimize (tripId=" + tripId + ")");
	        }
	 
	        log.info("Optimizing route tripId={} stops={}", tripId, stops.size());
	 
	        // --- 1. Build VROOM optimization request ---
	        OrsOptimizationRequest optimizationRequest = buildOptimizationRequest(stops);
	 
	        // --- 2. Call ORS /optimization ---
	        OrsOptimizationResponse optimizationResponse = orsClient.optimize(optimizationRequest);
	 
	        // --- 3. Reorder stops based on optimized job sequence ---
	        reorderStops(stops, optimizationResponse);
	 
	        // --- 4. Fetch driveable route geometry in optimized order ---
	        OrsDirectionsResponse directionsResponse = fetchRouteGeometry(stops);
	 
	        // --- 5. Persist route geometry on the trip ---
	        persistRouteGeometry(trip, directionsResponse);
	 
	        Trip saved = tripRepository.save(trip);
	        log.info("Route optimized tripId={} newOrder={}",
	                tripId, stops.stream().map(s -> s.getId() + "→" + s.getStopOrder()).toList());
	 
	        return tripMapper.toResponse(saved);
	    }
	 
	    // ── request building ─────────────────────────────────────────────────────
	 
	    OrsOptimizationRequest buildOptimizationRequest(List<Stop> stops) {
	        List<Job> jobs = stops.stream()
	                .map(stop -> new Job(
	                        stop.getId(),
	                        List.of(stop.getPlace().getLongitude(), stop.getPlace().getLatitude())))
	                .toList();
	 
	        // Vehicle starts and ends at the first stop's location (round-trip TSP).
	        // VROOM optimizes the intermediate visiting order. Round-trip is used
	        // instead of open-ended to avoid modifying the existing OrsOptimizationRequest
	        // record's Jackson serialization (null end would serialize as "end": null
	        // rather than being omitted, which some ORS versions reject).
	        Stop first = stops.get(0);
	        List<Double> startLocation = List.of(
	                first.getPlace().getLongitude(), first.getPlace().getLatitude());
	 
	        Vehicle vehicle = new Vehicle(1L, DRIVING_PROFILE, startLocation, startLocation);
	 
	        return new OrsOptimizationRequest(jobs, List.of(vehicle));
	    }
	 
	    // ── response parsing ─────────────────────────────────────────────────────
	 
	    void reorderStops(List<Stop> stops, OrsOptimizationResponse response) {
	        // Extract job IDs in optimized order (skip "start" and "end" steps)
	        List<Long> optimizedOrder = response.routes().get(0).steps().stream()
	                .filter(step -> "job".equals(step.type()))
	                .map(OrsOptimizationResponse.Step::job)
	                .toList();
	 
	        // Build a lookup: stop.id → Stop
	        Map<Long, Stop> stopById = stops.stream()
	                .collect(Collectors.toMap(Stop::getId, Function.identity()));
	 
	        // Assign new stopOrder based on position in optimized sequence
	        for (int i = 0; i < optimizedOrder.size(); i++) {
	            Stop stop = stopById.get(optimizedOrder.get(i));
	            if (stop == null) {
	                throw new IllegalStateException(
	                        "VROOM returned unknown job id " + optimizedOrder.get(i));
	            }
	            stop.setStopOrder(i);
	        }
	 
	        // Re-sort the in-memory list to match new order (Hibernate persists by index)
	        stops.sort(java.util.Comparator.comparingInt(Stop::getStopOrder));
	    }
	 
	    // ── route geometry ───────────────────────────────────────────────────────
	 
	    OrsDirectionsResponse fetchRouteGeometry(List<Stop> stops) {
	        // Build coordinate pairs in optimized order (already sorted)
	        List<double[]> coordinates = stops.stream()
	                .map(stop -> new double[]{
	                        stop.getPlace().getLongitude(),
	                        stop.getPlace().getLatitude()})
	                .toList();
	 
	        return orsClient.getDirections(OrsDirectionsRequest.of(coordinates));
	    }
	 
	    void persistRouteGeometry(Trip trip, OrsDirectionsResponse directionsResponse) {
	        // Store the first feature's geometry as the route line
	        OrsDirectionsResponse.Geometry geometry =
	                directionsResponse.features().get(0).geometry();
	        try {
	            trip.setRouteGeometry(objectMapper.writeValueAsString(geometry));
	        } catch (JsonProcessingException ex) {
	            // Geometry is always a simple GeoJSON object — serialization should never fail.
	            // Log and continue without geometry rather than failing the entire optimization.
	            log.error("Failed to serialize route geometry tripId={}: {}",
	                    trip.getId(), ex.getMessage());
	        }
	    }
	 
	    // ── shared helpers ───────────────────────────────────────────────────────
	 
	    private Trip loadOwnedTrip(Long tripId, Long requesterId) {
	        Trip trip = tripRepository.findWithStopsById(tripId)
	                .orElseThrow(() -> new ResourceNotFoundException("Trip not found: " + tripId));
	        if (!trip.getUser().getId().equals(requesterId)) {
	            log.debug("Optimize ownership check failed tripId={} ownerId={} requesterId={}",
	                    tripId, trip.getUser().getId(), requesterId);
	            throw new ForbiddenException("You do not have access to this trip");
	        }
	        return trip;
	    }
}
