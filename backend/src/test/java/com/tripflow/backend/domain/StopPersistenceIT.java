package com.tripflow.backend.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.tripflow.backend.config.JpaConfig;
import com.tripflow.backend.domain.Place;
import com.tripflow.backend.domain.Stop;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.repository.PlaceRepository;
import com.tripflow.backend.repository.TripRepository;
import com.tripflow.backend.repository.UserRepository;
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

@DataJpaTest
@Import(JpaConfig.class)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StopPersistenceIT {

	@Autowired private TripRepository tripRepository;
	@Autowired private UserRepository userRepository;
	@Autowired private PlaceRepository placeRepository;
	@Autowired private TestEntityManager entityManager;

	private Trip createTripWithOwner(String suffix) {
		User owner = new User();
		owner.setUsername("tanish-stop-" + suffix);
		owner.setEmail("tanish-stop-" + suffix + "@example.com");
		owner.setPasswordHash("hashed");
		owner = userRepository.save(owner);

		Trip trip = new Trip();
		trip.setUser(owner);
		trip.setTitle("Muskoka Weekend " + suffix);
		return trip;
	}

	private Place createPlace(String name, double lat, double lng) {
		Place place = new Place();
		place.setName(name);
		place.setLatitude(lat);
		place.setLongitude(lng);
		return placeRepository.save(place);
	}

	@Test
	void cascadePersist_reloadsStopsInOrder() {
		Trip trip = createTripWithOwner("order");

		Place cottage = createPlace("Cottage", 45.0, -79.9);
		Place gasStation = createPlace("Gas Station", 44.5, -79.6);

		Stop stop1 = new Stop();
		stop1.setPlace(gasStation);
		stop1.setStopOrder(1);

		Stop stop0 = new Stop();
		stop0.setPlace(cottage);
		stop0.setStopOrder(0);

		trip.getStops().add(stop1);
		trip.getStops().add(stop0);
		stop1.setTrip(trip);
		stop0.setTrip(trip);

		Trip saved = tripRepository.save(trip);
		entityManager.flush();
		entityManager.clear();

		Trip reloaded = tripRepository.findById(saved.getId()).orElseThrow();

		assertThat(reloaded.getStops()).hasSize(2);
		assertThat(reloaded.getStops().get(0).getPlace().getName()).isEqualTo("Cottage");
		assertThat(reloaded.getStops().get(1).getPlace().getName()).isEqualTo("Gas Station");
	}

	@Test
	void removingStopFromList_deletesOrphan_butPlaceSurvives() {
		Trip trip = createTripWithOwner("orphan");
		Place place = createPlace("Rest Stop", 44.0, -79.0);

		Stop stop = new Stop();
		stop.setPlace(place);
		stop.setStopOrder(0);
		trip.getStops().add(stop);
		stop.setTrip(trip);

		Trip saved = tripRepository.save(trip);
		entityManager.flush();
		entityManager.clear();

		Trip reloaded = tripRepository.findById(saved.getId()).orElseThrow();
		reloaded.getStops().clear();
		tripRepository.save(reloaded);
		entityManager.flush();
		entityManager.clear();

		Trip afterRemoval = tripRepository.findById(saved.getId()).orElseThrow();
		assertThat(afterRemoval.getStops()).isEmpty();

		assertThat(placeRepository.findById(place.getId())).isPresent();
	}

	// ---------- SCRUM-218: (trip_id, stop_order) uniqueness ----------

	@Test
	void duplicateStopOrderWithinSameTrip_violatesUniqueConstraint() {
		Trip trip = createTripWithOwner("dup-order");
		Place place = createPlace("Rest Stop", 44.0, -79.0);

		Stop stop0 = new Stop();
		stop0.setPlace(place);
		stop0.setStopOrder(0);
		trip.getStops().add(stop0);
		stop0.setTrip(trip);

		Trip saved = tripRepository.save(trip);
		entityManager.flush();

		Stop duplicateOrderStop = new Stop();
		duplicateOrderStop.setPlace(place);
		duplicateOrderStop.setStopOrder(0); // same trip, same order as stop0
		duplicateOrderStop.setTrip(saved);
		entityManager.persist(duplicateOrderStop);

		// uq_stops_trip_id_stop_order is DEFERRABLE INITIALLY DEFERRED (V6) so the three
		// app-code renumbering paths can transiently collide within one transaction without
		// tripping the constraint mid-flush. Switching it to IMMEDIATE re-validates every
		// pending deferred constraint right here — Postgres raises the violation as part of
		// that statement itself, proving the constraint genuinely rejects a duplicate. (The
		// default @DataJpaTest rollback never reaches a real COMMIT, so there'd be no other
		// way to observe a deferred check firing.) This bypasses Spring Data's repository
		// proxy, so the raw Hibernate exception surfaces here, not Spring's translated
		// DataIntegrityViolationException.
		assertThatThrownBy(() -> entityManager.getEntityManager()
				.createNativeQuery("SET CONSTRAINTS uq_stops_trip_id_stop_order IMMEDIATE")
				.executeUpdate())
				.isInstanceOf(ConstraintViolationException.class);
	}

	@Test
	void samePlace_reusedAcrossTwoTrips() {
		Place eiffelTower = createPlace("Eiffel Tower", 48.8584, 2.2945);

		Trip tripA = createTripWithOwner("a");
		Stop stopA = new Stop();
		stopA.setPlace(eiffelTower);
		stopA.setStopOrder(0);
		tripA.getStops().add(stopA);
		stopA.setTrip(tripA);
		tripRepository.save(tripA);

		Trip tripB = createTripWithOwner("b");
		Stop stopB = new Stop();
		stopB.setPlace(eiffelTower);
		stopB.setStopOrder(0);
		stopB.setNotes("Visit at sunset");
		tripB.getStops().add(stopB);
		stopB.setTrip(tripB);
		tripRepository.save(tripB);

		entityManager.flush();
		entityManager.clear();

		assertThat(placeRepository.findAll())
				.filteredOn(p -> p.getName().equals("Eiffel Tower"))
				.hasSize(1);
	}
}
