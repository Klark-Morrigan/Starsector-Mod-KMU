package kmu.maplayers.base.ribbon;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins when the overlay counts a path as having nothing to draw.
 *
 * <p>Either kind of stretch is something to draw. A ring carved the whole way round holds no
 * stretch a band may use, and it is still the one picture that says why the cell is bare, so a
 * path holding only carved ring must not read as empty.
 */
final class CellRibbonPathTests {

    // One short stretch, in the packed form both stretch lists hold.
    private static final float[] STRETCH = new float[] {0f, 0f, 1f, 1f};

    // Where a path is walked from; any point serves, since emptiness never reads it.
    private static final float[] START_POINT = new float[] {0f, 0f};

    @Nested
    class IsEmpty {

        @Test
        void answersTrueForTheCellNoPathWasTracedOn() {

            var actualIsEmpty = CellRibbonPath.NONE.isEmpty();

            assertThat(actualIsEmpty)
                .isTrue();
        }

        @Test
        void answersFalseForAPathHoldingRingABandMayUse() {

            var ribbonPath = new CellRibbonPath(
                List.of(STRETCH),
                List.of(),
                START_POINT,
                RibbonPathVerdict.LAID_AT_PAD);

            var actualIsEmpty = ribbonPath.isEmpty();

            assertThat(actualIsEmpty)
                .isFalse();
        }

        @Test
        void answersFalseForAPathHoldingOnlyCarvedRing() {

            var ribbonPath = new CellRibbonPath(
                List.of(),
                List.of(STRETCH),
                START_POINT,
                RibbonPathVerdict.REFUSED);

            var actualIsEmpty = ribbonPath.isEmpty();

            assertThat(actualIsEmpty)
                .isFalse();
        }
    }
}
