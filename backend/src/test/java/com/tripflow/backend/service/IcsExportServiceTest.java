package com.tripflow.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.tripflow.backend.domain.enums.StopType;
import com.tripflow.backend.domain.enums.TripStatus;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.StopResponse;
import com.tripflow.backend.dto.TripResponse;
import com.tripflow.backend.schedule.RouteScheduleProperties;

@ExtendWith(MockitoExtension.class)
class IcsExportServiceTest {

	@Mock private TripService tripService;

	private IcsExportService service;

	private static final Long TRIP_ID = 5L;
	private static final Long REQUESTER_ID = 1L;

	@BeforeEach
	void setUp() {
		RouteScheduleProperties scheduleProperties =
				new RouteScheduleProperties(LocalTime.of(9, 0), LocalTime.of(21, 0), Duration.ofHours(1));
		service = new IcsExportService(tripService, scheduleProperties);
	}

	private StopResponse stop(String name, String address, Integer dayNumber, LocalTime plannedTime) {
		return new StopResponse(1L, name, 43.65, -79.38, address, 0, null, null,
				dayNumber, plannedTime, StopType.SIGHTSEEING);
	}

	private TripResponse trip(LocalDate startDate, List<StopResponse> stops) {
		return new TripResponse(TRIP_ID, "Weekend Getaway", null, List.of(), TripVisibility.PRIVATE,
				TripStatus.DRAFT, REQUESTER_ID, stops, null, null, null, startDate);
	}

	@Test
	void exportIcs_delegatesOwnershipCheckToTripService() {
		given(tripService.getTrip(TRIP_ID, REQUESTER_ID)).willReturn(trip(null, List.of()));

		service.exportIcs(TRIP_ID, REQUESTER_ID);

		verify(tripService).getTrip(eq(TRIP_ID), eq(REQUESTER_ID));
	}

	@Test
	void exportIcs_returnsTheTripTitleAlongsideTheIcsContent() {
		given(tripService.getTrip(TRIP_ID, REQUESTER_ID)).willReturn(trip(null, List.of()));

		IcsExportService.IcsExport result = service.exportIcs(TRIP_ID, REQUESTER_ID);

		assertThat(result.tripTitle()).isEqualTo("Weekend Getaway");
	}

	@Test
	void exportIcs_scheduledStop_producesTimedVeventUsingDayNumberAndPlannedTime() {
		StopResponse scheduled = stop("St. Lawrence Market", "93 Front St E", 1, LocalTime.of(9, 0));
		given(tripService.getTrip(TRIP_ID, REQUESTER_ID))
				.willReturn(trip(LocalDate.of(2026, 8, 10), List.of(scheduled)));

		String ics = service.exportIcs(TRIP_ID, REQUESTER_ID).icsContent();

		assertThat(ics).contains("BEGIN:VEVENT");
		assertThat(ics).contains("SUMMARY:St. Lawrence Market");
		assertThat(ics).contains("LOCATION:93 Front St E");
		assertThat(ics).contains("DTSTART:20260810T090000");
		assertThat(ics).contains("DTEND:20260810T100000");
		assertThat(ics).contains("GEO:43.65;-79.38");
	}

	@Test
	void exportIcs_unscheduledStop_fallsBackToAllDayEventOnTripStartDate() {
		StopResponse unscheduled = stop("Cottage", null, null, null);
		given(tripService.getTrip(TRIP_ID, REQUESTER_ID))
				.willReturn(trip(LocalDate.of(2026, 8, 10), List.of(unscheduled)));

		String ics = service.exportIcs(TRIP_ID, REQUESTER_ID).icsContent();

		assertThat(ics).contains("SUMMARY:Cottage");
		// All-day VALUE=DATE events use bare DTSTART/DTEND (no "T" time component).
		assertThat(ics).contains("DTSTART;VALUE=DATE:20260810");
		assertThat(ics).contains("DTEND;VALUE=DATE:20260811");
	}

	@Test
	void exportIcs_unscheduledStopAndNoTripStartDate_fallsBackToTodayAsAllDay() {
		StopResponse unscheduled = stop("Cottage", null, null, null);
		given(tripService.getTrip(TRIP_ID, REQUESTER_ID)).willReturn(trip(null, List.of(unscheduled)));

		String ics = service.exportIcs(TRIP_ID, REQUESTER_ID).icsContent();

		String today = LocalDate.now().toString().replace("-", "");
		assertThat(ics).contains("DTSTART;VALUE=DATE:" + today);
	}

	@Test
	void exportIcs_stopWithNoAddress_omitsLocation() {
		StopResponse noAddress = stop("Remote Trailhead", null, 1, LocalTime.of(9, 0));
		given(tripService.getTrip(TRIP_ID, REQUESTER_ID))
				.willReturn(trip(LocalDate.of(2026, 8, 10), List.of(noAddress)));

		String ics = service.exportIcs(TRIP_ID, REQUESTER_ID).icsContent();

		assertThat(ics).doesNotContain("LOCATION:");
	}

	@Test
	void exportIcs_multipleStops_producesOneVeventPerStop() {
		StopResponse first = stop("Stop A", "Addr A", 1, LocalTime.of(9, 0));
		StopResponse second = stop("Stop B", "Addr B", 1, LocalTime.of(11, 0));
		given(tripService.getTrip(TRIP_ID, REQUESTER_ID))
				.willReturn(trip(LocalDate.of(2026, 8, 10), List.of(first, second)));

		String ics = service.exportIcs(TRIP_ID, REQUESTER_ID).icsContent();

		assertThat(ics.split("BEGIN:VEVENT", -1)).hasSize(3); // 2 events -> 3 pieces when split
		assertThat(ics).contains("SUMMARY:Stop A");
		assertThat(ics).contains("SUMMARY:Stop B");
	}

	@Test
	void exportIcs_noStops_producesValidEmptyCalendar() {
		given(tripService.getTrip(TRIP_ID, REQUESTER_ID)).willReturn(trip(null, List.of()));

		String ics = service.exportIcs(TRIP_ID, REQUESTER_ID).icsContent();

		assertThat(ics).contains("BEGIN:VCALENDAR");
		assertThat(ics).contains("END:VCALENDAR");
		assertThat(ics).doesNotContain("BEGIN:VEVENT");
	}
}
