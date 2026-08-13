package kmu.maplayers.politicalmap.base.dominance;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the two folds a faction's market contribution supports: {@link
 * FactionMarketContribution#addMarket}, which reads one market into the footprint and the raw
 * market-size total together, and {@link FactionMarketContribution#merge}, which a grouping uses to
 * collapse a bloc's member factions into one contribution. Both keep the dominance footprint and the
 * raw market size travelling together from the single read that produces them.
 */
class FactionMarketContributionTest {

    @Nested
    class AddMarket {

        @Test
        void foldsAPlanetMarketsWeightIntoTheFootprintAndItsSizeIntoTheTotal() {
            // A planet market's weight lands in the footprint (total, heaviest, and planet weight,
            // since it sits on a planet) and is counted there, while its raw colony size lands in
            // the market-size total.
            assertThat(FactionMarketContribution.EMPTY.addMarket(5, true, 3))
                .isEqualTo(new FactionMarketContribution(new MarketFootprint(1, 5, 5, 5), 3));
        }

        @Test
        void keepsAStationMarketOutOfPlanetWeightWhileStillCountingItsSize() {
            // A station market lifts the combined and heaviest weight but not planet weight, and its
            // size still counts - market size is raw colony size, indifferent to planet-versus-station.
            assertThat(FactionMarketContribution.EMPTY.addMarket(4, false, 2))
                .isEqualTo(new FactionMarketContribution(new MarketFootprint(1, 4, 4, 0), 2));
        }

        @Test
        void accumulatesSuccessiveMarketsIntoBothTheFootprintAndTheSizeTotal() {
            // Two markets fold in one after another: the footprint counts both, sums and takes the
            // heavier single market, and the raw sizes sum.
            assertThat(FactionMarketContribution.EMPTY.addMarket(5, true, 3).addMarket(4, false, 2))
                .isEqualTo(new FactionMarketContribution(new MarketFootprint(2, 9, 5, 5), 5));
        }
    }

    @Nested
    class Merge {

        @Test
        void mergesTheFootprintsAndSumsTheMarketSizes() {

            var left = new FactionMarketContribution(new MarketFootprint(2, 5, 3, 2), 6);
            var right = new FactionMarketContribution(new MarketFootprint(3, 4, 6, 1), 4);

            // The footprints fold as MarketFootprint.merge defines (market count, total and planet
            // weight sum, the heaviest single market takes the larger), and the raw market sizes sum.
            assertThat(left.merge(right))
                .isEqualTo(new FactionMarketContribution(new MarketFootprint(5, 9, 6, 3), 10));
        }

        @Test
        void foldingEmptyLeavesTheContributionUnchanged() {

            var contribution = new FactionMarketContribution(new MarketFootprint(3, 7, 5, 4), 8);

            // EMPTY is the fold identity from either side, so regrouping a lone faction into its own
            // bloc under the identity grouping is a no-op for both the footprint and the size total.
            assertThat(FactionMarketContribution.EMPTY.merge(contribution))
                .isEqualTo(contribution);
            assertThat(contribution.merge(FactionMarketContribution.EMPTY))
                .isEqualTo(contribution);
        }
    }
}
