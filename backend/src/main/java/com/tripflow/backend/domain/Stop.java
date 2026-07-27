package com.tripflow.backend.domain;

import com.tripflow.backend.domain.enums.StopStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stops")
@Getter @Setter @NoArgsConstructor
public class Stop extends BaseEntity {


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    // cascade = MERGE (SCRUM-210/226): without it, re-attaching a detached Trip via
    // tripRepository.save(...) (a JPA merge, since RouteOptimizationService's load and
    // persist steps are now separate transactions) does NOT carry over this already-loaded
    // Place onto the newly-managed graph — merge just relinks it as a fresh, uninitialized
    // proxy by id, which then throws LazyInitializationException the moment a mapper reads
    // it after the persist transaction has closed.
    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.MERGE)
    @JoinColumn(name = "place_id", nullable = false)
    private Place place;

    @Column(name = "stop_order", nullable = false)
    private Integer stopOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StopStatus status = StopStatus.PLANNED;

    @Column(columnDefinition = "TEXT")
    private String notes;
}