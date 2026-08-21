package com.tripflow.backend.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TripCompletionTest {

    @ParameterizedTest(name = "visited={0} stopCount={1} -> {2}")
    @CsvSource({
            "0, 0, 0.0",
            "0, 5, 0.0",
            "3, 5, 0.6",
            "5, 5, 1.0",
            "0, 4, 0.0",
    })
    void percentage_matchesExpected(long visited, long stopCount, double expected) {
        double result = TripCompletion.percentage(visited, stopCount);

        assertThat(result).isCloseTo(expected, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(result).isNotNaN();
        assertThat(Double.isInfinite(result)).isFalse();
    }

    @org.junit.jupiter.api.Test
    void percentage_zeroStopCount_returnsExactlyZero() {
        assertThat(TripCompletion.percentage(0, 0)).isEqualTo(0.0);
    }
}
