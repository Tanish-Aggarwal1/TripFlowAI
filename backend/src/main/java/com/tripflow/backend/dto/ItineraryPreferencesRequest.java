package com.tripflow.backend.dto;

import java.util.List;

public record ItineraryPreferencesRequest(
		List<String> interests,
        String budget,
        String pace
        ) {}
