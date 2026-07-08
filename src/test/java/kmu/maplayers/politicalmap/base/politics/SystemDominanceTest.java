package kmu.maplayers.politicalmap.base.politics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link SystemDominance}'s four-level rule: combined weight, then
 * heaviest single market, then planet weight, then the lowest faction id. Each
 * test forces a tie on every earlier level so it isolates exactly one
 * tie-break, and lists the higher faction id first so the winner is shown to
 * come from the rule, not from map iteration order.
 */
class SystemDominanceTest {

    @Nested
    class ResolveDominantFactionId {

        @Test
        void returnsNullForNoOwnedMarkets() {
            assertThat(SystemDominance.resolveDominantFactionId(Map.of())).isNull();
        }

        @Test
        void picksTheOnlyFactionPresent() {
            var footprints = orderedFootprints(
                    "hegemony", new MarketFootprint(7, 5, 5));

            assertThat(SystemDominance.resolveDominantFactionId(footprints))
                    .isEqualTo("hegemony");
        }

        @Test
        void picksLargerCombinedWeightOverHeavierSingleMarketAndPlanets() {
            // Tritachyon holds the heavier single market and all the planet
            // weight, but combined weight is the top level, so the larger sum
            // wins.
            var footprints = orderedFootprints(
                    "tritachyon", new MarketFootprint(6, 6, 6),
                    "hegemony", new MarketFootprint(8, 4, 0));

            assertThat(SystemDominance.resolveDominantFactionId(footprints))
                    .isEqualTo("hegemony");
        }

        @Test
        void breaksCombinedWeightTieByHeaviestSingleMarket() {
            // Equal totals: the faction holding the single heaviest market wins.
            var footprints = orderedFootprints(
                    "tritachyon", new MarketFootprint(9, 4, 0),
                    "hegemony", new MarketFootprint(9, 6, 0));

            assertThat(SystemDominance.resolveDominantFactionId(footprints))
                    .isEqualTo("hegemony");
        }

        @Test
        void breaksHeaviestMarketTieByPlanetWeight() {
            // Equal total and equal heaviest market: more weight on planets (vs
            // stations) outranks a footprint leaning on stations.
            var footprints = orderedFootprints(
                    "tritachyon", new MarketFootprint(9, 5, 2),
                    "hegemony", new MarketFootprint(9, 5, 7));

            assertThat(SystemDominance.resolveDominantFactionId(footprints))
                    .isEqualTo("hegemony");
        }

        @Test
        void breaksFullTieByLowestFactionId() {
            // Identical footprints fall to the lowest faction id, so the result
            // is deterministic and independent of insertion order.
            var footprints = orderedFootprints(
                    "tritachyon", new MarketFootprint(9, 5, 5),
                    "hegemony", new MarketFootprint(9, 5, 5));

            assertThat(SystemDominance.resolveDominantFactionId(footprints))
                    .isEqualTo("hegemony");
        }
    }

    // Builds the input map preserving insertion order, so a test can list the
    // higher-id faction first and still expect the rule to pick the right one.
    private static Map<String, MarketFootprint> orderedFootprints(Object... idsAndFootprints) {
        var footprints = new LinkedHashMap<String, MarketFootprint>();
        for (var i = 0; i < idsAndFootprints.length; i += 2) {
            footprints.put((String) idsAndFootprints[i],
                    (MarketFootprint) idsAndFootprints[i + 1]);
        }
        return footprints;
    }
}
