package kmu.desktop.ui.swing;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how {@link SliderRows} reads a typed value: clamped into the range, and refused where it
 * is not a number.
 */
final class SliderRowsTests {

    private static final SliderRows.SliderRange RANGE = new SliderRows.SliderRange(10, 20);

    @Nested
    class ParseValue {

        @Test
        void readsAValueInsideTheRange() {

            assertThat(SliderRows.parseValue("12.5", RANGE))
                .isEqualTo(12.5);
        }

        @Test
        void clampsAValueAboveTheRangeToItsTop() {

            assertThat(SliderRows.parseValue("1000", RANGE))
                .isEqualTo(20);
        }

        @Test
        void clampsAValueBelowTheRangeToItsBottom() {

            assertThat(SliderRows.parseValue("-3", RANGE))
                .isEqualTo(10);
        }

        @Test
        void ignoresSurroundingSpace() {

            assertThat(SliderRows.parseValue("  15 ", RANGE))
                .isEqualTo(15);
        }

        @Test
        void refusesTextThatIsNotANumber() {

            assertThat(SliderRows.parseValue("fifteen", RANGE))
                .isNaN();
        }
    }
}
