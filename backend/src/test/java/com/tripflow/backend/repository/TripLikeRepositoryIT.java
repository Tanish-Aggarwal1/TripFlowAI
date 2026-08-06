package com.tripflow.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.tripflow.backend.config.JpaConfig;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** SCRUM-161: proves TripLikeRepository's insert/delete are idempotent at the DB level
 * (ON CONFLICT DO NOTHING / affected-row-count), independent of the service layer. */
@DataJpaTest
@Import(JpaConfig.class)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TripLikeRepositoryIT {

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TripLikeRepository tripLikeRepository;

	@PersistenceContext
	private EntityManager entityManager;

	private User saveUser(String suffix) {
		User user = new User();
		user.setUsername("likerepo-" + suffix);
		user.setEmail("likerepo-" + suffix + "@tripflow.com");
		user.setPasswordHash("hashedpassword123");
		return userRepository.save(user);
	}

	private Trip saveTrip(User owner, String title) {
		Trip trip = new Trip();
		trip.setUser(owner);
		trip.setTitle(title);
		return tripRepository.save(trip);
	}

	@Test
	void insertIfAbsent_firstCall_insertsAndReturnsOne() {
		User owner = saveUser("owner1");
		User liker = saveUser("liker1");
		Trip trip = saveTrip(owner, "Trip 1");
		entityManager.flush();

		int result = tripLikeRepository.insertIfAbsent(liker.getId(), trip.getId());

		assertThat(result).isEqualTo(1);
		assertThat(tripLikeRepository.count()).isEqualTo(1);
	}

	@Test
	void insertIfAbsent_secondCallSameUserAndTrip_isNoOpAndReturnsZero() {
		User owner = saveUser("owner2");
		User liker = saveUser("liker2");
		Trip trip = saveTrip(owner, "Trip 2");
		entityManager.flush();

		int first = tripLikeRepository.insertIfAbsent(liker.getId(), trip.getId());
		int second = tripLikeRepository.insertIfAbsent(liker.getId(), trip.getId());

		assertThat(first).isEqualTo(1);
		assertThat(second).isEqualTo(0);
		assertThat(tripLikeRepository.count()).isEqualTo(1);
	}

	@Test
	void deleteByUserIdAndTripId_existingLike_deletesAndReturnsOne() {
		User owner = saveUser("owner3");
		User liker = saveUser("liker3");
		Trip trip = saveTrip(owner, "Trip 3");
		entityManager.flush();
		tripLikeRepository.insertIfAbsent(liker.getId(), trip.getId());

		int deleted = tripLikeRepository.deleteByUserIdAndTripId(liker.getId(), trip.getId());

		assertThat(deleted).isEqualTo(1);
		assertThat(tripLikeRepository.count()).isEqualTo(0);
	}

	@Test
	void deleteByUserIdAndTripId_noExistingLike_isNoOpAndReturnsZero() {
		User owner = saveUser("owner4");
		User other = saveUser("other4");
		Trip trip = saveTrip(owner, "Trip 4");
		entityManager.flush();

		int deleted = tripLikeRepository.deleteByUserIdAndTripId(other.getId(), trip.getId());

		assertThat(deleted).isEqualTo(0);
	}
}
