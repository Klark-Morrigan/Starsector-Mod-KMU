package kmu.politicalmap.domain.politics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link SystemDominance}'s four-level rule: combined size, then largest
 * single market, then planet size, then the lowest faction id. Each test forces
 * a tie on every earlier level so it isolates exactly one tie-break, and lists
 * the higher faction id first so the winner is shown to come from the rule, not
 * from map iteration order.
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
                    "hegemony", new FactionFootprint(7, 5, 5));

            assertThat(SystemDominance.resolveDominantFactionId(footprints))
                    .isEqualTo("hegemony");
        }

        @Test
        void picksLargerCombinedSizeOverBiggerSingleMarketAndPlanets() {
            // Tritachyon holds the bigger single market and all the planet size,
            // but combined size is the top level, so the larger sum wins.
            var footprints = orderedFootprints(
                    "tritachyon", new FactionFootprint(6, 6, 6),
                    "hegemony", new FactionFootprint(8, 4, 0));

            assertThat(SystemDominance.resolveDominantFactionId(footprints))
                    .isEqualTo("hegemony");
        }

        @Test
        void breaksCombinedSizeTieByLargestSingleMarket() {
            // Equal totals: the faction holding the single biggest market wins.
            var footprints = orderedFootprints(
                    "tritachyon", new FactionFootprint(9, 4, 0),
                    "hegemony", new FactionFootprint(9, 6, 0));

            assertThat(SystemDominance.resolveDominantFactionId(footprints))
                    .isEqualTo("hegemony");
        }

        @Test
        void breaksLargestMarketTieByPlanetSize() {
            // Equal total and equal biggest market: more size on planets (vs
            // stations) outranks a footprint leaning on stations.
            var footprints = orderedFootprints(
                    "tritachyon", new FactionFootprint(9, 5, 2),
                    "hegemony", new FactionFootprint(9, 5, 7));

            assertThat(SystemDominance.resolveDominantFactionId(footprints))
                    .isEqualTo("hegemony");
        }

        @Test
        void breaksFullTieByLowestFactionId() {
            // Identical footprints fall to the lowest faction id, so the result
            // is deterministic and independent of insertion order.
            var footprints = orderedFootprints(
                    "tritachyon", new FactionFootprint(9, 5, 5),
                    "hegemony", new FactionFootprint(9, 5, 5));

            assertThat(SystemDominance.resolveDominantFactionId(footprints))
                    .isEqualTo("hegemony");
        }
    }

    // Builds the input map preserving insertion order, so a test can list the
    // higher-id faction first and still expect the rule to pick the right one.
    private static Map<String, FactionFootprint> orderedFootprints(Object... idsAndFootprints) {
        var footprints = new LinkedHashMap<String, FactionFootprint>();
        for (var i = 0; i < idsAndFootprints.length; i += 2) {
            footprints.put((String) idsAndFootprints[i],
                    (FactionFootprint) idsAndFootprints[i + 1]);
        }
        return footprints;
    }
}
