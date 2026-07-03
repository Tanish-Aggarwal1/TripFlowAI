package com.tripflow.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tripflow.backend.beans.Stop;

public interface StopRepository extends JpaRepository<Stop, Long> {
    List<Stop> findByTripIdOrderByStopOrderAsc(Long tripId);
}
