package com.tripflow.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

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
class PlaceRepositoryIT {

	@Autowired
	private PlaceRepository placeRepository;

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private UserRepository userRepository;

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

	// ---------- SCRUM-216: multi-row dedup lookup ----------

	@Test
	void findFirstByNameAndLatitudeAndLongitudeOrderById_multipleMatches_returnsOneDeterministically() {
		// idx_places_name_lat_lng is non-unique by design — two places with the same
		// name+coordinates but different external_place_id both satisfy the partial
		// unique index on external_place_id and can coexist.
		Place first = new Place();
		first.setName("Shared Name");
		first.setLatitude(10.0);
		first.setLongitude(20.0);
		first.setExternalPlaceId("ext-a");
		Place firstSaved = placeRepository.save(first);

		Place second = new Place();
		second.setName("Shared Name");
		second.setLatitude(10.0);
		second.setLongitude(20.0);
		second.setExternalPlaceId("ext-b");
		placeRepository.save(second);

		// Must not throw IncorrectResultSizeDataAccessException despite two matching rows.
		Optional<Place> result = placeRepository.findFirstByNameAndLatitudeAndLongitudeOrderById(
				"Shared Name", 10.0, 20.0);

		assertThat(result).isPresent();
		assertThat(result.get().getId()).isEqualTo(firstSaved.getId());
	}

	// ---------- SCRUM-216: orphan cleanup ----------

	@Test
	void deleteOrphans_removesOnlyPlacesWithNoReferencingStop() {
		Place referenced = new Place();
		referenced.setName("Referenced");
		referenced.setLatitude(1.0);
		referenced.setLongitude(1.0);
		Place referencedSaved = placeRepository.save(referenced);

		Place orphan = new Place();
		orphan.setName("Orphan");
		orphan.setLatitude(2.0);
		orphan.setLongitude(2.0);
		Place orphanSaved = placeRepository.save(orphan);

		User user = new User();
		user.setUsername("orphantest");
		user.setEmail("orphantest@tripflow.com");
		user.setPasswordHash("hashed");
		User savedUser = userRepository.save(user);

		Trip trip = new Trip();
		trip.setUser(savedUser);
		trip.setTitle("Trip with a referenced place");

		Stop stop = new Stop();
		stop.setPlace(referencedSaved);
		stop.setStopOrder(0);
		stop.setTrip(trip);
		trip.getStops().add(stop);

		tripRepository.save(trip);

		int deleted = placeRepository.deleteOrphans();

		assertThat(deleted).isEqualTo(1);
		assertThat(placeRepository.findById(referencedSaved.getId())).isPresent();
		assertThat(placeRepository.findById(orphanSaved.getId())).isEmpty();
	}
}
