package com.tripflow.backend.dto;

/**
 * Shared VISITED-only completion arithmetic for {@link TripOwnerSummaryResponse} and
 * {@link TripResponse}. Extracted for the same reason as {@code config/SecretMask} — one
 * rule, not two copy-pasted bodies.
 *
 * <p>SKIPPED stops stay in the denominator but never the numerator (D-06). A zero-stop
 * trip returns exactly {@code 0.0}, never {@code null} and never a divide-by-zero (D-07).
 * The return value is a 0.0-1.0 fraction, not a 0-100 percentage — both DTOs and the
 * frontend depend on this convention.
 */
public final class TripCompletion {

    private TripCompletion() {
    }

    public static double percentage(long visitedStopCount, long stopCount) {
        return stopCount == 0 ? 0.0 : (double) visitedStopCount / stopCount;
    }
}
