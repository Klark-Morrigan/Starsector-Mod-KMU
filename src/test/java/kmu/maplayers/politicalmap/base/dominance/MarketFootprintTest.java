package kmu.maplayers.politicalmap.base.dominance;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link MarketFootprint#merge}, the fold a grouping uses to collapse a bloc's
 * member factions into one footprint: combined and planet weight sum, the heaviest
 * single market takes the larger, and folding {@link MarketFootprint#EMPTY} leaves a
 * footprint unchanged so a lone-faction bloc under the identity grouping is untouched.
 */
class MarketFootprintTest {

    @Nested
    class Merge {

        @Test
        void sumsTotalTakesMaxHeaviestAndSumsPlanetWeight() {
            var left = new MarketFootprint(5, 3, 2);
            var right = new MarketFootprint(4, 6, 1);

            // Total sums to 9 and planet weight to 3, but the heaviest single market
            // is the larger of the two (6), not their sum - a bloc's biggest market
            // is one member's biggest.
            assertThat(left.merge(right)).isEqualTo(new MarketFootprint(9, 6, 3));
        }

        @Test
        void foldingEmptyLeavesTheFootprintUnchanged() {
            var footprint = new MarketFootprint(7, 5, 4);

            // EMPTY is the fold identity from either side, so regrouping a lone
            // faction into its own bloc under the identity grouping is a no-op.
            assertThat(MarketFootprint.EMPTY.merge(footprint)).isEqualTo(footprint);
            assertThat(footprint.merge(MarketFootprint.EMPTY)).isEqualTo(footprint);
        }
    }
}
