package kmu.maplayers.ownermap.ribbon;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the two pairs of lengths the rules settle from one authored pair: the contested pair as the
 * player authored it, and the uncontested pair with only its stake run shortened, and only where
 * the switch says so.
 *
 * <p>Each half fails quietly if it drifts. A shortening that reached the parting as well would leave
 * a tally whose ticks and gaps are the same length, which reads as one long run rather than as a
 * count; and a shortening that reached the contested pair would quiet the very cells the design
 * exists to draw a reader to.
 */
final class RibbonPlanRulesTests {

    // A stake four widths long parted by two. Neither is the design's own proportion, and the two
    // differ from each other and from one width, so each length a case reads back is told apart by
    // value rather than by coincidence.
    private static final RibbonSegmentLengths AUTHORED_LENGTHS = new RibbonSegmentLengths(4, 2);

    // The shortening switch's two answers, named so a case reads which one it poses.
    private static final boolean UNCONTESTED_RUNS_SHORTENED = true;
    private static final boolean UNCONTESTED_RUNS_AUTHORED = false;

    @Nested
    class GetContestedLengths {

        // A contested cell is the readout the design exists for, so its runs keep the player's
        // lengths whatever the shortening says.
        @Test
        void answersTheAuthoredLengthsWhereTheUncontestedRunsAreShortened() {

            // ARRANGE
            var rules = RibbonPlanRules.createFromAuthoredLengths(AUTHORED_LENGTHS, UNCONTESTED_RUNS_SHORTENED);
            var expectedLengths = new RibbonSegmentLengths(4, 2);

            // ACT
            var actualLengths = rules.getContestedLengths();

            // ASSERT
            assertThat(actualLengths)
                .isEqualTo(expectedLengths);
        }
    }

    @Nested
    class GetUncontestedLengths {

        // The stake run alone is cut. The parting says the same thing on an uncontested cell as on
        // any other - one stake ends, the next begins - so the shortening has no business with it.
        @Test
        void cutsOnlyTheStakeRunToOneWidthWhereTheRunsAreShortened() {

            // ARRANGE
            var rules = RibbonPlanRules.createFromAuthoredLengths(AUTHORED_LENGTHS, UNCONTESTED_RUNS_SHORTENED);
            var expectedLengths = new RibbonSegmentLengths(1, 2);

            // ACT
            var actualLengths = rules.getUncontestedLengths();

            // ASSERT
            assertThat(actualLengths)
                .isEqualTo(expectedLengths);
        }

        @Test
        void answersTheAuthoredLengthsWhereTheRunsAreNotShortened() {

            // ARRANGE
            var rules = RibbonPlanRules.createFromAuthoredLengths(AUTHORED_LENGTHS, UNCONTESTED_RUNS_AUTHORED);
            var expectedLengths = new RibbonSegmentLengths(4, 2);

            // ACT
            var actualLengths = rules.getUncontestedLengths();

            // ASSERT
            assertThat(actualLengths)
                .isEqualTo(expectedLengths);
        }

        // Asked once per uncontested cell, so the shortened pair is settled once when the rules
        // are made rather than built again for each cell.
        @Test
        void answersEveryCellWithThePairSettledWhenTheRulesWereMade() {

            // ARRANGE
            var rules = RibbonPlanRules.createFromAuthoredLengths(AUTHORED_LENGTHS, UNCONTESTED_RUNS_SHORTENED);
            var firstLengths = rules.getUncontestedLengths();

            // ACT
            var actualLengths = rules.getUncontestedLengths();

            // ASSERT
            assertThat(actualLengths)
                .isSameAs(firstLengths);
        }
    }
}
