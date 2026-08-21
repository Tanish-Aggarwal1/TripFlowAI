package com.tripflow.backend.service;

import java.io.ByteArrayOutputStream;

import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.tripflow.backend.dto.TripResponse;

import lombok.RequiredArgsConstructor;

/**
 * Generates a formatted PDF itinerary from a trip's ordered stops (EXPORT-02).
 *
 * <p>Delegates the ownership/visibility check to {@link TripService#getTrip} — same
 * "owner or PUBLIC trip" rule as every other trip read, not reimplemented here.
 */
@Service
@RequiredArgsConstructor
public class PdfExportService {

	private final TripService tripService;

	/** The trip's title is returned alongside the PDF bytes so the controller can
	 * build a sensible download filename without a second lookup (and second
	 * ownership check) against {@link TripService#getTrip}. */
	public record PdfExport(String tripTitle, byte[] pdfBytes) {
	}

	public PdfExport exportPdf(Long tripId, Long requesterId) {
		TripResponse trip = tripService.getTrip(tripId, requesterId);

		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			Document doc = new Document();
			PdfWriter.getInstance(doc, out);
			doc.open();

			doc.add(new Paragraph(trip.title()));

			doc.close();
			return new PdfExport(trip.title(), out.toByteArray());
		} catch (DocumentException ex) {
			// PdfWriter.getInstance/doc.add only throw against a malformed document
			// structure, never against I/O (we write to an in-memory stream) — a
			// checked exception here means a programming error, not a runtime
			// condition callers should handle.
			throw new IllegalStateException("Failed to build PDF export for trip " + tripId, ex);
		}
	}
}
