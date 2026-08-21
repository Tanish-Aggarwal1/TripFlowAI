package com.tripflow.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.tripflow.backend.domain.enums.TripStatus;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.StopResponse;
import com.tripflow.backend.dto.TripResponse;

@ExtendWith(MockitoExtension.class)
class PdfExportServiceTest {

	@Mock private TripService tripService;

	private PdfExportService service;

	private static final Long TRIP_ID = 5L;
	private static final Long REQUESTER_ID = 1L;
	private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

	@BeforeEach
	void setUp() {
		service = new PdfExportService(tripService);
	}

	private TripResponse trip(List<StopResponse> stops) {
		return new TripResponse(TRIP_ID, "Weekend Getaway", null, List.of(), TripVisibility.PRIVATE,
				TripStatus.DRAFT, REQUESTER_ID, stops, null, null, null, null, 0);
	}

	@Test
	void exportPdf_delegatesOwnershipCheckToTripService() {
		given(tripService.getTrip(TRIP_ID, REQUESTER_ID)).willReturn(trip(List.of()));

		service.exportPdf(TRIP_ID, REQUESTER_ID);

		verify(tripService).getTrip(eq(TRIP_ID), eq(REQUESTER_ID));
	}

	@Test
	void exportPdf_returnsBytesStartingWithThePdfMagicNumber() {
		given(tripService.getTrip(TRIP_ID, REQUESTER_ID)).willReturn(trip(List.of()));

		PdfExportService.PdfExport export = service.exportPdf(TRIP_ID, REQUESTER_ID);

		assertThat(export.pdfBytes()).startsWith(PDF_MAGIC);
	}

	@Test
	void exportPdf_zeroStopTrip_stillReturnsAValidPdf() {
		given(tripService.getTrip(TRIP_ID, REQUESTER_ID)).willReturn(trip(List.of()));

		PdfExportService.PdfExport export = service.exportPdf(TRIP_ID, REQUESTER_ID);

		assertThat(export.pdfBytes()).isNotEmpty();
		assertThat(export.pdfBytes()).startsWith(PDF_MAGIC);
	}
}
