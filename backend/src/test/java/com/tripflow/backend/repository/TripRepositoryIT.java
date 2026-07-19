package com.tripflow.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.Session;
import org.hibernate.stat.Statistics;
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
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@DataJpaTest
@Import(JpaConfig.class)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TripRepositoryIT {

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PlaceRepository placeRepository;

	@PersistenceContext
	private EntityManager entityManager;

	@Test
	void saveAndFindById() {
		User user = new User();
		user.setUsername("tripowner");
		user.setEmail("owner@tripflow.com");
		user.setPasswordHash("hashedpassword123");
		User savedUser = userRepository.save(user);

		Trip trip = new Trip();
		trip.setUser(savedUser);
		trip.setTitle("Ontario Road Trip");
		trip.setDescription("A test trip across Ontario");

		Trip saved = tripRepository.save(trip);

		assertThat(saved.getId()).isNotNull();
		assertThat(tripRepository.findById(saved.getId())).isPresent();
		assertThat(tripRepository.findById(saved.getId()).get().getTitle())
				.isEqualTo("Ontario Road Trip");
	}

	@Test
	void findWithStopsById_singleTrip10Stops_issuesConstantQueryCount() {
		User user = new User();
		user.setUsername("statsowner");
		user.setEmail("stats@tripflow.com");
		user.setPasswordHash("hashedpassword123");
		User savedUser = userRepository.save(user);

		Trip trip = new Trip();
		trip.setUser(savedUser);
		trip.setTitle("Ten Stop Trip");

		for (int i = 0; i < 10; i++) {
			Place place = new Place();
			place.setName("Place " + i);
			place.setLatitude(43.0 + i * 0.01);
			place.setLongitude(-79.0 - i * 0.01);
			Place savedPlace = placeRepository.save(place);

			Stop stop = new Stop();
			stop.setTrip(trip);
			stop.setPlace(savedPlace);
			stop.setStopOrder(i);
			trip.getStops().add(stop);
		}

		Trip savedTrip = tripRepository.save(trip);
		entityManager.flush();
		entityManager.clear();

		Session session = entityManager.unwrap(Session.class);
		Statistics stats = session.getSessionFactory().getStatistics();
		stats.setStatisticsEnabled(true);
		stats.clear();

		Trip found = tripRepository.findWithStopsById(savedTrip.getId()).orElseThrow();

		// Force full materialization of the fetch-joined graph before counting.
		// findWithStopsById's @EntityGraph covers "stops" and "stops.place" only
		// (NOT "user") — deliberately not touching found.getUser() here, since that
		// would add a separate lazy-load query outside what this entity graph fixes.
		for (Stop stop : found.getStops()) {
			assertThat(stop.getPlace().getName()).isNotBlank();
		}

		long statementCount = stats.getPrepareStatementCount();

		// TEMP: no Docker available locally on any team machine, so this can only be
		// measured via CI. Printed here for the first CI run — check the Actions log,
		// then replace this println + the loose upper bound below with an exact
		// isEqualTo(<measured value>) and a dated comment, per SCRUM-196's AC.
		System.out.println("MEASURED findWithStopsById statement count: " + statementCount);

		assertThat(statementCount)
				.as("findWithStopsById should issue a small constant number of queries, "
						+ "not one per stop (10 stops in this trip)")
				.isLessThanOrEqualTo(5);
	}

	@Test
	void findWithStopsById_missingId_returnsEmpty() {
		assertThat(tripRepository.findWithStopsById(999_999L)).isEmpty();
	}

	@Test
	void findWithStopsById_existingIdNoStops_returnsEmptyStopsList() {
		User user = new User();
		user.setUsername("nostopsowner");
		user.setEmail("nostops@tripflow.com");
		user.setPasswordHash("hashedpassword123");
		User savedUser = userRepository.save(user);

		Trip trip = new Trip();
		trip.setUser(savedUser);
		trip.setTitle("Empty Trip");
		Trip savedTrip = tripRepository.save(trip);

		Trip found = tripRepository.findWithStopsById(savedTrip.getId()).orElseThrow();

		assertThat(found.getStops()).isEmpty();
	}
}