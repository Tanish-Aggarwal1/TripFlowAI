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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import com.tripflow.backend.config.JpaConfig;
import com.tripflow.backend.domain.Place;
import com.tripflow.backend.domain.Stop;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.domain.enums.StopStatus;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.TripOwnerSummaryResponse;
import com.tripflow.backend.dto.TripSummaryResponse;
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

		// Measured 2026-07-19 via CI (no Docker available locally on any team machine):
		// findWithStopsById issues exactly 1 SQL statement for a 10-stop trip — Hibernate
		// resolves the @EntityGraph("stops", "stops.place") as a single query with JOINs
		// across trips -> stops -> places, not the naive 1-per-stop N+1 pattern SCRUM-108
		// was fixing. If this number ever regresses upward, the entity graph likely broke.
		assertThat(statementCount)
				.as("findWithStopsById should issue a single query for trip+stops+place, "
						+ "not one per stop (10 stops in this trip)")
				.isEqualTo(1);
	}

	@Test
	void findSummariesByVisibility_multiplePublicTripsWithStops_issuesConstantQueryCount() {
		User user = new User();
		user.setUsername("discoveryowner");
		user.setEmail("discovery@tripflow.com");
		user.setPasswordHash("hashedpassword123");
		User savedUser = userRepository.save(user);

		for (int t = 0; t < 5; t++) {
			Trip trip = new Trip();
			trip.setUser(savedUser);
			trip.setTitle("Public Trip " + t);
			trip.setVisibility(TripVisibility.PUBLIC);

			for (int i = 0; i < 3; i++) {
				Place place = new Place();
				place.setName("Place " + t + "-" + i);
				place.setLatitude(43.0 + i * 0.01);
				place.setLongitude(-79.0 - i * 0.01);
				Place savedPlace = placeRepository.save(place);

				Stop stop = new Stop();
				stop.setTrip(trip);
				stop.setPlace(savedPlace);
				stop.setStopOrder(i);
				trip.getStops().add(stop);
			}
			tripRepository.save(trip);
		}

		Trip privateTrip = new Trip();
		privateTrip.setUser(savedUser);
		privateTrip.setTitle("Private Trip");
		privateTrip.setVisibility(TripVisibility.PRIVATE);
		tripRepository.save(privateTrip);

		entityManager.flush();
		entityManager.clear();

		Session session = entityManager.unwrap(Session.class);
		Statistics stats = session.getSessionFactory().getStatistics();
		stats.setStatisticsEnabled(true);
		stats.clear();

		Page<TripSummaryResponse> page = tripRepository.findSummariesByVisibility(
				TripVisibility.PUBLIC, PageRequest.of(0, 20));

		long statementCount = stats.getPrepareStatementCount();

		assertThat(page.getContent()).hasSize(5);
		assertThat(page.getContent()).allMatch(summary -> summary.visibility() == TripVisibility.PUBLIC);
		// The flat TripSummaryResponse projection (no fetch join on stops) means paging
		// happens in SQL: 1 statement here (Spring Data skips the extra count query
		// since content size < page size, letting it infer totalElements), never
		// one-per-trip regardless of how many public trips exist.
		assertThat(statementCount)
				.as("findSummariesByVisibility should issue a constant, small number of "
						+ "queries, not scale with the number of trips")
				.isEqualTo(1);
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

	// ---------- findSummariesByUserId: completion (EXPORT-03) ----------

	private User savedUser(String username) {
		User user = new User();
		user.setUsername(username);
		user.setEmail(username + "@tripflow.com");
		user.setPasswordHash("hashedpassword123");
		return userRepository.save(user);
	}

	private void addStop(Trip trip, int index, StopStatus status) {
		Place place = new Place();
		place.setName("Place " + index);
		place.setLatitude(43.0 + index * 0.01);
		place.setLongitude(-79.0 - index * 0.01);
		Place savedPlace = placeRepository.save(place);

		Stop stop = new Stop();
		stop.setTrip(trip);
		stop.setPlace(savedPlace);
		stop.setStopOrder(index);
		stop.setStatus(status);
		trip.getStops().add(stop);
	}

	@Test
	void findSummariesByUserId_fiveStopsThreeVisited_returnsVisitedAndStopCounts() {
		User user = savedUser("completionowner1");
		Trip trip = new Trip();
		trip.setUser(user);
		trip.setTitle("Five Stop Trip");
		addStop(trip, 0, StopStatus.VISITED);
		addStop(trip, 1, StopStatus.VISITED);
		addStop(trip, 2, StopStatus.VISITED);
		addStop(trip, 3, StopStatus.PLANNED);
		addStop(trip, 4, StopStatus.SKIPPED);
		tripRepository.save(trip);
		entityManager.flush();
		entityManager.clear();

		Page<TripOwnerSummaryResponse> page = tripRepository.findSummariesByUserId(user.getId(), PageRequest.of(0, 20));

		assertThat(page.getContent()).hasSize(1);
		TripOwnerSummaryResponse summary = page.getContent().get(0);
		assertThat(summary.stopCount()).isEqualTo(5L);
		assertThat(summary.visitedStopCount()).isEqualTo(3L);
		assertThat(summary.completionPercentage()).isCloseTo(0.6, org.assertj.core.data.Offset.offset(0.0001));
	}

	@Test
	void findSummariesByUserId_allStopsSkipped_visitedZeroWithFullStopCount() {
		// D-06: SKIPPED stops count in the denominator only — proven here against real SQL,
		// not just the mapper's in-memory arithmetic.
		User user = savedUser("completionowner2");
		Trip trip = new Trip();
		trip.setUser(user);
		trip.setTitle("All Skipped Trip");
		addStop(trip, 0, StopStatus.SKIPPED);
		addStop(trip, 1, StopStatus.SKIPPED);
		addStop(trip, 2, StopStatus.SKIPPED);
		tripRepository.save(trip);
		entityManager.flush();
		entityManager.clear();

		Page<TripOwnerSummaryResponse> page = tripRepository.findSummariesByUserId(user.getId(), PageRequest.of(0, 20));

		TripOwnerSummaryResponse summary = page.getContent().get(0);
		assertThat(summary.visitedStopCount()).isZero();
		assertThat(summary.stopCount()).isEqualTo(3L);
	}

	@Test
	void findSummariesByUserId_zeroStops_completionIsZero() {
		// D-07: zero-stop trips must never null-pointer or divide-by-zero at the SQL layer.
		User user = savedUser("completionowner3");
		Trip trip = new Trip();
		trip.setUser(user);
		trip.setTitle("Empty Trip");
		tripRepository.save(trip);
		entityManager.flush();
		entityManager.clear();

		Page<TripOwnerSummaryResponse> page = tripRepository.findSummariesByUserId(user.getId(), PageRequest.of(0, 20));

		TripOwnerSummaryResponse summary = page.getContent().get(0);
		assertThat(summary.stopCount()).isZero();
		assertThat(summary.visitedStopCount()).isZero();
		assertThat(summary.completionPercentage()).isEqualTo(0.0);
	}

	@Test
	void findSummariesByUserId_twoTripsDifferentVisitedCounts_countsAreIndependentPerRow() {
		// Proves the correlated subquery correlates per Trip row rather than aggregating
		// across the whole page.
		User user = savedUser("completionowner4");

		Trip tripA = new Trip();
		tripA.setUser(user);
		tripA.setTitle("Trip A");
		addStop(tripA, 0, StopStatus.VISITED);
		addStop(tripA, 1, StopStatus.PLANNED);
		tripRepository.save(tripA);

		Trip tripB = new Trip();
		tripB.setUser(user);
		tripB.setTitle("Trip B");
		addStop(tripB, 0, StopStatus.VISITED);
		addStop(tripB, 1, StopStatus.VISITED);
		addStop(tripB, 2, StopStatus.VISITED);
		tripRepository.save(tripB);

		entityManager.flush();
		entityManager.clear();

		Page<TripOwnerSummaryResponse> page = tripRepository.findSummariesByUserId(user.getId(), PageRequest.of(0, 20));

		assertThat(page.getContent()).hasSize(2);
		TripOwnerSummaryResponse a = page.getContent().stream()
				.filter(s -> s.title().equals("Trip A")).findFirst().orElseThrow();
		TripOwnerSummaryResponse b = page.getContent().stream()
				.filter(s -> s.title().equals("Trip B")).findFirst().orElseThrow();
		assertThat(a.visitedStopCount()).isEqualTo(1L);
		assertThat(a.stopCount()).isEqualTo(2L);
		assertThat(b.visitedStopCount()).isEqualTo(3L);
		assertThat(b.stopCount()).isEqualTo(3L);
	}

	/**
	 * D-08 tripwire: {@link TripSummaryResponse} backs both discovery-feed queries
	 * (findSummariesByVisibility, searchPublicTrips). If a future change adds a completion
	 * field to it, every user's progress starts leaking to strangers via the public feed.
	 * This assertion is the first thing that breaks if that happens — fix the leak (fork
	 * onto TripOwnerSummaryResponse-shaped record instead), don't bump this constant.
	 */
	@Test
	void tripSummaryResponse_recordComponentCount_staysAtEightForD08() {
		assertThat(TripSummaryResponse.class.getRecordComponents()).hasSize(8);
	}
}