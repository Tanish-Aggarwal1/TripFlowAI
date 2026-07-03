package com.tripflow.backend.beans;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.tripflow.backend.beans.enums.TripStatus;
import com.tripflow.backend.beans.enums.TripVisibility;

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
@Table(name = "trips")
@Getter @Setter @NoArgsConstructor
public class Trip extends BaseEntity {


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;
    
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "TEXT[]")
    private List<String> tags = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TripVisibility visibility = TripVisibility.PRIVATE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TripStatus status = TripStatus.DRAFT;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "route_geometry", columnDefinition = "jsonb")
    private String routeGeometry;
}