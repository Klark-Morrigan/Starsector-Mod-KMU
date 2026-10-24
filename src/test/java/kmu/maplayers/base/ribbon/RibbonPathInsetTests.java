package kmu.maplayers.base.ribbon;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins which distance each inset stands for, since a swap would trace every cell at the other
 * inset and still produce a ring that looks reasonable.
 */
final class RibbonPathInsetTests {

    // Round numbers: a 400-wide band 200 clear of the border runs its centreline 400 inside the
    // cell, and 200 inside it once the pad is given up.
    private static final RibbonStyle STYLE = new RibbonStyle(
        400.0,
        200.0,
        2.0,
        false);

    @Nested
    class ComputeInsetDistance {

        @Test
        void keepsThePadAndTheHalfWidthOnThePaddedInset() {

            // ARRANGE
            var expectedInsetDistance = 400.0;

            // ACT
            var actualInsetDistance = RibbonPathInset.PADDED.computeInsetDistance(STYLE);

            // ASSERT
            assertThat(actualInsetDistance)
                .isEqualTo(expectedInsetDistance);
        }

        @Test
        void keepsOnlyTheHalfWidthOnTheUnpaddedInset() {

            // ARRANGE
            var expectedInsetDistance = 200.0;

            // ACT
            var actualInsetDistance = RibbonPathInset.UNPADDED.computeInsetDistance(STYLE);

            // ASSERT
            assertThat(actualInsetDistance)
                .isEqualTo(expectedInsetDistance);
        }
    }
}
