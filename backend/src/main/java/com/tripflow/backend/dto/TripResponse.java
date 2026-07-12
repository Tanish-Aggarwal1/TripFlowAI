package com.tripflow.backend.dto;

import java.time.Instant;
import java.util.List;

import com.tripflow.backend.domain.enums.TripStatus;
import com.tripflow.backend.domain.enums.TripVisibility;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class TripResponse {
	private Long id;
    private String title;
    private String description;
    private List<String> tags;
    private TripVisibility visibility;
    private TripStatus status;
    private Long ownerId;
    private List<StopResponse> stops;
    private Instant createdAt;
    private Instant updatedAt;
}
