package kmu.maplayers.politicalmap.base.dominance;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the two totals {@link PatrolFactor} sums out of its tiers rather than storing beside
 * them: the patrol contribution and the plain headcount. Summing rather than storing is
 * what makes a reader adding the tier lines up land on the total the same value reports, so
 * these pin that the sums are over every tier and count an unfielded one as nothing.
 */
class PatrolFactorTest {

    @Nested
    class ComputeContribution {

        @Test
        void sumsEveryTiersContribution() {

            var factor = new PatrolFactor(
                new PatrolTierFactor(2, 0.25, 0.375),
                new PatrolTierFactor(1, 0.5, 0.375),
                new PatrolTierFactor(3, 1.0, 2.25),
                0.25);

            assertThat(factor.computeContribution())
                .isEqualTo(3.0);
        }

        @Test
        void countsAnUnfieldedTierAsNothing() {

            var factor = new PatrolFactor(
                new PatrolTierFactor(0, 0.25, 0.0),
                new PatrolTierFactor(0, 0.5, 0.0),
                new PatrolTierFactor(2, 1.0, 1.5),
                0.25);

            assertThat(factor.computeContribution())
                .isEqualTo(1.5);
        }
    }
}
