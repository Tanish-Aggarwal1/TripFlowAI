package com.tripflow.backend.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tripflow.backend.security.UserPrincipal;
import com.tripflow.backend.service.IcsExportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Trip export endpoints (SCRUM-176) — kept separate from {@link TripController} since
 * this is expected to grow (PDF export is planned next, see the fall break plan) and
 * each format is its own concern, not trip CRUD.
 */
@Tag(name = "Trip Export", description = "Calendar/PDF export of a trip's itinerary")
@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripExportController {

	private static final MediaType TEXT_CALENDAR = MediaType.parseMediaType("text/calendar");

	private final IcsExportService icsExportService;

	@Operation(summary = "Export a trip as an .ics calendar file",
			description = "One VEVENT per stop. Owner sees any trip; non-owners only see PUBLIC trips — "
					+ "same rule as GET /api/trips/{id}.")
	@GetMapping(value = "/{id}/calendar.ics", produces = "text/calendar")
	public ResponseEntity<String> exportIcs(
			@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
		IcsExportService.IcsExport export = icsExportService.exportIcs(id, principal.userId());

		return ResponseEntity.ok()
				.contentType(TEXT_CALENDAR)
				.header(HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"" + sanitizeFilename(export.tripTitle()) + ".ics\"")
				.body(export.icsContent());
	}

	/**
	 * Strips everything except letters, digits, spaces, and dashes so the trip title
	 * can't inject header syntax (CR/LF, quotes) or path-unsafe characters into the
	 * Content-Disposition filename, and caps length well under filesystem limits.
	 */
	private static String sanitizeFilename(String title) {
		String sanitized = title.replaceAll("[^a-zA-Z0-9 \\-]", "").trim();
		if (sanitized.isEmpty()) {
			sanitized = "trip";
		}
		return sanitized.length() > 100 ? sanitized.substring(0, 100) : sanitized;
	}
}
