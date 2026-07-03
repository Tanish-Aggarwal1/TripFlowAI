package com.tripflow.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tripflow.model.Trip;

public interface TripRepository extends JpaRepository<Trip, Long> {
    List<Trip> findByUserId(Long userId);
}