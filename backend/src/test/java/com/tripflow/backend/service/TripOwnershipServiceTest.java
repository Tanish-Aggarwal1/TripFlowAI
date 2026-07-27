package com.tripflow.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.exception.ForbiddenException;
import com.tripflow.backend.exception.ResourceNotFoundException;
import com.tripflow.backend.repository.TripRepository;

@ExtendWith(MockitoExtension.class)
class TripOwnershipServiceTest {

	@Mock private TripRepository tripRepository;

	private TripOwnershipService tripOwnershipService;

	@BeforeEach
	void setUp() {
		tripOwnershipService = new TripOwnershipService(tripRepository);
	}

	@Test
	void loadOwnedTrip_owner_returnsTrip() {
		User owner = new User();
		owner.setId(1L);

		Trip trip = new Trip();
		trip.setId(50L);
		trip.setUser(owner);

		when(tripRepository.findWithStopsById(50L)).thenReturn(Optional.of(trip));

		Trip result = tripOwnershipService.loadOwnedTrip(50L, 1L);

		assertThat(result).isEqualTo(trip);
	}

	@Test
	void loadOwnedTrip_nonOwner_throwsForbidden() {
		User owner = new User();
		owner.setId(1L);

		Trip trip = new Trip();
		trip.setId(50L);
		trip.setUser(owner);

		when(tripRepository.findWithStopsById(50L)).thenReturn(Optional.of(trip));

		assertThatThrownBy(() -> tripOwnershipService.loadOwnedTrip(50L, 2L))
				.isInstanceOf(ForbiddenException.class);
	}

	@Test
	void loadOwnedTrip_missingTrip_throwsNotFound() {
		when(tripRepository.findWithStopsById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> tripOwnershipService.loadOwnedTrip(999L, 1L))
				.isInstanceOf(ResourceNotFoundException.class);
	}
}
