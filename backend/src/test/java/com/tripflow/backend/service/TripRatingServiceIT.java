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
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.exception.ResourceNotFoundException;
import com.tripflow.backend.repository.TripRatingRepository;
import com.tripflow.backend.repository.TripRepository;
import com.tripflow.backend.repository.UserRepository;
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Real-Postgres IT for {@link TripRatingService} (SOCIAL-07) — mirrors {@code
 * TripSaveServiceIT}'s Testcontainers harness. Asserts the upsert-not-toggle semantics that
 * make ratings structurally different from likes/saves (re-rate replaces rather than
 * ignores or duplicates), the 404-not-403 existence-hiding convention (SCRUM-274), and that
 * the database's own CHECK constraint refuses an out-of-range value independent of Bean
 * Validation.
 */
@DataJpaTest
@Import(JpaConfig.class)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TripRatingServiceIT {

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TripRatingRepository tripRatingRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private TripRatingService tripRatingService;

    @BeforeEach
    void setUp() {
        tripRatingService = new TripRatingService(tripRatingRepository, new TripOwnershipService(tripRepository));
    }

    private User saveUser(String suffix) {
        User user = new User();
        user.setUsername("ratingservice-" + suffix);
        user.setEmail("ratingservice-" + suffix + "@tripflow.com");
        user.setPasswordHash("hashedpassword123");
        return userRepository.save(user);
    }

    private Trip saveTrip(User owner, String title, TripVisibility visibility) {
        Trip trip = new Trip();
        trip.setUser(owner);
        trip.setTitle(title);
        trip.setVisibility(visibility);
        Trip saved = tripRepository.save(trip);
        entityManager.flush();
        entityManager.clear();
        return saved;
    }

    @Test
    void rateTrip_publicTrip_storesOneRowWithGivenRating() {
        User owner = saveUser("owner1");
        User rater = saveUser("rater1");
        Trip trip = saveTrip(owner, "Ottawa Weekend", TripVisibility.PUBLIC);

        tripRatingService.rateTrip(trip.getId(), rater.getId(), 4);

        assertThat(tripRatingRepository.count()).isEqualTo(1);
        assertThat(tripRatingRepository.findRatingByUserIdAndTripId(rater.getId(), trip.getId()))
                .contains(4);
    }

    @Test
    void rateTrip_calledTwiceBySameUser_replacesRatingWithoutDuplicateRow() {
        User owner = saveUser("owner2");
        User rater = saveUser("rater2");
        Trip trip = saveTrip(owner, "Repeatable Rating", TripVisibility.PUBLIC);

        tripRatingService.rateTrip(trip.getId(), rater.getId(), 4);
        tripRatingService.rateTrip(trip.getId(), rater.getId(), 2);

        assertThat(tripRatingRepository.count())
                .as("re-rating the same trip must not create a second row")
                .isEqualTo(1);
        assertThat(tripRatingRepository.findRatingByUserIdAndTripId(rater.getId(), trip.getId()))
                .as("the second rating value must replace the first")
                .contains(2);
    }

    @Test
    void rateTrip_byTwoDifferentUsers_producesTwoRows() {
        User owner = saveUser("owner3");
        User raterA = saveUser("ratera3");
        User raterB = saveUser("raterb3");
        Trip trip = saveTrip(owner, "Shared Trip", TripVisibility.PUBLIC);

        tripRatingService.rateTrip(trip.getId(), raterA.getId(), 5);
        tripRatingService.rateTrip(trip.getId(), raterB.getId(), 3);

        assertThat(tripRatingRepository.count()).isEqualTo(2);
    }

    @Test
    void rateTrip_foreignPrivateTrip_throwsResourceNotFound() {
        User owner = saveUser("owner4");
        User other = saveUser("other4");
        Trip trip = saveTrip(owner, "Private Trip", TripVisibility.PRIVATE);

        assertThatThrownBy(() -> tripRatingService.rateTrip(trip.getId(), other.getId(), 3))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(tripRatingRepository.count()).isEqualTo(0);
    }

    @Test
    void rateTrip_nonexistentTrip_throwsResourceNotFound() {
        User rater = saveUser("rater5");

        assertThatThrownBy(() -> tripRatingService.rateTrip(999_999L, rater.getId(), 3))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rateTrip_ownPrivateTrip_isAllowed() {
        User owner = saveUser("owner6");
        Trip trip = saveTrip(owner, "My Own Private Trip", TripVisibility.PRIVATE);

        tripRatingService.rateTrip(trip.getId(), owner.getId(), 5);

        assertThat(tripRatingRepository.count()).isEqualTo(1);
    }

    @Test
    void directInsert_outOfRangeRating_isRejectedByCheckConstraint() {
        User owner = saveUser("owner7");
        User rater = saveUser("rater7");
        Trip trip = saveTrip(owner, "Constraint Check Trip", TripVisibility.PUBLIC);

        assertThatThrownBy(() -> {
            entityManager.createNativeQuery(
                            "INSERT INTO trip_ratings (user_id, trip_id, rating, created_at, updated_at) "
                                    + "VALUES (:userId, :tripId, :rating, NOW(), NOW())")
                    .setParameter("userId", rater.getId())
                    .setParameter("tripId", trip.getId())
                    .setParameter("rating", 7)
                    .executeUpdate();
            entityManager.flush();
        }).isInstanceOf(Exception.class);
    }

    @Test
    void getSummary_threeRatings_returnsAverageAndCount() {
        User owner = saveUser("owner9");
        User raterA = saveUser("ratera9");
        User raterB = saveUser("raterb9");
        User raterC = saveUser("raterc9");
        Trip trip = saveTrip(owner, "Well Rated Trip", TripVisibility.PUBLIC);

        tripRatingService.rateTrip(trip.getId(), raterA.getId(), 5);
        tripRatingService.rateTrip(trip.getId(), raterB.getId(), 3);
        tripRatingService.rateTrip(trip.getId(), raterC.getId(), 4);

        var summary = tripRatingService.getSummary(trip.getId(), raterA.getId());

        assertThat(summary.averageRating()).isEqualTo(4.0);
        assertThat(summary.ratingCount()).isEqualTo(3);
        assertThat(summary.myRating()).isEqualTo(5);
    }

    @Test
    void getSummary_unratedTrip_returnsNullAverageAndZeroCount() {
        User owner = saveUser("owner10");
        User viewer = saveUser("viewer10");
        Trip trip = saveTrip(owner, "Unrated Trip", TripVisibility.PUBLIC);

        var summary = tripRatingService.getSummary(trip.getId(), viewer.getId());

        assertThat(summary.averageRating())
                .as("AVG over zero rows must be represented as null, not 0.0")
                .isNull();
        assertThat(summary.ratingCount()).isEqualTo(0);
        assertThat(summary.myRating()).isNull();
    }

    @Test
    void getSummary_callerHasNotRated_myRatingIsNull() {
        User owner = saveUser("owner11");
        User rater = saveUser("rater11");
        User viewer = saveUser("viewer11");
        Trip trip = saveTrip(owner, "Partially Rated Trip", TripVisibility.PUBLIC);

        tripRatingService.rateTrip(trip.getId(), rater.getId(), 4);

        var summary = tripRatingService.getSummary(trip.getId(), viewer.getId());

        assertThat(summary.myRating()).isNull();
        assertThat(summary.ratingCount()).isEqualTo(1);
    }

    @Test
    void getSummary_reRate_changesAverageWithoutChangingCount() {
        User owner = saveUser("owner12");
        User rater = saveUser("rater12");
        Trip trip = saveTrip(owner, "Re-rated Trip", TripVisibility.PUBLIC);

        tripRatingService.rateTrip(trip.getId(), rater.getId(), 2);
        var before = tripRatingService.getSummary(trip.getId(), rater.getId());
        assertThat(before.averageRating()).isEqualTo(2.0);
        assertThat(before.ratingCount()).isEqualTo(1);

        tripRatingService.rateTrip(trip.getId(), rater.getId(), 5);
        var after = tripRatingService.getSummary(trip.getId(), rater.getId());

        assertThat(after.averageRating()).isEqualTo(5.0);
        assertThat(after.ratingCount())
                .as("re-rating must change the average without changing the count")
                .isEqualTo(1);
    }

    @Test
    void getSummary_foreignPrivateTrip_throwsResourceNotFound() {
        User owner = saveUser("owner13");
        User other = saveUser("other13");
        Trip trip = saveTrip(owner, "Private Rating Summary Trip", TripVisibility.PRIVATE);

        assertThatThrownBy(() -> tripRatingService.getSummary(trip.getId(), other.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void directInsert_zeroRating_isRejectedByCheckConstraint() {
        User owner = saveUser("owner8");
        User rater = saveUser("rater8");
        Trip trip = saveTrip(owner, "Constraint Check Trip Two", TripVisibility.PUBLIC);

        assertThatThrownBy(() -> {
            entityManager.createNativeQuery(
                            "INSERT INTO trip_ratings (user_id, trip_id, rating, created_at, updated_at) "
                                    + "VALUES (:userId, :tripId, :rating, NOW(), NOW())")
                    .setParameter("userId", rater.getId())
                    .setParameter("tripId", trip.getId())
                    .setParameter("rating", 0)
                    .executeUpdate();
            entityManager.flush();
        }).isInstanceOf(Exception.class);
    }
}
