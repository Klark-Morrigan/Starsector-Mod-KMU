package kmu.maplayers.politicalmap.base.dominance;

import kmlib.starsector.entities.EntityMapIcon;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static kmu.maplayers.politicalmap.base.dominance.KnownMarketFootprints.DOMINANCE_WEIGHT_SCALE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how {@link MarketWeightBreakdown} adds its parts up: what the factors are worth
 * together, and the weight that sum lands on once rounded onto the dominance grid.
 *
 * <p>The rounding is the half the read that produces these values cannot pin on its own -
 * every economy-side case lands on an exact grid value - so the fractional readings a factor
 * weight can actually produce are exercised here, on hand-built parts.
 */
class MarketWeightBreakdownTest {

    // A colony whose entity the game marks with no glyph, which is every colony here: what a box leads
    // a name with says nothing about what the parts beneath it add up to.
    private static final Optional<EntityMapIcon> NO_ICON = Optional.empty();

    @Nested
    class ComputeTotalContribution {

        @Test
        void sumsEveryFactorThatRan() {

            var breakdown = new MarketWeightBreakdown(
                "Chicomoztoc",
                NO_ICON,
                false,
                5.0,
                new BaseSizeFactor(4, 4.0, 2.0, 0.5),
                Optional.of(new StationFactor("Fort Ludd", 1.0, 0.0, 0.25, 0.75)),
                Optional.of(new PatrolFactor(
                    new PatrolTierFactor(2, 0.25, 0.375),
                    new PatrolTierFactor(1, 0.5, 0.375),
                    new PatrolTierFactor(0, 1.0, 0.0),
                    0.25)));

            assertThat(breakdown.computeTotalContribution())
                .isEqualTo(3.5);
        }

        @Test
        void countsAFactorThatNeverRanAsNothing() {
            // A market with neither a station nor patrols is worth its base size alone,
            // rather than the two absent factors reading as a zero the sum has to carry.
            var breakdown = buildBaseSizeOnlyBreakdown(2.0);

            assertThat(breakdown.computeTotalContribution())
                .isEqualTo(2.0);
        }
    }

    @Nested
    class ComputeTotalWeight {

        @Test
        void roundsAFractionalSumUpOntoTheGrid() {
            // 0.09375 size points is 93.75 grid units, which the grid has no room for.
            var breakdown = buildBaseSizeOnlyBreakdown(0.09375);

            assertThat(breakdown.computeTotalWeight())
                .isEqualTo(94);
        }

        @Test
        void roundsAFractionalSumDownOntoTheGrid() {

            var breakdown = buildBaseSizeOnlyBreakdown(0.03125);

            assertThat(breakdown.computeTotalWeight())
                .isEqualTo(31);
        }

        @Test
        void roundsOnceOverTheSumRatherThanPerFactor() {
            // Each factor is worth a third of a grid unit - nothing on its own once rounded -
            // yet together they are worth one, which is what rounding the sum rather than the
            // parts preserves.
            var third = 1.0 / 3.0 / DOMINANCE_WEIGHT_SCALE;
            var breakdown = new MarketWeightBreakdown(
                "Chicomoztoc",
                NO_ICON,
                false,
                10.0,
                new BaseSizeFactor(4, 4.0, third, 0.0),
                Optional.of(new StationFactor("Fort Ludd", 1.0, 0.0, 0.0, third)),
                Optional.of(new PatrolFactor(
                    new PatrolTierFactor(1, third, third),
                    new PatrolTierFactor(0, 0.5, 0.0),
                    new PatrolTierFactor(0, 1.0, 0.0),
                    0.0)));

            assertThat(breakdown.computeTotalWeight())
                .isEqualTo(1);
        }

        @Test
        void weighsAMarketWhoseFactorsHoldNothingAtNothing() {
            // A weightless colony still folds into its faction's footprint, marking presence.
            var breakdown = buildBaseSizeOnlyBreakdown(0.0);

            assertThat(breakdown.computeTotalWeight())
                .isZero();
        }
    }

    // A breakdown of a plain colony - no station, no patrols - worth the given size points,
    // for the sums that turn on the base-size factor alone.
    private static MarketWeightBreakdown buildBaseSizeOnlyBreakdown(double contribution) {
        return new MarketWeightBreakdown(
            "Jangala",
            NO_ICON,
            false,
            10.0,
            new BaseSizeFactor(4, 4.0, contribution, 0.0),
            Optional.empty(),
            Optional.empty());
    }
}
