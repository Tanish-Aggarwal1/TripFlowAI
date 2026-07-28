package com.tripflow.backend.dto;

import java.util.List;

import jakarta.validation.constraints.Size;

public record ItineraryPreferencesRequest(
		@Size(max = 10, message = "at most 10 interests are allowed")
		List<@Size(max = 50, message = "each interest must be at most 50 characters") String> interests,
        @Size(max = 50, message = "must be at most 50 characters") String budget,
        @Size(max = 50, message = "must be at most 50 characters") String pace
        ) {}
