package com.tripflow.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tripflow.backend.domain.Stop;

public interface StopRepository extends JpaRepository<Stop, Long> {
}