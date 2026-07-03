package com.tripflow.backend.dto;

import java.util.List;

import com.tripflow.backend.beans.enums.TripVisibility;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CreateTripRequest {
	@NotBlank
    @Size(max = 150)
    private String title;

    private String description;

    private List<String> tags;

    @NotNull
    private TripVisibility visibility;

    @NotEmpty
    @Valid
    private List<CreateStopRequest> stops;
}
