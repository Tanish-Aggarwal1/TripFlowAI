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
import com.tripflow.backend.service.PdfExportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Trip export endpoints (SCRUM-176) — kept separate from {@link TripController} since
 * this is expected to grow (calendar and PDF export both live here, see the fall break
 * plan) and each format is its own concern, not trip CRUD.
 */
@Tag(name = "Trip Export", description = "Calendar/PDF export of a trip's itinerary")
@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripExportController {

	private static final MediaType TEXT_CALENDAR = MediaType.parseMediaType("text/calendar");

	private final IcsExportService icsExportService;
	private final PdfExportService pdfExportService;

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

	@Operation(summary = "Export a trip as a formatted PDF itinerary",
			description = "Header, ordered stops with notes, and (when available) a route-map "
					+ "snapshot. Owner sees any trip; non-owners only see PUBLIC trips — "
					+ "same rule as GET /api/trips/{id}.")
	@GetMapping(value = "/{id}/export/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
	public ResponseEntity<byte[]> exportPdf(
			@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
		PdfExportService.PdfExport export = pdfExportService.exportPdf(id, principal.userId());

		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION,
						"attachment; filename=\"" + sanitizeFilename(export.tripTitle()) + ".pdf\"")
				.body(export.pdfBytes());
	}

	/**
	 * Strips everything except letters, digits, spaces, and dashes so the trip title
	 * can't inject header syntax (CR/LF, quotes) or path-unsafe characters into the
	 * Content-Disposition filename, and caps length well under filesystem limits.
	 *
	 * <p>Matched by a second, independent implementation in the frontend
	 * ({@code trip-view.page.ts}'s {@code sanitizeFilename}) — Blob downloads lose
	 * response headers, so the client can't just read this filename back from the
	 * server and has to compute its own. Keep the character-set and length-cap rules
	 * identical on both sides; a shared fixture set is asserted against in
	 * {@code TripExportControllerTest} (backend) and {@code trip-view.page.spec.ts}
	 * (frontend) to catch future drift. Package-private (not private) so that test
	 * can call it directly.
	 */
	static String sanitizeFilename(String title) {
		String sanitized = title.replaceAll("[^a-zA-Z0-9 \\-]", "").trim();
		if (sanitized.isEmpty()) {
			sanitized = "trip";
		}
		return sanitized.length() > 100 ? sanitized.substring(0, 100) : sanitized;
	}
}
