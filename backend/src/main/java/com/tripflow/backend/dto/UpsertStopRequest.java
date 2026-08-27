package com.tripflow.backend.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A stop inside a full-itinerary replace ({@link UpdateTripRequest}). Identical to
 * {@link CreateStopRequest} apart from the leading {@code id}, which is what lets
 * {@code TripService.updateTrip} merge by identity instead of deleting and recreating
 * every stop on every update:
 *
 * <ul>
 *   <li>{@code id} matching a stop already on this trip — that stop is updated in place,
 *       so its server-owned state ({@code status}, {@code dayNumber}, {@code plannedTime},
 *       {@code stopType}) and its {@code stop_photos} rows survive.</li>
 *   <li>{@code id} null — a new stop is inserted.</li>
 *   <li>An existing stop whose id is absent from the payload is deleted. That is the only
 *       intentional deletion path.</li>
 * </ul>
 *
 * <p>Deliberately a separate record rather than a nullable {@code id} bolted onto
 * {@link CreateStopRequest}: the create path has no stop to identify, and an {@code id}
 * there would be meaningless input a client could try to set.
 */
public record UpsertStopRequest(
        Long id,
        @NotBlank @Size(max = 200) String name,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @Size(max = 300) String address,
        @Size(max = 150) String externalPlaceId,
        @Size(max = 2000) String notes
) {

    /**
     * Adapter for the two collaborators that are keyed to {@link CreateStopRequest} and are
     * shared with the create path — {@code PlaceResolutionService.resolvePlaces} (the batched
     * place lookup) and {@code StopMapper.toEntity}. Lets the merge reuse both unchanged
     * rather than growing a second copy of place resolution for updates.
     */
    public CreateStopRequest toCreateRequest() {
        return new CreateStopRequest(name, latitude, longitude, address, externalPlaceId, notes);
    }
}
