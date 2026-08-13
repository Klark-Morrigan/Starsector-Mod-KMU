package kmu.maplayers.politicalmap.base.dominance;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link SystemDominance}'s four-level rule: combined weight, then
 * heaviest single market, then planet weight, then a tie-break over the tied
 * ids (lowest id by default, or the supplied comparator). Each test forces a
 * tie on every earlier level so it isolates exactly one tie-break, and lists
 * the higher faction id first so the winner is shown to come from the rule, not
 * from map iteration order.
 */
class SystemDominanceTest {

    // How many markets each hand-built footprint stands for. Fixed, because the rule compares
    // weights alone: a count that varied between fixtures would read as though it mattered.
    private static final int SINGLE_MARKET = 1;

    @Nested
    class ResolveDominantFactionId {

        @Test
        void returnsNullForNoOwnedMarkets() {
            assertThat(SystemDominance.resolveDominantFactionId(Map.of()))
                .isNull();
        }

        @Test
        void picksTheOnlyFactionPresent() {

            var footprints = listOrderedFootprints(
                "hegemony",
                buildWeightedFootprint(7, 5, 5));

            assertThat(SystemDominance.resolveDominantFactionId(footprints))
                .isEqualTo("hegemony");
        }

        @Test
        void picksLargerCombinedWeightOverHeavierSingleMarketAndPlanets() {
            // Tritachyon holds the heavier single market and all the planet
            // weight, but combined weight is the top level, so the larger sum
            // wins.
            var footprints = listOrderedFootprints(
                "tritachyon",
                buildWeightedFootprint(6, 6, 6),
                "hegemony",
                buildWeightedFootprint(8, 4, 0));

            assertThat(SystemDominance.resolveDominantFactionId(footprints))
                .isEqualTo("hegemony");
        }

        @Test
        void breaksCombinedWeightTieByHeaviestSingleMarket() {
            // Equal totals: the faction holding the single heaviest market wins.
            var footprints = listOrderedFootprints(
                "tritachyon",
                buildWeightedFootprint(9, 4, 0),
                "hegemony",
                buildWeightedFootprint(9, 6, 0));

            assertThat(SystemDominance.resolveDominantFactionId(footprints))
                .isEqualTo("hegemony");
        }

        @Test
        void breaksHeaviestMarketTieByPlanetWeight() {
            // Equal total and equal heaviest market: more weight on planets (vs
            // stations) outranks a footprint leaning on stations.
            var footprints = listOrderedFootprints(
                "tritachyon",
                buildWeightedFootprint(9, 5, 2),
                "hegemony",
                buildWeightedFootprint(9, 5, 7));

            assertThat(SystemDominance.resolveDominantFactionId(footprints))
                .isEqualTo("hegemony");
        }

        @Test
        void breaksFullTieByLowestFactionId() {
            // Identical footprints fall to the lowest faction id, so the result
            // is deterministic and independent of insertion order.
            var footprints = listOrderedFootprints(
                "tritachyon",
                buildWeightedFootprint(9, 5, 5),
                "hegemony",
                buildWeightedFootprint(9, 5, 5));

            assertThat(SystemDominance.resolveDominantFactionId(footprints))
                .isEqualTo("hegemony");
        }

        @Test
        void breaksAFullTieWithTheSuppliedComparator() {
            // With a comparator given, the full tie falls to whichever id it
            // orders first - here reverse order, so the higher id wins, proving
            // the tie-break comes from the comparator and not the natural id order.
            var footprints = listOrderedFootprints(
                "hegemony",
                buildWeightedFootprint(9, 5, 5),
                "tritachyon",
                buildWeightedFootprint(9, 5, 5));

            assertThat(SystemDominance.resolveDominantFactionId(
                    footprints,
                    Comparator.<String>reverseOrder()))
                .isEqualTo("tritachyon");
        }

        @Test
        void leavesTheSuppliedComparatorUnconsultedWhenAWeightLevelDiffers() {
            // A clear winner on combined weight is decided before the tie-break,
            // so a comparator that would throw if consulted proves the rule never
            // reaches it without a full tie.
            var footprints = listOrderedFootprints(
                "tritachyon",
                buildWeightedFootprint(6, 6, 6),
                "hegemony",
                buildWeightedFootprint(8, 4, 0));

            Comparator<String> throwingTieBreak = (left, right) -> {
                throw new AssertionError("tie-break consulted without a full tie");
            };

            assertThat(SystemDominance.resolveDominantFactionId(footprints, throwingTieBreak))
                .isEqualTo("hegemony");
        }
    }

    // A footprint stating only the three weights the rule compares, standing for one held
    // market, so each fixture reads as the levels the tie-break walks and nothing else.
    private static MarketFootprint buildWeightedFootprint(
            int totalWeight,
            int largestMarketWeight,
            int planetWeight) {

        return new MarketFootprint(
            SINGLE_MARKET,
            totalWeight,
            largestMarketWeight,
            planetWeight);
    }

    // Builds the input map preserving insertion order, so a test can list the
    // higher-id faction first and still expect the rule to pick the right one.
    private static Map<String, MarketFootprint> listOrderedFootprints(Object... idsAndFootprints) {

        var footprints = new LinkedHashMap<String, MarketFootprint>();

        for (var i = 0; i < idsAndFootprints.length; i += 2) {

            footprints.put(
                (String) idsAndFootprints[i],
                (MarketFootprint) idsAndFootprints[i + 1]);
        }
        return footprints;
    }
}
