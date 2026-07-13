package com.tripflow.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.tripflow.backend.domain.Place;
import com.tripflow.backend.domain.Stop;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

import jakarta.persistence.EntityManager;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class TripReadQueryCountIT {

    private static final int STOP_COUNT = 10;

    @Autowired private EntityManager entityManager;
    @Autowired private TripRepository tripRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlaceRepository placeRepository;

    @Test
    void findWithStopsById_loadsTripStopsAndPlaces_inOneQuery() {
        Long tripId = persistTripWithStops();

        Statistics stats = statistics();
        stats.clear();

        Trip loaded = tripRepository.findWithStopsById(tripId).orElseThrow();

        // Touch every lazy association the mapper would touch
        loaded.getStops().forEach(s -> s.getPlace().getName());

        assertThat(loaded.getStops()).hasSize(STOP_COUNT);
        assertThat(stats.getPrepareStatementCount())
                .as("trip + %d stops + places should load in a single query", STOP_COUNT)
                .isEqualTo(1);
    }

    private Long persistTripWithStops() {
        User owner = new User();
        owner.setUsername("nplusone-owner");
        owner.setEmail("nplusone@example.com");
        owner.setPasswordHash("hashed");
        userRepository.save(owner);

        Trip trip = new Trip();
        trip.setUser(owner);
        trip.setTitle("Query Count Trip");

        for (int i = 0; i < STOP_COUNT; i++) {
            Place place = new Place();
            place.setName("Place " + i);
            place.setLatitude(45.0 + i);
            place.setLongitude(-79.0 - i);
            placeRepository.save(place);

            Stop stop = new Stop();
            stop.setPlace(place);
            stop.setStopOrder(i);
            stop.setTrip(trip);
            trip.getStops().add(stop);
        }
        Long id = tripRepository.save(trip).getId();

        // Force fresh loads — nothing may come from the persistence context
        entityManager.flush();
        entityManager.clear();
        return id;
    }

    private Statistics statistics() {
        return entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
    }
}