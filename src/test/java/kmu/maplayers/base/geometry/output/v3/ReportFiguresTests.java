package kmu.maplayers.base.geometry.output.v3;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit coverage for how a report reads a population of numbers out. */
class ReportFiguresTests {

    // Eleven values, so the ninetieth percentile lands on an index of its own: floor(0.9 * 10).
    private static final List<Double> ZERO_TO_TEN =
        List.of(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0);

    @Nested
    class DescribeSpread {

        @Test
        void readsTheMiddleTheFarEndAndTheWorst() {

            assertThat(ReportFigures.describeSpread(ZERO_TO_TEN, "%.0f"))
                .isEqualTo("p50 5 / p90 9 / max 10");
        }

        @Test
        void printsEachValueInTheFormatGiven() {

            assertThat(ReportFigures.describeSpread(List.of(0.25, 0.5, 0.75), "%.2f"))
                .isEqualTo("p50 0.50 / p90 0.50 / max 0.75");
        }

        @Test
        void anEmptyPopulationReadsAsNothing() {

            assertThat(ReportFigures.describeSpread(List.of(), "%.0f"))
                .isEqualTo("p50 0 / p90 0 / max 0");
        }
    }

    @Nested
    class FindMedian {

        @Test
        void sortsBeforeReadingTheMiddle() {

            assertThat(ReportFigures.findMedian(List.of(9.0, 1.0, 5.0)))
                .isEqualTo(5.0);
        }
    }
}
