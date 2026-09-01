package com.tripflow.backend.dto;

import java.time.Instant;
import java.util.List;

/**
 * Full-screen feed card shape for {@code GET /api/discovery/feed} (SOCIAL-01).
 *
 * <p>Deliberately NOT {@link TripSummaryResponse}: that DTO is a card-projection
 * built for a paginated list view and carries no {@code stops}, no owner info,
 * and no {@code tags}. D-02's fixed card header/footer needs owner and
 * description on every card, and D-03's text-card fallback needs per-stop
 * name/notes text — none of which the summary projection carries.
 */
public record FeedTripResponse(
        Long id,
        String title,
        String description,
        List<String> tags,
        String ownerUsername,
        long likeCount,
        Instant createdAt,
        List<FeedStop> stops
) {
    /**
     * One stop's feed-card content. {@code photoUrls} is always a non-null list —
     * empty when the stop has zero photos, which is the data precondition for
     * D-03's text-based fallback card (rendered from {@code name}/{@code notes}
     * instead of an image).
     */
    public record FeedStop(
            Long id,
            String name,
            String address,
            Integer stopOrder,
            String notes,
            List<String> photoUrls
    ) {}
}
