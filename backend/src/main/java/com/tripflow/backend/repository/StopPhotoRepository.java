package com.tripflow.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tripflow.backend.domain.StopPhoto;

public interface StopPhotoRepository extends JpaRepository<StopPhoto, Long> {
    List<StopPhoto> findByStopId(Long stopId);
}