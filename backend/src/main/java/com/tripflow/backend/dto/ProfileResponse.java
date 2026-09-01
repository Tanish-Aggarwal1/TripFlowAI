package com.tripflow.backend.dto;

import java.time.Instant;
import java.util.List;

/**
 * SOCIAL-05 (D-07): a signed-in user's own profile — username, join date, stored interests.
 * Named {@code joinedAt} rather than {@code createdAt} so the API reads as a profile
 * contract, not a leak of the {@code BaseEntity} audit-column name.
 */
public record ProfileResponse(Long id, String username, Instant joinedAt, List<String> interests) {
}
