package com.tripflow.backend.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import org.springframework.stereotype.Service;

import biweekly.ICalendar;
import biweekly.component.VEvent;
import biweekly.property.Geo;

import com.tripflow.backend.dto.StopResponse;
import com.tripflow.backend.dto.TripResponse;
import com.tripflow.backend.schedule.RouteScheduleProperties;

import lombok.RequiredArgsConstructor;

/**
 * Generates a standard .ics (RFC 5545) file from a trip's ordered stops (SCRUM-176).
 *
 * <p>Delegates the ownership/visibility check to {@link TripService#getTrip} — same
 * "owner or PUBLIC trip" rule as every other trip read, not reimplemented here.
 *
 * <p>Each stop becomes one {@link VEvent}: {@code SUMMARY} is the stop name,
 * {@code LOCATION}/{@code GEO} come from the stop's address/coordinates, and
 * {@code DTSTART}/{@code DTEND} come from the stop's {@code dayNumber}/{@code plannedTime}
 * (SCRUM-244a) when the trip has been scheduled. A stop with no schedule yet falls back to
 * an all-day event on the trip's {@code startDate} (or the export date, if that's also unset).
 */
@Service
@RequiredArgsConstructor
public class IcsExportService {

	private static final ZoneId ZONE = ZoneId.systemDefault();

	private final TripService tripService;
	private final RouteScheduleProperties scheduleProperties;

	/** The trip's title is returned alongside the .ics content so the controller can
	 * build a sensible download filename without a second lookup (and second
	 * ownership check) against {@link TripService#getTrip}. */
	public record IcsExport(String tripTitle, String icsContent) {
	}

	public IcsExport exportIcs(Long tripId, Long requesterId) {
		TripResponse trip = tripService.getTrip(tripId, requesterId);

		ICalendar ical = new ICalendar();
		ical.setProductId("-//TripFlowAI//Calendar Export//EN");
		// A stop's plannedTime is a destination-local wall-clock time — we don't know
		// the destination's actual timezone (no lookup from lat/lng), so every
		// DATE-TIME property is written "floating" (no Z, no TZID) rather than
		// converted through the server's own timezone, which would silently corrupt
		// the hour depending on wherever the app happens to be deployed.
		ical.getTimezoneInfo().setGlobalFloatingTime(true);

		LocalDate anchorDate = trip.startDate() != null ? trip.startDate() : LocalDate.now();
		for (StopResponse stop : trip.stops()) {
			ical.addEvent(toVEvent(stop, anchorDate));
		}

		return new IcsExport(trip.title(), ical.write());
	}

	private VEvent toVEvent(StopResponse stop, LocalDate anchorDate) {
		VEvent event = new VEvent();
		event.setSummary(stop.name());
		if (stop.address() != null) {
			event.setLocation(stop.address());
		}
		event.setGeo(new Geo(stop.latitude(), stop.longitude()));

		if (stop.dayNumber() != null && stop.plannedTime() != null) {
			LocalDateTime start = LocalDateTime.of(
					anchorDate.plusDays(stop.dayNumber() - 1L), stop.plannedTime());
			Duration visitDuration = scheduleProperties.defaultVisitDuration();
			event.setDateStart(toDate(start), true);
			event.setDateEnd(toDate(start.plus(visitDuration)), true);
		} else {
			// No schedule yet — all-day event on the trip's date (or the export date).
			event.setDateStart(toDate(anchorDate.atStartOfDay()), false);
			event.setDateEnd(toDate(anchorDate.plusDays(1).atStartOfDay()), false);
		}

		return event;
	}

	private static Date toDate(LocalDateTime dateTime) {
		return Date.from(dateTime.atZone(ZONE).toInstant());
	}
}
