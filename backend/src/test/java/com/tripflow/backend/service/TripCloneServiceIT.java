package com.tripflow.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.tripflow.backend.config.JpaConfig;
import com.tripflow.backend.domain.Place;
import com.tripflow.backend.domain.Stop;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.TripResponse;
import com.tripflow.backend.exception.ResourceNotFoundException;
import com.tripflow.backend.mapper.StopMapper;
import com.tripflow.backend.mapper.TripMapper;
import com.tripflow.backend.repository.PlaceRepository;
import com.tripflow.backend.repository.TripRepository;
import com.tripflow.backend.repository.UserRepository;
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Real-Postgres IT for {@link TripCloneService} (SCRUM-162) — asserts the deep-copy
 * semantics that a controller-level test can't see through {@code StopResponse} alone:
 * that cloned Stops reference the exact same {@code Place} row id (shared, not
 * duplicated), not merely equal coordinates.
 */
@DataJpaTest
@Import(JpaConfig.class)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TripCloneServiceIT {

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PlaceRepository placeRepository;

	@PersistenceContext
	private EntityManager entityManager;

	private TripCloneService tripCloneService;

	@BeforeEach
	void setUp() {
		TripMapper tripMapper = new TripMapper(new StopMapper());
		tripCloneService = new TripCloneService(tripRepository, userRepository, tripMapper);
	}

	private User saveUser(String suffix) {
		User user = new User();
		user.setUsername("cloneservice-" + suffix);
		user.setEmail("cloneservice-" + suffix + "@tripflow.com");
		user.setPasswordHash("hashedpassword123");
		return userRepository.save(user);
	}

	private Place savePlace(String name) {
		Place place = new Place();
		place.setName(name);
		place.setLatitude(45.0);
		place.setLongitude(-75.0);
		return placeRepository.save(place);
	}

	@Test
	void cloneTrip_sharesPlaceRows_doesNotDuplicateThem() {
		User owner = saveUser("owner1");
		User cloner = saveUser("cloner1");
		Place place = savePlace("Byward Market");

		Trip trip = new Trip();
		trip.setUser(owner);
		trip.setTitle("Ottawa Weekend");
		trip.setVisibility(TripVisibility.PUBLIC);

		Stop stop = new Stop();
		stop.setTrip(trip);
		stop.setPlace(place);
		stop.setStopOrder(0);
		trip.getStops().add(stop);

		Trip saved = tripRepository.save(trip);
		entityManager.flush();
		entityManager.clear();

		long placeCountBefore = placeRepository.count();

		TripResponse cloneResponse = tripCloneService.cloneTrip(saved.getId(), cloner.getId());

		long placeCountAfter = placeRepository.count();
		assertThat(placeCountAfter).as("cloning must not create a new Place row").isEqualTo(placeCountBefore);

		Trip clone = tripRepository.findWithStopsById(cloneResponse.id()).orElseThrow();
		assertThat(clone.getStops()).hasSize(1);
		assertThat(clone.getStops().get(0).getPlace().getId())
				.as("cloned Stop must reference the exact same Place row, not a copy")
				.isEqualTo(place.getId());
	}

	@Test
	void cloneTrip_multipleStops_preservesStopOrderAndOwnerAndTitle() {
		User owner = saveUser("owner2");
		User cloner = saveUser("cloner2");
		Place placeA = savePlace("Place A");
		Place placeB = savePlace("Place B");

		Trip trip = new Trip();
		trip.setUser(owner);
		trip.setTitle("Multi Stop Trip");
		trip.setVisibility(TripVisibility.PUBLIC);

		Stop stopA = new Stop();
		stopA.setTrip(trip);
		stopA.setPlace(placeA);
		stopA.setStopOrder(0);
		trip.getStops().add(stopA);

		Stop stopB = new Stop();
		stopB.setTrip(trip);
		stopB.setPlace(placeB);
		stopB.setStopOrder(1);
		trip.getStops().add(stopB);

		Trip saved = tripRepository.save(trip);
		entityManager.flush();
		entityManager.clear();

		TripResponse cloneResponse = tripCloneService.cloneTrip(saved.getId(), cloner.getId());

		assertThat(cloneResponse.title()).isEqualTo("Copy of Multi Stop Trip");
		assertThat(cloneResponse.visibility()).isEqualTo(TripVisibility.PRIVATE);
		assertThat(cloneResponse.ownerId()).isEqualTo(cloner.getId());

		Trip clone = tripRepository.findWithStopsById(cloneResponse.id()).orElseThrow();
		assertThat(clone.getStops()).hasSize(2);
		assertThat(clone.getStops().get(0).getStopOrder()).isEqualTo(0);
		assertThat(clone.getStops().get(0).getPlace().getId()).isEqualTo(placeA.getId());
		assertThat(clone.getStops().get(1).getStopOrder()).isEqualTo(1);
		assertThat(clone.getStops().get(1).getPlace().getId()).isEqualTo(placeB.getId());
	}

	@Test
	void cloneTrip_privateTripNotOwner_throwsResourceNotFound() {
		User owner = saveUser("owner3");
		User other = saveUser("other3");

		Trip trip = new Trip();
		trip.setUser(owner);
		trip.setTitle("Private Trip");
		trip.setVisibility(TripVisibility.PRIVATE);
		Trip saved = tripRepository.save(trip);
		entityManager.flush();
		entityManager.clear();

		assertThatThrownBy(() -> tripCloneService.cloneTrip(saved.getId(), other.getId()))
				.isInstanceOf(ResourceNotFoundException.class);
	}
}
