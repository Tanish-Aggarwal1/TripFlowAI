package com.tripflow.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.tripflow.backend.domain.StopPhoto;
import com.tripflow.backend.domain.Trip;
import com.tripflow.backend.domain.User;
import com.tripflow.backend.testsupport.PostgresTestcontainersConfiguration;

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
    @Autowired private TestEntityManager entityManager;

    private Long createStopId(String suffix) {
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
        Stop savedStop = stopRepository.save(stop);

        // Flush + clear so every caller gets a clean, reloaded, unambiguously
        // persistent Stop reference — matches the pattern already used in
        // StopPersistenceIT / TripRepositoryIT for exactly this reason.
        entityManager.flush();
        entityManager.clear();

        return savedStop.getId();
    }

    private Stop reloadStop(Long stopId) {
        return stopRepository.findById(stopId).orElseThrow();
    }

    @Test
    void saveAndFindById_persistsAllFields() {
        Stop stop = reloadStop(createStopId("save"));

        StopPhoto photo = new StopPhoto();
        photo.setStop(stop);
        photo.setUrl("https://res.cloudinary.com/demo-cloud/image/upload/v1/trip/sample.jpg");
        photo.setCloudinaryPublicId("trip/sample");
        photo.setCaption("Sunset at the lookout");

        StopPhoto saved = stopPhotoRepository.save(photo);
        entityManager.flush();
        entityManager.clear();

        StopPhoto found = stopPhotoRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getUrl()).isEqualTo(photo.getUrl());
        assertThat(found.getCloudinaryPublicId()).isEqualTo("trip/sample");
        assertThat(found.getCaption()).isEqualTo("Sunset at the lookout");
        assertThat(found.getStop().getId()).isEqualTo(stop.getId());
    }

    @Test
    void findByStopId_returnsOnlyPhotosForThatStop() {
        Long stopAId = createStopId("a");
        Long stopBId = createStopId("b");
        Stop stopA = reloadStop(stopAId);
        Stop stopB = reloadStop(stopBId);

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

        entityManager.flush();
        entityManager.clear();

        assertThat(stopPhotoRepository.findByStopId(stopAId)).hasSize(2);
        assertThat(stopPhotoRepository.findByStopId(stopBId)).hasSize(1);
    }

    @Test
    void findByStopId_noPhotos_returnsEmptyList() {
        Long stopId = createStopId("empty");

        assertThat(stopPhotoRepository.findByStopId(stopId)).isEmpty();
    }

    @Test
    void deletingStop_cascadesToItsPhotos() {
        Long stopId = createStopId("cascade");
        Stop stop = reloadStop(stopId);

        StopPhoto photo = new StopPhoto();
        photo.setStop(stop);
        photo.setUrl("https://res.cloudinary.com/demo/image/upload/cascade.jpg");
        StopPhoto savedPhoto = stopPhotoRepository.save(photo);
        entityManager.flush();
        entityManager.clear();

        Long savedPhotoId = savedPhoto.getId();

        // Reload the stop fresh after the clear so the delete operates on a
        // clean, unambiguous managed instance with no pending sibling actions
        // in the same persistence-context flush.
        Stop stopToDelete = stopRepository.findById(stopId).orElseThrow();
        stopRepository.delete(stopToDelete);
        entityManager.flush();
        entityManager.clear();

        assertThat(stopPhotoRepository.findById(savedPhotoId)).isEmpty();
        assertThat(stopPhotoRepository.findByStopId(stopId)).isEmpty();
    }
}