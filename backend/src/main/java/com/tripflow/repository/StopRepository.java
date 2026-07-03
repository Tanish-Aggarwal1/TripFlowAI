package com.tripflow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tripflow.beans.Stop;

public interface StopRepository extends JpaRepository<Stop, Long> {
    List<Stop> findByTripIdOrderByStopOrderAsc(Long tripId);
}
