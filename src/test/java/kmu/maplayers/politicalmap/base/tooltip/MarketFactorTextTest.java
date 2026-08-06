package kmu.maplayers.politicalmap.base.tooltip;

import kmu.maplayers.politicalmap.base.dominance.BaseSizeFactor;
import kmu.maplayers.politicalmap.base.dominance.PatrolFactor;
import kmu.maplayers.politicalmap.base.dominance.PatrolTierFactor;
import kmu.maplayers.politicalmap.base.dominance.StationFactor;
import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the grammar every factor line is worded to: what went in, the weight it became, and the cuts
 * taken off it in the order the weight read took them.
 *
 * <p>The two units are the point of most of these cases. A rating reads as the player set it, a
 * weight on the dominance grid the rest of the box counts in - so a line's numbers can be added up
 * against the market's own and a market's against its bloc's. A case that let the two blur would
 * make the box state an account that does not sum.
 *
 * <p>The parts are hand-built, since what a factor is worth is the weight read's decision and pinned
 * there; what is asserted here is only how those values read.
 */
final class MarketFactorTextTest {

    @BeforeEach
    void installStrings() {
        StarsectorSettingsFake.installSettings();
    }

    @AfterEach
    void clearStrings() {
        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class FormatStability {

        @Test
        void formatStabilityStatesAWholeReadingWithoutAFraction() {
            // Stability is the one number in the box a player also reads on the colony itself, so it
            // reads as they read it there.
            assertThat(MarketFactorText.formatStability(5.0))
                .isEqualTo("5");
        }

        @Test
        void formatStabilityKeepsAFractionalReading() {
            // A modded or drifting stability lands off the whole numbers, and rounding it would put
            // the box's stated cause a step away from the cut it caused.
            assertThat(MarketFactorText.formatStability(4.5))
                .isEqualTo("4.5");
        }
    }

    @Nested
    class FormatBaseSize {

        @Test
        void formatBaseSizeStatesTheSizeAndTheWeightItBecame() {
            assertThat(MarketFactorText.formatBaseSize(new BaseSizeFactor(5, 5.0, 5.0, 0.0), false))
                .isEqualTo("5 :: 5,000");
        }

        @Test
        void formatBaseSizeStatesTheCutLowStabilityTook() {
            assertThat(MarketFactorText.formatBaseSize(new BaseSizeFactor(5, 5.0, 4.5, 0.1), false))
                .isEqualTo("5 :: 4,500 (-10%)");
        }

        @Test
        void formatBaseSizeMarksARatingThatIsNotTheColonysOwnSize() {
            // Under fixed hidden-market scaling the rating is a token rather than a size, and an
            // unmarked one reads as a size the colony does not have.
            assertThat(MarketFactorText.formatBaseSize(new BaseSizeFactor(7, 2.5, 2.5, 0.0), true))
                .isEqualTo("2.5 (fixed) :: 2,500");
        }
    }

    @Nested
    class FormatStation {

        @Test
        void formatStationStatesBothCutsInTheOrderTheyWereTaken() {
            // The hidden-market rate is applied to the station weight and low stability to what
            // survived it, so stating them the other way round would describe different arithmetic.
            assertThat(MarketFactorText.formatStation(
                    new StationFactor("Fort Ludd", 3.0, 0.25, 0.1, 2.025)))
                .isEqualTo("3 :: 2,025 (-25%) (-10%)");
        }

        @Test
        void formatStationLeavesACutOfNothingUnsaid() {
            // A colony held in the open forfeits nothing to the hidden rate, and a deduction of zero
            // printed anyway would read as a cut on every openly held stationed colony in the box.
            assertThat(MarketFactorText.formatStation(
                    new StationFactor("Fort Ludd", 3.0, 0.0, 0.1, 2.7)))
                .isEqualTo("3 :: 2,700 (-10%)");
        }
    }

    @Nested
    class FormatPatrols {

        @Test
        void formatPatrolsHeadsTheLineWithTheHeadcount() {
            // The count is what a player can check against the map, so it is what the garrison's line
            // leads on rather than the summed tier worth behind it.
            assertThat(MarketFactorText.formatPatrols(new PatrolFactor(
                    new PatrolTierFactor(2, 0.25, 0.45),
                    new PatrolTierFactor(1, 0.5, 0.45),
                    new PatrolTierFactor(0, 1.0, 0.0),
                    0.1)))
                .isEqualTo("3 / 900 (-10%)");
        }
    }

    @Nested
    class FormatPatrolTier {

        @Test
        void formatPatrolTierStatesWhatOnePatrolCountsForAndWhatTheTierBecame() {
            // The tier restates no cut: all three took the one the garrison's line above states, and
            // repeating it per tier would read as three separate deductions.
            assertThat(MarketFactorText.formatPatrolTier(new PatrolTierFactor(2, 0.25, 0.45)))
                .isEqualTo("0.25 / 450");
        }
    }
}
