package com.tripflow.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * SCRUM-261 regression coverage: {@link TripExportController#sanitizeFilename} is one half
 * of a matched pair with the frontend's {@code sanitizeFilename} in
 * {@code trip-view.page.ts} (the two implementations can't share code across
 * languages/processes, so they're kept aligned by asserting the same fixture inputs
 * produce the same outputs on both sides — see {@code trip-view.page.spec.ts} for the
 * frontend half of this fixture set).
 */
class TripExportControllerTest {

	@ParameterizedTest
	@CsvSource({
			"'Trip: \"Fun\" / Times?', 'Trip Fun  Times'",
			"'Ottawa Weekend', 'Ottawa Weekend'",
			"'Café Trip', 'Caf Trip'",
			"'  ', trip",
			"'', trip",
	})
	void sanitizeFilename_stripsUnsafeCharactersAndFallsBackWhenEmpty(String input, String expected) {
		assertThat(TripExportController.sanitizeFilename(input)).isEqualTo(expected);
	}

	@Test
	void sanitizeFilename_longTitle_truncatedTo100Characters() {
		String longTitle = "a".repeat(150);

		String result = TripExportController.sanitizeFilename(longTitle);

		assertThat(result).hasSize(100);
		assertThat(result).isEqualTo("a".repeat(100));
	}
}
