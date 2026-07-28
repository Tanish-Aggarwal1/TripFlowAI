package com.tripflow.backend.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.tripflow.backend.domain.Stop;

class ItinerarySchedulerTest {

	private static final LocalTime DAY_START = LocalTime.of(9, 0);
	private static final LocalTime DAY_END = LocalTime.of(21, 0);

	private final ItineraryScheduler scheduler = new ItineraryScheduler(
			new RouteScheduleProperties(DAY_START, DAY_END, Duration.ofHours(1)));

	private List<Stop> stops(int count) {
		List<Stop> stops = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			stops.add(new Stop());
		}
		return stops;
	}

	@Test
	void schedule_emptyList_doesNothing() {
		scheduler.schedule(List.of(), List.of());
		// No exception — nothing to assert on an empty list.
	}

	@Test
	void schedule_singleStop_assignsDayOneAtDayStart() {
		List<Stop> stops = stops(1);

		scheduler.schedule(stops, List.of());

		assertThat(stops.get(0).getDayNumber()).isEqualTo(1);
		assertThat(stops.get(0).getPlannedTime()).isEqualTo(DAY_START);
	}

	@Test
	void schedule_multipleStopsWithinWindow_assignsSameDayIncreasingTimes() {
		List<Stop> stops = stops(3);
		// 30 min travel between each leg; 1h visit duration per stop.
		List<Double> legDurations = List.of(1800.0, 1800.0);

		scheduler.schedule(stops, legDurations);

		assertThat(stops.get(0).getDayNumber()).isEqualTo(1);
		assertThat(stops.get(0).getPlannedTime()).isEqualTo(LocalTime.of(9, 0));
		assertThat(stops.get(1).getDayNumber()).isEqualTo(1);
		assertThat(stops.get(1).getPlannedTime()).isEqualTo(LocalTime.of(10, 30));
		assertThat(stops.get(2).getDayNumber()).isEqualTo(1);
		assertThat(stops.get(2).getPlannedTime()).isEqualTo(LocalTime.of(12, 0));
	}

	@Test
	void schedule_travelPushesPastWindow_rollsToNextDay() {
		List<Stop> stops = stops(2);
		// 11 hours of travel — with a 1h visit at stop 1, stop 2 can't fit in the
		// remaining ~11h of a 12h window without exceeding it, so it must roll over.
		List<Double> legDurations = List.of(Duration.ofHours(11).getSeconds() + 1.0);

		scheduler.schedule(stops, legDurations);

		assertThat(stops.get(0).getDayNumber()).isEqualTo(1);
		assertThat(stops.get(1).getDayNumber()).isEqualTo(2);
		assertThat(stops.get(1).getPlannedTime()).isEqualTo(DAY_START);
	}

	@Test
	void schedule_manyStopsAcrossMultipleDays_incrementsDayNumberEachRollover() {
		List<Stop> stops = stops(4);
		// Each leg alone exceeds the window, forcing a rollover before every stop after the first.
		List<Double> legDurations = List.of(
				Duration.ofHours(20).getSeconds() + 0.0,
				Duration.ofHours(20).getSeconds() + 0.0,
				Duration.ofHours(20).getSeconds() + 0.0);

		scheduler.schedule(stops, legDurations);

		assertThat(stops.get(0).getDayNumber()).isEqualTo(1);
		assertThat(stops.get(1).getDayNumber()).isEqualTo(2);
		assertThat(stops.get(2).getDayNumber()).isEqualTo(3);
		assertThat(stops.get(3).getDayNumber()).isEqualTo(4);
	}

	@Test
	void schedule_missingLegDurations_treatsMissingEntriesAsZeroTravelTime() {
		List<Stop> stops = stops(3);
		// Only one leg duration provided for two legs — the second is missing entirely.
		List<Double> legDurations = List.of(600.0);

		scheduler.schedule(stops, legDurations);

		// No exception, and every stop still gets scheduled on day 1 (short window usage).
		assertThat(stops).allSatisfy(stop -> assertThat(stop.getDayNumber()).isEqualTo(1));
	}

	@Test
	void schedule_nullLegDurationsList_treatsEveryLegAsZeroTravelTime() {
		List<Stop> stops = stops(2);

		scheduler.schedule(stops, null);

		assertThat(stops.get(0).getDayNumber()).isEqualTo(1);
		assertThat(stops.get(1).getDayNumber()).isEqualTo(1);
		assertThat(stops.get(1).getPlannedTime()).isEqualTo(LocalTime.of(10, 0));
	}

	@Test
	void schedule_oversizedSingleVisit_doesNotInfiniteLoop() {
		// A visit duration longer than the whole window is a misconfiguration, but the
		// scheduler must still terminate rather than spin forever re-rolling days — each
		// oversized stop gets placed on its own day instead of never making progress.
		ItineraryScheduler oversizedVisitScheduler = new ItineraryScheduler(
				new RouteScheduleProperties(DAY_START, DAY_END, Duration.ofHours(13)));
		List<Stop> stops = stops(2);

		oversizedVisitScheduler.schedule(stops, List.of(0.0));

		assertThat(stops.get(0).getDayNumber()).isEqualTo(1);
		assertThat(stops.get(1).getDayNumber()).isEqualTo(2);
	}
}
