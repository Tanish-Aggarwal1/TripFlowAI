package com.tripflow.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tripflow.backend.beans.Place;

public interface PlaceRepository extends JpaRepository<Place, Long> {
	Optional<Place> findByNameAndLatitudeAndLongitude(String name, Double latitude, Double longitude);
}
