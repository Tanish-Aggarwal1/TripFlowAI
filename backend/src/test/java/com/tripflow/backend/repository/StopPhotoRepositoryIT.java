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
import com.tripflow.backend.domain.StopPhoto;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

/**
 * Also serves as the primary check for the SCRUM-66b acceptance criterion
 * "ddl-auto=validate passes after this migration lands" — this test class
 * boots a real Hibernate SessionFactory against a Testcontainers Postgres
 * with V5 applied. If the entity and migration disagree, context startup
 * fails before any {@code @Test} method runs.
 */
@DataJpaTest
@Import(JpaConfig.class)
@ImportTestcontainers(PostgresTestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StopPhotoRepositoryIT {

    @Autowired private StopPhotoRepository stopPhotoRepository;
    @Autowired private StopRepository stopRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlaceRepository placeRepository;

    private Stop createStop(String suffix) {
        User user = new User();
        user.setUsername("photo-owner-" + suffix);
        user.setEmail("photo-owner-" + suffix + "@tripflow.com");
        user.setPasswordHash("hashedpassword123");
        User savedUser = userRepository.save(user);

        Trip trip = new Trip();
        trip.setUser(savedUser);
        trip.setTitle("Trip With Photos " + suffix);
        Trip savedTrip = tripRepository.save(trip);

        Place place = new Place();
        place.setName("Scenic Lookout " + suffix);
        place.setLatitude(45.0);
        place.setLongitude(-79.0);
        Place savedPlace = placeRepository.save(place);

        Stop stop = new Stop();
        stop.setTrip(savedTrip);
        stop.setPlace(savedPlace);
        stop.setStopOrder(0);
        return stopRepository.save(stop);
    }

    @Test
    void saveAndFindById_persistsAllFields() {
        Stop stop = createStop("save");

        StopPhoto photo = new StopPhoto();
        photo.setStop(stop);
        photo.setUrl("https://res.cloudinary.com/demo-cloud/image/upload/v1/trip/sample.jpg");
        photo.setCloudinaryPublicId("trip/sample");
        photo.setCaption("Sunset at the lookout");

        StopPhoto saved = stopPhotoRepository.save(photo);

        assertThat(saved.getId()).isNotNull();
        StopPhoto found = stopPhotoRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getUrl()).isEqualTo(photo.getUrl());
        assertThat(found.getCloudinaryPublicId()).isEqualTo("trip/sample");
        assertThat(found.getCaption()).isEqualTo("Sunset at the lookout");
        assertThat(found.getStop().getId()).isEqualTo(stop.getId());
    }

    @Test
    void findByStopId_returnsOnlyPhotosForThatStop() {
        Stop stopA = createStop("a");
        Stop stopB = createStop("b");

        StopPhoto photo1 = new StopPhoto();
        photo1.setStop(stopA);
        photo1.setUrl("https://res.cloudinary.com/demo/image/upload/a1.jpg");
        stopPhotoRepository.save(photo1);

        StopPhoto photo2 = new StopPhoto();
        photo2.setStop(stopA);
        photo2.setUrl("https://res.cloudinary.com/demo/image/upload/a2.jpg");
        stopPhotoRepository.save(photo2);

        StopPhoto photo3 = new StopPhoto();
        photo3.setStop(stopB);
        photo3.setUrl("https://res.cloudinary.com/demo/image/upload/b1.jpg");
        stopPhotoRepository.save(photo3);

        assertThat(stopPhotoRepository.findByStopId(stopA.getId())).hasSize(2);
        assertThat(stopPhotoRepository.findByStopId(stopB.getId())).hasSize(1);
    }

    @Test
    void findByStopId_noPhotos_returnsEmptyList() {
        Stop stop = createStop("empty");

        assertThat(stopPhotoRepository.findByStopId(stop.getId())).isEmpty();
    }

    @Test
    void deletingStop_cascadesToItsPhotos() {
        Stop stop = createStop("cascade");

        StopPhoto photo = new StopPhoto();
        photo.setStop(stop);
        photo.setUrl("https://res.cloudinary.com/demo/image/upload/cascade.jpg");
        StopPhoto savedPhoto = stopPhotoRepository.save(photo);

        stopRepository.delete(stop);
        stopRepository.flush();

        assertThat(stopPhotoRepository.findById(savedPhoto.getId())).isEmpty();
        assertThat(stopPhotoRepository.findByStopId(stop.getId())).isEmpty();
    }
}