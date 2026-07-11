package com.tripflow.backend.beans;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.tripflow.backend.config.JpaConfig;
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

import jakarta.persistence.EntityManager;

@DataJpaTest
@Import(JpaConfig.class)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BaseEntityProxyIT {

	@Autowired
	private EntityManager entityManager;

	@Test
	void hibernateProxyEqualsRealEntityWithSameId() {
		Place saved = new Place();
		saved.setName("Proxy Test Place");
		saved.setLatitude(45.0);
		saved.setLongitude(-79.0);
		entityManager.persist(saved);
		entityManager.flush();
		entityManager.clear();

		Place proxy = entityManager.getReference(Place.class, saved.getId());

		assertThat(proxy).isEqualTo(saved);
		assertThat(proxy).hasSameHashCodeAs(saved);
	}
}
