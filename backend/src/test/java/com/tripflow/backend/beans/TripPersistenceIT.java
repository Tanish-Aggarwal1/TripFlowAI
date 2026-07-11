package com.tripflow.backend.beans;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.tripflow.backend.beans.enums.TripVisibility;
import com.tripflow.backend.config.JpaConfig;
import com.tripflow.backend.repository.TripRepository;
import com.tripflow.backend.repository.UserRepository;
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

@DataJpaTest
@Import(JpaConfig.class)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TripPersistenceIT {

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TestEntityManager entityManager;

	@Test
	void persistAndReloadTrip_retainsFieldsAndAuditTimestamps() {
		User owner = new User();
		owner.setUsername("tanish");
		owner.setEmail("tanish@example.com");
		owner.setPasswordHash("hashed");
		owner = userRepository.save(owner);

		Trip trip = new Trip();
		trip.setUser(owner);
		trip.setTitle("Weekend in Muskoka");
		trip.setDescription("Cottage trip");
		trip.setTags(List.of("cottage", "roadtrip"));
		trip.setVisibility(TripVisibility.PRIVATE);

		Trip saved = tripRepository.save(trip);
		entityManager.flush();
		entityManager.clear();

		Trip reloaded = tripRepository.findById(saved.getId()).orElseThrow();

		assertThat(reloaded.getTitle()).isEqualTo("Weekend in Muskoka");
		assertThat(reloaded.getTags()).containsExactly("cottage", "roadtrip");
		assertThat(reloaded.getUser().getUsername()).isEqualTo("tanish");
		assertThat(reloaded.getCreatedAt()).isNotNull();
		assertThat(reloaded.getUpdatedAt()).isNotNull();
	}
}
