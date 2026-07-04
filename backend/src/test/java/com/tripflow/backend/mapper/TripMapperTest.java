package com.tripflow.backend.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.tripflow.backend.beans.Trip;
import com.tripflow.backend.beans.User;
import com.tripflow.backend.beans.enums.TripStatus;
import com.tripflow.backend.beans.enums.TripVisibility;
import com.tripflow.backend.dto.CreateTripRequest;

public class TripMapperTest {
	@Test
    void toEntity_neverSetsServerOwnedFields() {
        CreateTripRequest request = new CreateTripRequest();
        request.setTitle("Weekend Trip");
        request.setDescription("A nice trip");
        request.setTags(List.of("cottage"));
        request.setVisibility(TripVisibility.PRIVATE);

        User owner = new User();
        owner.setUsername("tanish");

        Trip trip = TripMapper.toEntity(request, owner);

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
}
