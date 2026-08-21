package com.tripflow.backend.service;

import java.io.ByteArrayOutputStream;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.tripflow.backend.dto.StopResponse;
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

	private static final int STOP_TABLE_COLUMNS = 4; // #, name/address, schedule, notes

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

			addHeader(doc, trip);
			addStopsTable(doc, trip);

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

	private void addHeader(Document doc, TripResponse trip) throws DocumentException {
		doc.add(new Paragraph(trip.title()));

		StringBuilder subHeader = new StringBuilder();
		if (trip.startDate() != null) {
			subHeader.append(trip.startDate());
		}
		if (!trip.stops().isEmpty()) {
			if (subHeader.length() > 0) {
				subHeader.append(" — ");
			}
			subHeader.append(trip.stops().size()).append(" stop").append(trip.stops().size() == 1 ? "" : "s");
		}
		if (subHeader.length() > 0) {
			doc.add(new Paragraph(subHeader.toString()));
		}
		if (trip.description() != null) {
			doc.add(new Paragraph(trip.description()));
		}
	}

	private void addStopsTable(Document doc, TripResponse trip) throws DocumentException {
		if (trip.stops().isEmpty()) {
			return;
		}

		List<StopResponse> sortedStops = trip.stops().stream()
				.sorted(Comparator.comparing(StopResponse::stopOrder))
				.toList();

		PdfPTable table = new PdfPTable(STOP_TABLE_COLUMNS);
		table.setWidthPercentage(100);
		table.addCell("#");
		table.addCell("Stop");
		table.addCell("Schedule");
		table.addCell("Notes");

		for (StopResponse stop : sortedStops) {
			table.addCell(String.valueOf(stop.stopOrder() + 1));
			table.addCell(nameAndAddress(stop));
			table.addCell(schedule(stop));
			table.addCell(stop.notes() != null ? stop.notes() : "");
		}

		doc.add(table);
	}

	private static String nameAndAddress(StopResponse stop) {
		if (stop.address() == null) {
			return stop.name();
		}
		return stop.name() + "\n" + stop.address();
	}

	private static String schedule(StopResponse stop) {
		if (stop.dayNumber() == null || stop.plannedTime() == null) {
			return "";
		}
		return "Day " + stop.dayNumber() + ", " + stop.plannedTime();
	}
}
