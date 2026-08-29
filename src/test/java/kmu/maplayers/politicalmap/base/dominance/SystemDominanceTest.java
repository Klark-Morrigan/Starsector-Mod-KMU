package kmu.maplayers.politicalmap.base.dominance;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static kmu.maplayers.politicalmap.base.dominance.MarketFootprintFixtures.buildWeightedFootprint;
import static kmu.maplayers.politicalmap.base.dominance.MarketFootprintFixtures.listOrderedFootprints;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link SystemDominance}'s four-level rule: combined weight, then
 * heaviest single market, then planet weight, then a tie-break over the tied
 * ids (lowest id by default, or the supplied comparator). Each test forces a
 * tie on every earlier level so it isolates exactly one tie-break, and lists
 * the higher faction id first so the winner is shown to come from the rule, not
 * from map iteration order.
 *
 * <p>Also pins who is ranked at all: a candidacy bars an id from winning
 * whatever it scored, and lifts where no footprint is a candidate. The barred
 * ids here are hand-named rather than taken from the live rule, so the pure
 * comparison stays free of what any one faction happens to be.
 */
class SystemDominanceTest {

    private static final String PLACEHOLDER = "placeholder";
    private static final String SECOND_PLACEHOLDER = "placeholder-second";

    // Both placeholder ids sort below the scoring factions below, so a case a bar decides cannot
    // be passing on the natural id order instead.
    private static final Predicate<String> BARS_PLACEHOLDERS =
        factionId -> !Set.of(PLACEHOLDER, SECOND_PLACEHOLDER).contains(factionId);

    @Nested
    class ResolveDominantFactionId {

        @Test
        void returnsNullForNoOwnedMarkets() {
            assertThat(SystemDominance.resolveDominantFactionId(Map.of(), BlocCandidacy.NONE_BARRED))
                .isNull();
        }

        @Test
        void picksTheOnlyFactionPresent() {

            var footprints = listOrderedFootprints(
                "hegemony",
                buildWeightedFootprint(7, 5, 5));

            assertThat(SystemDominance.resolveDominantFactionId(footprints, BlocCandidacy.NONE_BARRED))
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

            assertThat(SystemDominance.resolveDominantFactionId(footprints, BlocCandidacy.NONE_BARRED))
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

            assertThat(SystemDominance.resolveDominantFactionId(footprints, BlocCandidacy.NONE_BARRED))
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

            assertThat(SystemDominance.resolveDominantFactionId(footprints, BlocCandidacy.NONE_BARRED))
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

            assertThat(SystemDominance.resolveDominantFactionId(footprints, BlocCandidacy.NONE_BARRED))
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
                    Comparator.<String>reverseOrder(),
                    BlocCandidacy.NONE_BARRED))
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

            assertThat(SystemDominance.resolveDominantFactionId(
                    footprints,
                    throwingTieBreak,
                    BlocCandidacy.NONE_BARRED))
                .isEqualTo("hegemony");
        }

        @Test
        void leavesTheSystemToACandidateOutscoredByABarredHolder() {
            // The bar is on the id and never on the weight, so a barred holder outscoring
            // everything present still loses the system to whoever may actually win it.
            var footprints = listOrderedFootprints(
                PLACEHOLDER,
                buildWeightedFootprint(9, 5, 5),
                "tritachyon",
                buildWeightedFootprint(1, 1, 0));

            assertThat(SystemDominance.resolveDominantFactionId(footprints, BARS_PLACEHOLDERS))
                .isEqualTo("tritachyon");
        }

        @Test
        void leavesTheSystemToACandidateTiedWithABarredHolderAtNought() {
            // Nought against nought, with the barred id both listed first and sorting lower, so
            // it would take the system on either reading were it in the running at all.
            var footprints = listOrderedFootprints(
                PLACEHOLDER,
                buildWeightedFootprint(0, 0, 0),
                "tritachyon",
                buildWeightedFootprint(0, 0, 0));

            assertThat(SystemDominance.resolveDominantFactionId(footprints, BARS_PLACEHOLDERS))
                .isEqualTo("tritachyon");
        }

        @Test
        void picksABarredHolderWhereItIsTheOnlyFootprint() {
            // A holder barred from the contest still holds what nobody contests, so a system it
            // stands alone in keeps exactly the holder it had before any bar existed.
            var footprints = listOrderedFootprints(
                PLACEHOLDER,
                buildWeightedFootprint(4, 4, 0));

            assertThat(SystemDominance.resolveDominantFactionId(footprints, BARS_PLACEHOLDERS))
                .isEqualTo(PLACEHOLDER);
        }

        @Test
        void ranksBarredHoldersAgainstEachOtherWhereNoneIsACandidate() {
            // The reopened ranking is the same four-level rule rather than a fall back to the first
            // entry: the heavier of two barred holders wins, though it is listed second.
            var footprints = listOrderedFootprints(
                SECOND_PLACEHOLDER,
                buildWeightedFootprint(3, 2, 1),
                PLACEHOLDER,
                buildWeightedFootprint(9, 5, 5));

            assertThat(SystemDominance.resolveDominantFactionId(footprints, BARS_PLACEHOLDERS))
                .isEqualTo(PLACEHOLDER);
        }
    }

}
