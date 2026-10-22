package kmu.maplayers.base.ribbon;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that each count lands in its own slot of the call tag, since the tag is only worth reading
 * while a cell refused for room and a cell that planned nothing are told apart.
 */
final class RibbonBakeTallyTests {

    // A band with something in it, so a laid cell carrying it counts as banded.
    private static final CellRibbon ANY_BAND = new CellRibbon(List.of(
        new RibbonBand(Color.WHITE, new float[] {0f, 0f, 1f, 0f, 0f, 1f})));

    private final RibbonBakeTally bakeTally = new RibbonBakeTally();

    @Nested
    class DescribeTally {

        // A call that counted nothing still writes every slot, so a reader never has to tell a
        // missing count from a zero.
        @Test
        void writesEverySlotAtZeroBeforeAnythingIsCounted() {

            // ARRANGE
            var expectedTally = "banded=0 traced=0 reused=0 unplanned=0 refusedForRoom=0";

            // ACT
            var actualTally = bakeTally.describeTally();

            // ASSERT
            assertThat(actualTally)
                .isEqualTo(expectedTally);
        }
    }

    @Nested
    class CountLaidCell {

        @Test
        void countsACellCarryingABandAsBanded() {

            // ARRANGE
            var expectedTally = "banded=1 traced=0 reused=0 unplanned=0 refusedForRoom=0";

            // ACT
            bakeTally.countLaidCell(ANY_BAND);

            // ASSERT
            var actualTally = bakeTally.describeTally();

            assertThat(actualTally)
                .isEqualTo(expectedTally);
        }

        @Test
        void countsACellCarryingNoBandAsRefusedForRoom() {

            // ARRANGE
            var expectedTally = "banded=0 traced=0 reused=0 unplanned=0 refusedForRoom=1";

            // ACT
            bakeTally.countLaidCell(CellRibbon.NONE);

            // ASSERT
            var actualTally = bakeTally.describeTally();

            assertThat(actualTally)
                .isEqualTo(expectedTally);
        }
    }

    @Nested
    class CountRingReach {

        @Test
        void countsACellThatWalkedARingAsTraced() {

            // ARRANGE
            var expectedTally = "banded=0 traced=1 reused=0 unplanned=0 refusedForRoom=0";

            // ACT
            bakeTally.countRingReach(true);

            // ASSERT
            var actualTally = bakeTally.describeTally();

            assertThat(actualTally)
                .isEqualTo(expectedTally);
        }

        @Test
        void countsACellThatFoundItsRingStandingAsReused() {

            // ARRANGE
            var expectedTally = "banded=0 traced=0 reused=1 unplanned=0 refusedForRoom=0";

            // ACT
            bakeTally.countRingReach(false);

            // ASSERT
            var actualTally = bakeTally.describeTally();

            assertThat(actualTally)
                .isEqualTo(expectedTally);
        }
    }

    @Nested
    class CountUnplannedCell {

        @Test
        void countsTheCellAsUnplanned() {

            // ARRANGE
            var expectedTally = "banded=0 traced=0 reused=0 unplanned=1 refusedForRoom=0";

            // ACT
            bakeTally.countUnplannedCell();

            // ASSERT
            var actualTally = bakeTally.describeTally();

            assertThat(actualTally)
                .isEqualTo(expectedTally);
        }
    }
}
