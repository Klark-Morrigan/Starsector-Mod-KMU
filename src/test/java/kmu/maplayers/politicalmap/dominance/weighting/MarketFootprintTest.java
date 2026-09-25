package kmu.maplayers.politicalmap.dominance.weighting;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the two folds a footprint supports. {@link MarketFootprint#addMarket} banks one
 * market: its weight into the totals and the holding into the count, however little it
 * weighs. {@link MarketFootprint#merge} is the fold a grouping uses to collapse a bloc's
 * member factions into one footprint: market count, combined weight and planet weight
 * sum, the heaviest single market takes the larger, and folding
 * {@link MarketFootprint#EMPTY} leaves a footprint unchanged so a lone-faction bloc under
 * the identity grouping is untouched.
 */
class MarketFootprintTest {

    @Nested
    class AddMarket {

        @Test
        void countsAPlanetMarketAndBanksItsWeightIntoEveryTotal() {
            // A planet market lifts the count to 1 and lands its weight in all three
            // totals - combined, heaviest and planet, the last because it is on a planet.
            assertThat(MarketFootprint.EMPTY.addMarket(5, true))
                .isEqualTo(new MarketFootprint(1, 5, 5, 5));
        }

        @Test
        void countsAStationMarketWhileKeepingItOutOfPlanetWeight() {
            // A station market counts as a holding and lifts the combined and heaviest
            // weight, but planet weight stays at 0 - the rule's tie-break must not see it.
            assertThat(MarketFootprint.EMPTY.addMarket(4, false))
                .isEqualTo(new MarketFootprint(1, 4, 4, 0));
        }

        @Test
        void countsAWeightlessMarketThatAddsNothingToAnyWeight() {
            // A colony worth nothing to the rule - collapsed stability - is still held
            // there, so the count rises to 1 while every weight stays at 0. This is the
            // reading no weight can be divided back into.
            assertThat(MarketFootprint.EMPTY.addMarket(0, true))
                .isEqualTo(new MarketFootprint(1, 0, 0, 0));
        }

        @Test
        void accumulatesSuccessiveMarketsIntoTheCountAndTheWeights() {
            // Two markets fold in one after another: the count reaches 2, the combined
            // weight sums to 9, and the heaviest stays the heavier single market (5).
            assertThat(MarketFootprint.EMPTY.addMarket(5, true).addMarket(4, false))
                .isEqualTo(new MarketFootprint(2, 9, 5, 5));
        }
    }

    @Nested
    class Merge {

        @Test
        void sumsCountAndTotalTakesMaxHeaviestAndSumsPlanetWeight() {

            var left = new MarketFootprint(2, 5, 3, 2);
            var right = new MarketFootprint(3, 4, 6, 1);

            // Count sums to 5, total to 9 and planet weight to 3, but the heaviest single
            // market is the larger of the two (6), not their sum - a bloc's biggest market
            // is one member's biggest.
            assertThat(left.merge(right))
                .isEqualTo(new MarketFootprint(5, 9, 6, 3));
        }

        @Test
        void foldingEmptyLeavesTheFootprintUnchanged() {

            var footprint = new MarketFootprint(3, 7, 5, 4);

            // EMPTY is the fold identity from either side, so regrouping a lone
            // faction into its own bloc under the identity grouping is a no-op.
            assertThat(MarketFootprint.EMPTY.merge(footprint))
                .isEqualTo(footprint);
            assertThat(footprint.merge(MarketFootprint.EMPTY))
                .isEqualTo(footprint);
        }
    }
}
