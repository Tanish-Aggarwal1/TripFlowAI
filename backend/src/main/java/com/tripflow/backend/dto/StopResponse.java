package com.tripflow.backend.dto;

import com.tripflow.backend.domain.enums.StopStatus;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class StopResponse {
	private Long id;
    private String name;
    private Double latitude;
    private Double longitude;
    private String address;
    private Integer stopOrder;
    private StopStatus status;
    private String notes;
}
