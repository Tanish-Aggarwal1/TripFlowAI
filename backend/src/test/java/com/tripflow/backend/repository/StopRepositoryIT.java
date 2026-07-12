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
import com.tripflow.backend.domain.Place;
import com.tripflow.backend.domain.Stop;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

@DataJpaTest
@Import(JpaConfig.class)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StopRepositoryIT {

	@Autowired
	private StopRepository stopRepository;

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PlaceRepository placeRepository;

	@Test
	void saveAndFindById() {
		User user = new User();
		user.setUsername("stopowner");
		user.setEmail("stopowner@tripflow.com");
		user.setPasswordHash("hashedpassword123");
		User savedUser = userRepository.save(user);

		Trip trip = new Trip();
		trip.setUser(savedUser);
		trip.setTitle("Trip With Stops");
		Trip savedTrip = tripRepository.save(trip);

		Place place = new Place();
		place.setName("Niagara Falls");
		place.setLatitude(43.0962);
		place.setLongitude(-79.0377);
		Place savedPlace = placeRepository.save(place);

		Stop stop = new Stop();
		stop.setTrip(savedTrip);
		stop.setPlace(savedPlace);
		stop.setStopOrder(1);

		Stop saved = stopRepository.save(stop);

		assertThat(saved.getId()).isNotNull();
		assertThat(stopRepository.findById(saved.getId())).isPresent();
		assertThat(stopRepository.findById(saved.getId()).get().getPlace().getName())
				.isEqualTo("Niagara Falls");
	}
}
