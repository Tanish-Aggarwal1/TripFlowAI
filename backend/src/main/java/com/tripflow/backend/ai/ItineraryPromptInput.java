package com.tripflow.backend.ai;

import java.util.List;

public record ItineraryPromptInput(
		List<String> interests,
        String budget,
        String pace,
        List<String> destinations
        ) {

}
