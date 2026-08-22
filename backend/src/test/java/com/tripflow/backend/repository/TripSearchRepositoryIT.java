package com.tripflow.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.tripflow.backend.config.JpaConfig;
import com.tripflow.backend.domain.Place;
import com.tripflow.backend.domain.Stop;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.domain.enums.TripStatus;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.TripOwnerSummaryResponse;
import com.tripflow.backend.dto.TripSearchFilters;
import com.tripflow.backend.dto.TripSummaryResponse;
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** SCRUM-163/SEARCH-01: TripSearchRepositoryImpl's ILIKE title/tag/place-name matching,
 * PUBLIC-only vs owner-only scoping, filter AND-combination, and case-insensitivity,
 * exercised against real Postgres. */
@DataJpaTest
@Import(JpaConfig.class)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TripSearchRepositoryIT {

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@PersistenceContext
	private EntityManager entityManager;

	private User saveUser(String suffix) {
		User user = new User();
		user.setUsername("search-" + suffix);
		user.setEmail("search-" + suffix + "@tripflow.com");
		user.setPasswordHash("hashedpassword123");
		return userRepository.save(user);
	}

	private Trip saveTrip(User owner, String title, TripVisibility visibility, List<String> tags) {
		Trip trip = new Trip();
		trip.setUser(owner);
		trip.setTitle(title);
		trip.setVisibility(visibility);
		if (tags != null) {
			trip.setTags(tags);
		}
		return tripRepository.save(trip);
	}

	private Place savePlace(String name) {
		Place place = new Place();
		place.setName(name);
		place.setLatitude(45.0);
		place.setLongitude(-75.0);
		entityManager.persist(place);
		return place;
	}

	private Stop addStop(Trip trip, Place place, Integer dayNumber) {
		Stop stop = new Stop();
		stop.setTrip(trip);
		stop.setPlace(place);
		stop.setStopOrder(0);
		stop.setDayNumber(dayNumber);
		entityManager.persist(stop);
		return stop;
	}

	@Test
	void searchPublicTrips_titleSubstringCaseInsensitive_matches() {
		User owner = saveUser("owner1");
		saveTrip(owner, "Ottawa Weekend Getaway", TripVisibility.PUBLIC, null);
		saveTrip(owner, "Quiet Cottage Retreat", TripVisibility.PUBLIC, null);
		entityManager.flush();

		Page<TripSummaryResponse> result = tripRepository.searchPublicTrips("ottawa", PageRequest.of(0, 20));

		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().get(0).title()).isEqualTo("Ottawa Weekend Getaway");
	}

	@Test
	void searchPublicTrips_tagMatch_matches() {
		User owner = saveUser("owner2");
		saveTrip(owner, "Unrelated Title", TripVisibility.PUBLIC, List.of("hiking", "mountains"));
		saveTrip(owner, "Also Unrelated", TripVisibility.PUBLIC, List.of("beach"));
		entityManager.flush();

		Page<TripSummaryResponse> result = tripRepository.searchPublicTrips("hiking", PageRequest.of(0, 20));

		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().get(0).title()).isEqualTo("Unrelated Title");
	}

	@Test
	void searchPublicTrips_privateTripMatchingTitle_isExcluded() {
		User owner = saveUser("owner3");
		saveTrip(owner, "Secret Ottawa Trip", TripVisibility.PRIVATE, null);
		entityManager.flush();

		Page<TripSummaryResponse> result = tripRepository.searchPublicTrips("ottawa", PageRequest.of(0, 20));

		assertThat(result.getContent()).isEmpty();
	}

	@Test
	void searchPublicTrips_noMatches_returnsEmptyPage() {
		User owner = saveUser("owner4");
		saveTrip(owner, "Totally Different", TripVisibility.PUBLIC, null);
		entityManager.flush();

		Page<TripSummaryResponse> result = tripRepository.searchPublicTrips("zzzznomatch", PageRequest.of(0, 20));

		assertThat(result.getContent()).isEmpty();
		assertThat(result.getTotalElements()).isEqualTo(0);
	}

	@Test
	void searchPublicTrips_matchesTitleOrTags_notBoth() {
		User owner = saveUser("owner5");
		saveTrip(owner, "Rome Adventure", TripVisibility.PUBLIC, List.of("italy"));
		saveTrip(owner, "Paris Getaway", TripVisibility.PUBLIC, List.of("rome-inspired"));
		entityManager.flush();

		Page<TripSummaryResponse> result = tripRepository.searchPublicTrips("rome", PageRequest.of(0, 20));

		assertThat(result.getContent()).hasSize(2);
	}

	// ---------- searchOwnedTrips: match surface (D-10) ----------

	@Test
	void searchOwnedTrips_tagMatch_matches() {
		User owner = saveUser("tagowner");
		saveTrip(owner, "Unrelated Title", TripVisibility.PRIVATE, List.of("hiking", "mountains"));
		saveTrip(owner, "Also Unrelated", TripVisibility.PRIVATE, List.of("beach"));
		entityManager.flush();

		Page<TripOwnerSummaryResponse> result = tripRepository.searchOwnedTrips(
				owner.getId(), "%hiking%", TripSearchFilters.none(), PageRequest.of(0, 20));

		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().get(0).title()).isEqualTo("Unrelated Title");
	}

	@Test
	void searchOwnedTrips_placeNameMatch_matches() {
		User owner = saveUser("placeowner");
		Trip trip = saveTrip(owner, "Road Trip", TripVisibility.PRIVATE, null);
		addStop(trip, savePlace("Niagara Falls"), null);
		entityManager.flush();

		Page<TripOwnerSummaryResponse> result = tripRepository.searchOwnedTrips(
				owner.getId(), "%niagara%", TripSearchFilters.none(), PageRequest.of(0, 20));

		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().get(0).title()).isEqualTo("Road Trip");
	}

	@Test
	void searchOwnedTrips_titleAndPlaceNameBothMatch_returnsOnce() {
		User owner = saveUser("bothowner");
		Trip trip = saveTrip(owner, "Ottawa Adventure", TripVisibility.PRIVATE, null);
		addStop(trip, savePlace("Ottawa River"), null);
		entityManager.flush();

		Page<TripOwnerSummaryResponse> result = tripRepository.searchOwnedTrips(
				owner.getId(), "%ottawa%", TripSearchFilters.none(), PageRequest.of(0, 20));

		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getTotalElements()).isEqualTo(1);
	}

	@Test
	void searchOwnedTrips_threeStopsAllMatchPlaceName_returnsOnce() {
		User owner = saveUser("threestops");
		Trip trip = saveTrip(owner, "Museum Tour", TripVisibility.PRIVATE, null);
		addStop(trip, savePlace("Ottawa Museum One"), 1);
		addStop(trip, savePlace("Ottawa Museum Two"), 1);
		addStop(trip, savePlace("Ottawa Museum Three"), 1);
		entityManager.flush();

		Page<TripOwnerSummaryResponse> result = tripRepository.searchOwnedTrips(
				owner.getId(), "%ottawa%", TripSearchFilters.none(), PageRequest.of(0, 20));

		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getTotalElements()).isEqualTo(1);
	}

	@Test
	void searchOwnedTrips_anotherUsersPublicTripMatching_returnsNothing() {
		User owner = saveUser("scopeowner");
		User stranger = saveUser("stranger");
		saveTrip(stranger, "Ottawa Getaway", TripVisibility.PUBLIC, null);
		entityManager.flush();

		Page<TripOwnerSummaryResponse> result = tripRepository.searchOwnedTrips(
				owner.getId(), "%ottawa%", TripSearchFilters.none(), PageRequest.of(0, 20));

		assertThat(result.getContent()).isEmpty();
		assertThat(result.getTotalElements()).isEqualTo(0);
	}

	@Test
	void searchOwnedTrips_noMatches_returnsEmptyPage() {
		User owner = saveUser("nomatchowner");
		saveTrip(owner, "Totally Different", TripVisibility.PRIVATE, null);
		entityManager.flush();

		Page<TripOwnerSummaryResponse> result = tripRepository.searchOwnedTrips(
				owner.getId(), "%zzzznomatch%", TripSearchFilters.none(), PageRequest.of(0, 20));

		assertThat(result.getContent()).isEmpty();
		assertThat(result.getTotalElements()).isEqualTo(0);
	}

	@Test
	void searchOwnedTrips_matchesExactlyOneTrip_returnsOneItem() {
		User owner = saveUser("onematchowner");
		saveTrip(owner, "Ottawa Weekend", TripVisibility.PRIVATE, null);
		saveTrip(owner, "Toronto Weekend", TripVisibility.PRIVATE, null);
		entityManager.flush();

		Page<TripOwnerSummaryResponse> result = tripRepository.searchOwnedTrips(
				owner.getId(), "%ottawa%", TripSearchFilters.none(), PageRequest.of(0, 20));

		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getTotalElements()).isEqualTo(1);
	}

	// ---------- searchOwnedTrips: filters (D-12/D-13/D-14) ----------

	@Test
	void searchOwnedTrips_statusFilter_narrowsToMatchingStatus() {
		User owner = saveUser("statusowner");
		Trip active = saveTrip(owner, "Active Trip", TripVisibility.PRIVATE, null);
		active.setStatus(TripStatus.ACTIVE);
		tripRepository.save(active);
		saveTrip(owner, "Draft Trip", TripVisibility.PRIVATE, null);
		entityManager.flush();

		TripSearchFilters filters = new TripSearchFilters(TripStatus.ACTIVE, null, null, null, null);
		Page<TripOwnerSummaryResponse> result = tripRepository.searchOwnedTrips(
				owner.getId(), null, filters, PageRequest.of(0, 20));

		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().get(0).title()).isEqualTo("Active Trip");
	}

	@Test
	void searchOwnedTrips_visibilityFilter_narrowsToMatchingVisibility() {
		User owner = saveUser("visowner");
		saveTrip(owner, "Public Trip", TripVisibility.PUBLIC, null);
		saveTrip(owner, "Private Trip", TripVisibility.PRIVATE, null);
		entityManager.flush();

		TripSearchFilters filters = new TripSearchFilters(null, TripVisibility.PUBLIC, null, null, null);
		Page<TripOwnerSummaryResponse> result = tripRepository.searchOwnedTrips(
				owner.getId(), null, filters, PageRequest.of(0, 20));

		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().get(0).title()).isEqualTo("Public Trip");
	}

	@Test
	void searchOwnedTrips_startDateRangeFilter_narrowsByTripStartDate() {
		User owner = saveUser("dateowner");
		Trip inRange = saveTrip(owner, "June Trip", TripVisibility.PRIVATE, null);
		inRange.setStartDate(LocalDate.of(2026, 6, 15));
		tripRepository.save(inRange);
		Trip outOfRange = saveTrip(owner, "December Trip", TripVisibility.PRIVATE, null);
		outOfRange.setStartDate(LocalDate.of(2026, 12, 1));
		tripRepository.save(outOfRange);
		entityManager.flush();

		TripSearchFilters filters = new TripSearchFilters(
				null, null, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null);
		Page<TripOwnerSummaryResponse> result = tripRepository.searchOwnedTrips(
				owner.getId(), null, filters, PageRequest.of(0, 20));

		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().get(0).title()).isEqualTo("June Trip");
	}

	@Test
	void searchOwnedTrips_durationDaysFilter_narrowsByMaxDayNumber() {
		User owner = saveUser("durationowner");
		Trip threeDay = saveTrip(owner, "Three Day Trip", TripVisibility.PRIVATE, null);
		addStop(threeDay, savePlace("Stop A"), 3);
		Trip oneDay = saveTrip(owner, "One Day Trip", TripVisibility.PRIVATE, null);
		addStop(oneDay, savePlace("Stop B"), 1);
		entityManager.flush();

		TripSearchFilters filters = new TripSearchFilters(null, null, null, null, 3);
		Page<TripOwnerSummaryResponse> result = tripRepository.searchOwnedTrips(
				owner.getId(), null, filters, PageRequest.of(0, 20));

		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().get(0).title()).isEqualTo("Three Day Trip");
	}

	@Test
	void searchOwnedTrips_zeroStopTrip_matchesDurationZero_excludedFromNonZero() {
		User owner = saveUser("zerostopowner");
		saveTrip(owner, "No Stops Yet", TripVisibility.PRIVATE, null);
		entityManager.flush();

		TripSearchFilters zeroFilter = new TripSearchFilters(null, null, null, null, 0);
		Page<TripOwnerSummaryResponse> zeroResult = tripRepository.searchOwnedTrips(
				owner.getId(), null, zeroFilter, PageRequest.of(0, 20));
		assertThat(zeroResult.getContent()).hasSize(1);

		TripSearchFilters threeFilter = new TripSearchFilters(null, null, null, null, 3);
		Page<TripOwnerSummaryResponse> threeResult = tripRepository.searchOwnedTrips(
				owner.getId(), null, threeFilter, PageRequest.of(0, 20));
		assertThat(threeResult.getContent()).isEmpty();
	}

	@Test
	void searchOwnedTrips_multipleFilters_narrowToIntersection() {
		User owner = saveUser("intersectowner");
		Trip match = saveTrip(owner, "Matches Both", TripVisibility.PUBLIC, null);
		match.setStatus(TripStatus.ACTIVE);
		tripRepository.save(match);
		saveTrip(owner, "Wrong Status", TripVisibility.PUBLIC, null);
		entityManager.flush();

		TripSearchFilters filters = new TripSearchFilters(TripStatus.ACTIVE, TripVisibility.PUBLIC, null, null, null);
		Page<TripOwnerSummaryResponse> result = tripRepository.searchOwnedTrips(
				owner.getId(), null, filters, PageRequest.of(0, 20));

		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().get(0).title()).isEqualTo("Matches Both");
	}

	@Test
	void searchOwnedTrips_noSearchNoFilters_returnsWholeFirstPage() {
		User owner = saveUser("allowner");
		saveTrip(owner, "Trip One", TripVisibility.PRIVATE, null);
		saveTrip(owner, "Trip Two", TripVisibility.PRIVATE, null);
		entityManager.flush();

		Page<TripOwnerSummaryResponse> result = tripRepository.searchOwnedTrips(
				owner.getId(), null, TripSearchFilters.none(), PageRequest.of(0, 20));

		assertThat(result.getContent()).hasSize(2);
		assertThat(result.getTotalElements()).isEqualTo(2);
	}

	// ---------- searchOwnedTrips: ordering (D-adjacency / stability) ----------

	@Test
	void searchOwnedTrips_equalCreatedAt_ordersByIdDescendingStably() {
		User owner = saveUser("tieowner");
		Trip first = saveTrip(owner, "First Created", TripVisibility.PRIVATE, null);
		Trip second = saveTrip(owner, "Second Created", TripVisibility.PRIVATE, null);
		entityManager.flush();

		Instant tiedInstant = Instant.parse("2026-01-01T00:00:00Z");
		jdbcTemplate.update("UPDATE trips SET created_at = ? WHERE id IN (?, ?)",
				Timestamp.from(tiedInstant), first.getId(), second.getId());
		entityManager.clear();

		Page<TripOwnerSummaryResponse> result1 = tripRepository.searchOwnedTrips(
				owner.getId(), null, TripSearchFilters.none(), PageRequest.of(0, 20));
		Page<TripOwnerSummaryResponse> result2 = tripRepository.searchOwnedTrips(
				owner.getId(), null, TripSearchFilters.none(), PageRequest.of(0, 20));

		assertThat(result1.getContent()).extracting(TripOwnerSummaryResponse::id)
				.containsExactly(second.getId(), first.getId());
		assertThat(result2.getContent()).extracting(TripOwnerSummaryResponse::id)
				.containsExactly(second.getId(), first.getId());
	}
}
