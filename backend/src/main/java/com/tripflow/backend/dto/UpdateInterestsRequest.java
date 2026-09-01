package com.tripflow.backend.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * SOCIAL-05 (D-07): replaces the caller's stored interests wholesale. Limits mirror the
 * existing {@code Trip.tags} policy documented in {@code docs/api-contracts.md} exactly —
 * max 20 elements, each max 50 characters — rather than inventing new numbers.
 */
public record UpdateInterestsRequest(
        @NotNull @Size(max = 20) List<@Size(max = 50) String> interests
) {
}
