package com.tripflow.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import com.tripflow.backend.client.mapbox.MapboxClient;
import com.tripflow.backend.domain.enums.StopStatus;
import com.tripflow.backend.domain.enums.StopType;
import com.tripflow.backend.domain.enums.TripStatus;
import com.tripflow.backend.domain.enums.TripVisibility;
import com.tripflow.backend.dto.StopResponse;
import com.tripflow.backend.dto.TripResponse;
import com.tripflow.backend.exception.MapboxClientException;

@ExtendWith(MockitoExtension.class)
class PdfExportServiceTest {

	@Mock private TripService tripService;
	@Mock private MapboxClient mapboxClient;

	private PdfExportService service;

	private static final Long TRIP_ID = 5L;
	private static final Long REQUESTER_ID = 1L;
	private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

	@BeforeEach
	void setUp() {
		service = new PdfExportService(tripService, mapboxClient);
	}

	private TripResponse trip(List<StopResponse> stops) {
		return trip(stops, null, null);
	}

	private TripResponse trip(List<StopResponse> stops, String description) {
		return trip(stops, description, null);
	}

	private TripResponse trip(List<StopResponse> stops, String description, String routeGeometry) {
		return new TripResponse(TRIP_ID, "Weekend Getaway", description, List.of(), TripVisibility.PRIVATE,
				TripStatus.DRAFT, REQUESTER_ID, stops, null, null, routeGeometry, null, 0, 0);
	}

	private StopResponse stop(String name, Integer stopOrder, String notes, Integer dayNumber,
			LocalTime plannedTime) {
		return new StopResponse(1L, name, 43.65, -79.38, null, stopOrder, StopStatus.PLANNED, notes,
				dayNumber, plannedTime, StopType.SIGHTSEEING);
	}

	private static String extractText(byte[] pdfBytes) throws Exception {
		PdfReader reader = new PdfReader(pdfBytes);
		try {
			return new PdfTextExtractor(reader).getTextFromPage(1);
		} finally {
			reader.close();
		}
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

	@Test
	void exportPdf_threeStops_extractedTextContainsTitleAndAllStopNames() throws Exception {
		List<StopResponse> stops = List.of(
				stop("St. Lawrence Market", 0, "Bring cash", 1, LocalTime.of(9, 0)),
				stop("Casa Loma", 1, null, 1, LocalTime.of(11, 0)),
				stop("Distillery District", 2, "Evening stroll", 2, LocalTime.of(18, 0)));
		given(tripService.getTrip(TRIP_ID, REQUESTER_ID)).willReturn(trip(stops));

		byte[] pdfBytes = service.exportPdf(TRIP_ID, REQUESTER_ID).pdfBytes();
		String text = extractText(pdfBytes);

		assertThat(text).contains("Weekend Getaway");
		assertThat(text).contains("St. Lawrence Market");
		assertThat(text).contains("Casa Loma");
		assertThat(text).contains("Distillery District");
	}

	@Test
	void exportPdf_stopsOutOfOrder_renderInAscendingStopOrderSequence1Indexed() throws Exception {
		// Passed in reverse stopOrder — service must sort ascending before rendering.
		List<StopResponse> stops = List.of(
				stop("Third Stop", 2, null, 1, null),
				stop("First Stop", 0, null, 1, null),
				stop("Second Stop", 1, null, 1, null));
		given(tripService.getTrip(TRIP_ID, REQUESTER_ID)).willReturn(trip(stops));

		String text = extractText(service.exportPdf(TRIP_ID, REQUESTER_ID).pdfBytes());

		int firstIdx = text.indexOf("First Stop");
		int secondIdx = text.indexOf("Second Stop");
		int thirdIdx = text.indexOf("Third Stop");
		assertThat(firstIdx).isGreaterThanOrEqualTo(0);
		assertThat(secondIdx).isGreaterThan(firstIdx);
		assertThat(thirdIdx).isGreaterThan(secondIdx);
		// 1-indexed position column.
		assertThat(text).contains("1");
		assertThat(text).contains("2");
		assertThat(text).contains("3");
	}

	@Test
	void exportPdf_stopWithNullNotes_rendersEmptyCellNotLiteralNull() throws Exception {
		List<StopResponse> stops = List.of(stop("Cottage", 0, null, 1, LocalTime.of(9, 0)));
		given(tripService.getTrip(TRIP_ID, REQUESTER_ID)).willReturn(trip(stops));

		String text = extractText(service.exportPdf(TRIP_ID, REQUESTER_ID).pdfBytes());

		assertThat(text).doesNotContain("null");
	}

	@Test
	void exportPdf_stopWithNullDayNumberAndPlannedTime_rendersWithoutThrowing() {
		List<StopResponse> stops = List.of(stop("Unscheduled Stop", 0, null, null, null));
		given(tripService.getTrip(TRIP_ID, REQUESTER_ID)).willReturn(trip(stops));

		assertThatCode(() -> service.exportPdf(TRIP_ID, REQUESTER_ID)).doesNotThrowAnyException();
	}

	@Test
	void exportPdf_zeroStops_extractedTextContainsTitleAndNoTableRows() throws Exception {
		given(tripService.getTrip(TRIP_ID, REQUESTER_ID)).willReturn(trip(List.of()));

		String text = extractText(service.exportPdf(TRIP_ID, REQUESTER_ID).pdfBytes());

		assertThat(text).contains("Weekend Getaway");
	}

	@Test
	void exportPdf_nullDescription_producesAValidPdf() throws Exception {
		given(tripService.getTrip(TRIP_ID, REQUESTER_ID)).willReturn(trip(List.of(), null));

		byte[] pdfBytes = service.exportPdf(TRIP_ID, REQUESTER_ID).pdfBytes();

		assertThat(pdfBytes).startsWith(PDF_MAGIC);
	}

	@Test
	void exportPdf_mapboxClientThrows_stillYieldsPdfBytes() {
		List<StopResponse> stops = List.of(stop("Cottage", 0, null, 1, LocalTime.of(9, 0)));
		given(tripService.getTrip(TRIP_ID, REQUESTER_ID)).willReturn(trip(stops));
		when(mapboxClient.staticSnapshot(any(), any())).thenThrow(new MapboxClientException("Mapbox down"));

		byte[] pdfBytes = service.exportPdf(TRIP_ID, REQUESTER_ID).pdfBytes();

		assertThat(pdfBytes).startsWith(PDF_MAGIC);
	}

	@Test
	void exportPdf_nullRouteGeometry_stillYieldsAValidPdf() {
		List<StopResponse> stops = List.of(stop("Cottage", 0, null, 1, LocalTime.of(9, 0)));
		given(tripService.getTrip(TRIP_ID, REQUESTER_ID)).willReturn(trip(stops, null, null));
		given(mapboxClient.staticSnapshot(eq(null), any())).willReturn(Optional.empty());

		byte[] pdfBytes = service.exportPdf(TRIP_ID, REQUESTER_ID).pdfBytes();

		assertThat(pdfBytes).startsWith(PDF_MAGIC);
	}

	@Test
	void exportPdf_zeroStopTrip_neverCallsMapboxClient() {
		given(tripService.getTrip(TRIP_ID, REQUESTER_ID)).willReturn(trip(List.of()));

		service.exportPdf(TRIP_ID, REQUESTER_ID);

		verifyNoInteractions(mapboxClient);
	}
}
