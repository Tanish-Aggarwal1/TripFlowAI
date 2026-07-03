package com.tripflow.backend.beans;

import com.tripflow.backend.beans.enums.StopStatus;

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

    @ManyToOne(fetch = FetchType.LAZY)
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