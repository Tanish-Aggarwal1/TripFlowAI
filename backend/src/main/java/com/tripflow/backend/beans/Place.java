package com.tripflow.backend.beans;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "places")
@Getter @Setter @NoArgsConstructor
public class Place extends BaseEntity {
	@Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(length = 300)
    private String address;
    
    @Column(name = "external_place_id", length = 150)
    private String externalPlaceId;
}
