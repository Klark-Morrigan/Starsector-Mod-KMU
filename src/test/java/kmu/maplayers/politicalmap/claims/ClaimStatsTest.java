package kmu.maplayers.politicalmap.claims;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the per-bloc claim stats fold: the claim count and the market-size total build up
 * independently of one another, since a system contributes a claim, a colony, both, or neither. Also
 * pins the one thing these metrics answer about the bloc rather than about its numbers - whether its
 * picker row reads back - since that is the layer's own rule and not the picker's.
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

    @Nested
    class IsPaintingNothing {

        @Test
        void isPaintingNothingIsTrueForABlocThatClaimsNothing() {
            // A colony holder that claims nowhere is listed but paints nothing on this layer, so its
            // row reads back rather than sitting at full strength beside the claimants.
            assertThat(new ClaimStats(0, 40).isPaintingNothing())
                .isTrue();
        }

        @Test
        void isPaintingNothingIsFalseForABlocThatClaimsAnything() {
            // One claim is enough: the layer paints it, so the row has something to show.
            assertThat(new ClaimStats(1, 40).isPaintingNothing())
                .isFalse();
        }

        @Test
        void isPaintingNothingReadsTheClaimCountAloneAndNotTheMarketSize() {
            // The rule is the metric the layer paints by, not how big the bloc is: a claimant that
            // holds no colony anywhere still reads at full strength, and a large holder that claims
            // nowhere still reads back.
            assertThat(new ClaimStats(1, 0).isPaintingNothing())
                .isFalse();
            assertThat(new ClaimStats(0, 0).isPaintingNothing())
                .isTrue();
        }
    }
}
