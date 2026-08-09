package kmu.maplayers.politicalmap.base.politics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the per-bloc claim stats fold: the claim count and the market-size total build up
 * independently of one another, since a system contributes a claim, a colony, both, or neither.
 */
final class ClaimStatsTest {

    @Nested
    class AddClaim {

        @Test
        void addClaimCountsTheClaimAndLeavesMarketSizeAlone() {
            // A claimed system adds a claim only - market size is summed over the systems the bloc
            // holds colonies in, which this fold knows nothing about.
            assertThat(ClaimStats.EMPTY.addClaim())
                .isEqualTo(new ClaimStats(1, 0));
        }

        @Test
        void addClaimAccumulatesAcrossSuccessiveSystems() {
            // Two claimed systems fold in one after another, leaving two claims.
            assertThat(ClaimStats.EMPTY.addClaim().addClaim())
                .isEqualTo(new ClaimStats(2, 0));
        }
    }

    @Nested
    class AddMarketSize {

        @Test
        void addMarketSizeSumsTheSizeAndLeavesTheClaimCountAlone() {
            // A system the bloc holds a colony in but does not claim adds size without a claim - the
            // ordinary case, since a claimant usually does not hold what it claims.
            assertThat(ClaimStats.EMPTY.addMarketSize(5))
                .isEqualTo(new ClaimStats(0, 5));
        }

        @Test
        void addMarketSizeAccumulatesAcrossSuccessiveSystems() {
            // Colonies in two systems sum into one whole-sector total.
            assertThat(ClaimStats.EMPTY.addMarketSize(5).addMarketSize(3))
                .isEqualTo(new ClaimStats(0, 8));
        }

        @Test
        void addMarketSizeCombinesWithAClaimOnTheSameBloc()  {
            // The two folds compose in either order on one bloc: a claim in one system and a colony
            // in another leave both metrics set.
            assertThat(ClaimStats.EMPTY.addClaim().addMarketSize(4))
                .isEqualTo(new ClaimStats(1, 4));
        }
    }
}
