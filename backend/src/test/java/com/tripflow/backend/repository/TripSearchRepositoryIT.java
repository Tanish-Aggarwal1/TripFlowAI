package com.tripflow.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

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
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.TripSummaryResponse;
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** SCRUM-163: TripSearchRepositoryImpl's ILIKE title/tag matching, PUBLIC-only scoping,
 * and case-insensitivity, exercised against real Postgres. */
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
}
