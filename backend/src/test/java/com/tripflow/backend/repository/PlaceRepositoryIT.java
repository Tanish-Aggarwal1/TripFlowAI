package com.tripflow.backend.repository;

import com.tripflow.backend.beans.Place;
import com.tripflow.backend.config.JpaConfig;
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaConfig.class)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PlaceRepositoryIT {

	@Autowired
	private PlaceRepository placeRepository;

	@Test
	void saveAndFindById() {
		Place place = new Place();
		place.setName("Niagara Falls");
		place.setLatitude(43.0962);
		place.setLongitude(-79.0377);
		place.setAddress("Niagara Falls, ON");

		Place saved = placeRepository.save(place);

		assertThat(saved.getId()).isNotNull();
		assertThat(placeRepository.findById(saved.getId())).isPresent();
	}
}
