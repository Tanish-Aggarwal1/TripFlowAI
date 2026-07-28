package com.tripflow.backend.schedule;

import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Component;

import com.tripflow.backend.domain.Stop;

import lombok.RequiredArgsConstructor;

/**
 * Heuristic day/time assignment (SCRUM-244a) — no Gemini involvement, just a greedy
 * walk over the already route-optimized stop order.
 *
 * <p>Each stop is assumed to take {@link RouteScheduleProperties#defaultVisitDuration()}
 * to visit; travel time between consecutive stops comes from the caller (the ORS
 * directions call already made during route optimization). Cumulative time rolls over
 * to the next {@code dayNumber} once it would exceed the configured day window
 * ({@link RouteScheduleProperties#dayStartTime()}..{@link RouteScheduleProperties#dayEndTime()}).
 */
@Component
@RequiredArgsConstructor
public class ItineraryScheduler {

	private final RouteScheduleProperties properties;

	/**
	 * Mutates each stop's dayNumber/plannedTime in place, in the order given.
	 *
	 * @param orderedStops        stops in route-optimized visiting order
	 * @param legDurationsSeconds travel time (seconds) from stop i to stop i+1;
	 *                            index i is missing or the list is shorter than
	 *                            {@code orderedStops.size() - 1} is treated as zero
	 *                            travel time for that leg, rather than failing.
	 */
	public void schedule(List<Stop> orderedStops, List<Double> legDurationsSeconds) {
		if (orderedStops.isEmpty()) {
			return;
		}

		long windowSeconds = Duration.between(properties.dayStartTime(), properties.dayEndTime()).getSeconds();
		long visitSeconds = properties.defaultVisitDuration().getSeconds();

		int dayNumber = 1;
		long elapsedInDay = 0;

		for (int i = 0; i < orderedStops.size(); i++) {
			// Only roll to a fresh day if we've already made progress today — an
			// oversized single visit (misconfiguration) must not spin forever.
			if (elapsedInDay > 0 && elapsedInDay + visitSeconds > windowSeconds) {
				dayNumber++;
				elapsedInDay = 0;
			}

			Stop stop = orderedStops.get(i);
			stop.setDayNumber(dayNumber);
			stop.setPlannedTime(properties.dayStartTime().plusSeconds(elapsedInDay));
			elapsedInDay += visitSeconds;

			boolean hasNextStop = i < orderedStops.size() - 1;
			if (hasNextStop) {
				long travelSeconds = legDurationsSeconds != null && i < legDurationsSeconds.size()
						&& legDurationsSeconds.get(i) != null
						? legDurationsSeconds.get(i).longValue()
						: 0L;

				if (elapsedInDay > 0 && elapsedInDay + travelSeconds > windowSeconds) {
					dayNumber++;
					elapsedInDay = 0;
				} else {
					elapsedInDay += travelSeconds;
				}
			}
		}
	}
}
