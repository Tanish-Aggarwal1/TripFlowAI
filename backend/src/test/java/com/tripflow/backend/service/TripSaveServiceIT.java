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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import com.tripflow.backend.config.JpaConfig;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.exception.ResourceNotFoundException;
import com.tripflow.backend.repository.SavedTripRepository;
import com.tripflow.backend.repository.TripRepository;
import com.tripflow.backend.repository.UserRepository;
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Real-Postgres IT for {@link TripSaveService} (SOCIAL-04) — mirrors
 * {@code TripCloneServiceIT}'s Testcontainers harness. Asserts the idempotency of
 * save/unsave at the database layer and the 404-not-403 existence-hiding convention
 * (SCRUM-274) for a foreign PRIVATE trip.
 */
@DataJpaTest
@Import(JpaConfig.class)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TripSaveServiceIT {

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SavedTripRepository savedTripRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private TripSaveService tripSaveService;

    @BeforeEach
    void setUp() {
        tripSaveService = new TripSaveService(savedTripRepository, new TripOwnershipService(tripRepository));
    }

    private User saveUser(String suffix) {
        User user = new User();
        user.setUsername("saveservice-" + suffix);
        user.setEmail("saveservice-" + suffix + "@tripflow.com");
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
    void saveTrip_publicTrip_insertsOneRow() {
        User owner = saveUser("owner1");
        User saver = saveUser("saver1");
        Trip trip = saveTrip(owner, "Ottawa Weekend", TripVisibility.PUBLIC);

        tripSaveService.saveTrip(trip.getId(), saver.getId());

        assertThat(savedTripRepository.count()).isEqualTo(1);
    }

    @Test
    void saveTrip_calledTwice_doesNotInsertDuplicateRow() {
        User owner = saveUser("owner2");
        User saver = saveUser("saver2");
        Trip trip = saveTrip(owner, "Repeatable Save", TripVisibility.PUBLIC);

        tripSaveService.saveTrip(trip.getId(), saver.getId());
        tripSaveService.saveTrip(trip.getId(), saver.getId());

        assertThat(savedTripRepository.count())
                .as("saving the same trip twice must not create a second row")
                .isEqualTo(1);
    }

    @Test
    void unsaveTrip_removesRow() {
        User owner = saveUser("owner3");
        User saver = saveUser("saver3");
        Trip trip = saveTrip(owner, "Unsave Me", TripVisibility.PUBLIC);

        tripSaveService.saveTrip(trip.getId(), saver.getId());
        assertThat(savedTripRepository.count()).isEqualTo(1);

        tripSaveService.unsaveTrip(trip.getId(), saver.getId());
        assertThat(savedTripRepository.count()).isEqualTo(0);
    }

    @Test
    void unsaveTrip_calledOnAlreadyUnsavedTrip_doesNotThrow() {
        User owner = saveUser("owner4");
        User saver = saveUser("saver4");
        Trip trip = saveTrip(owner, "Never Saved", TripVisibility.PUBLIC);

        tripSaveService.unsaveTrip(trip.getId(), saver.getId());

        assertThat(savedTripRepository.count()).isEqualTo(0);
    }

    @Test
    void saveTrip_foreignPrivateTrip_throwsResourceNotFound() {
        User owner = saveUser("owner5");
        User other = saveUser("other5");
        Trip trip = saveTrip(owner, "Private Trip", TripVisibility.PRIVATE);

        assertThatThrownBy(() -> tripSaveService.saveTrip(trip.getId(), other.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(savedTripRepository.count()).isEqualTo(0);
    }

    @Test
    void unsaveTrip_foreignPrivateTrip_throwsResourceNotFound() {
        User owner = saveUser("owner6");
        User other = saveUser("other6");
        Trip trip = saveTrip(owner, "Private Trip Two", TripVisibility.PRIVATE);

        assertThatThrownBy(() -> tripSaveService.unsaveTrip(trip.getId(), other.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void saveTrip_nonexistentTrip_throwsResourceNotFound() {
        User saver = saveUser("saver7");

        assertThatThrownBy(() -> tripSaveService.saveTrip(999_999L, saver.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void saveTrip_ownPrivateTrip_isAllowed() {
        User owner = saveUser("owner8");
        Trip trip = saveTrip(owner, "My Own Private Trip", TripVisibility.PRIVATE);

        tripSaveService.saveTrip(trip.getId(), owner.getId());

        assertThat(savedTripRepository.count()).isEqualTo(1);
    }

    @Test
    void listSaved_tripSavedByUserA_isAbsentFromUserBsList() {
        User owner = saveUser("owner9");
        User userA = saveUser("usera9");
        User userB = saveUser("userb9");
        Trip trip = saveTrip(owner, "Shared Public Trip", TripVisibility.PUBLIC);

        tripSaveService.saveTrip(trip.getId(), userA.getId());

        Pageable pageable = PageRequest.of(0, 20);
        assertThat(tripSaveService.listSaved(userA.getId(), pageable).getContent())
                .as("user A saved this trip")
                .hasSize(1);
        assertThat(tripSaveService.listSaved(userB.getId(), pageable).getContent())
                .as("user B never saved anything and must not see user A's save")
                .isEmpty();
    }
}
