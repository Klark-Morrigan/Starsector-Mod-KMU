package kmu.maplayers.politicalmap.dominance;

import kmu.maplayers.ownermap.owners.SpotlitBlocs;
import kmu.maplayers.politicalmap.dominance.FilteredPolitics.SelectedBlocPresence;
import kmu.maplayers.politicalmap.dominance.weighting.MarketFootprint;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static kmu.maplayers.politicalmap.dominance.weighting.MarketFootprintFixtures.buildWeightedFootprint;
import static kmu.maplayers.politicalmap.dominance.weighting.MarketFootprintFixtures.listOrderedFootprints;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link FilteredPolitics}'s pure rules on hand-built footprints and presence sets: the
 * three-way classification that decides how a spotlighted bloc draws in each system - including
 * that a bloc living in a system nothing weighed for it still draws - and the group-key helper the
 * render layer reads to tell a spotlit cell from a receding one. The presence-aware
 * holder assembly - the synthetic key, palette reuse, the contested set, and the real-holder
 * fallback - reads the live economy, so it is covered in {@link FilteredPoliticsIntegrationTest}.
 */
class FilteredPoliticsTest {

    // Nobody barred: every case here poses ordinary factions, so the bar has nothing to say
    // about them and stating it would only obscure what each case is actually about.
    private static final HolderRankingRules OPEN_CONTEST =
        HolderRankingRules.createOpenContestByLowestId();

    @Nested
    class ClassifySelectedBlocPresence {

        @Test
        void returnsAbsentWhenTheSelectedBlocHoldsNothingInTheSystem() {
            // A rival holds the system and the selected bloc owns nothing here, so its real
            // holder draws (receded) rather than the spotlighted bloc.
            var footprints = listOrderedFootprints("hegemony", buildWeightedFootprint(9, 5, 5));

            assertThat(FilteredPolitics.classifySelectedBlocPresence(
                    footprints,
                    Set.of("hegemony"),
                    "tritachyon",
                    OPEN_CONTEST))
                .isEqualTo(SelectedBlocPresence.ABSENT);
        }

        @Test
        void returnsAbsentForAnEmptySystem() {
            // An uninhabited system holds nobody's colony, so the selected bloc is absent there
            // like everywhere it owns nothing.
            assertThat(FilteredPolitics.classifySelectedBlocPresence(
                    Map.of(),
                    Set.of(),
                    "hegemony",
                    OPEN_CONTEST))
                .isEqualTo(SelectedBlocPresence.ABSENT);
        }

        @Test
        void returnsDominatesWhenTheSelectedBlocWinsTheSystem() {

            var footprints = listOrderedFootprints(
                "hegemony",
                buildWeightedFootprint(9, 5, 5),
                "tritachyon",
                buildWeightedFootprint(3, 3, 0));

            assertThat(FilteredPolitics.classifySelectedBlocPresence(
                    footprints,
                    Set.of("hegemony", "tritachyon"),
                    "hegemony",
                    OPEN_CONTEST))
                .isEqualTo(SelectedBlocPresence.DOMINATES);
        }

        @Test
        void returnsPresentButDominatedWhenARivalOutranksTheSelectedBloc() {
            // The selected bloc owns a market but loses the dominance comparison, so it draws
            // contested (hatched) in its own palette rather than ceding the cell to the rival.
            var footprints = listOrderedFootprints(
                "hegemony",
                buildWeightedFootprint(9, 5, 5),
                "tritachyon",
                buildWeightedFootprint(3, 3, 0));

            assertThat(FilteredPolitics.classifySelectedBlocPresence(
                    footprints,
                    Set.of("hegemony", "tritachyon"),
                    "tritachyon",
                    OPEN_CONTEST))
                .isEqualTo(SelectedBlocPresence.PRESENT_BUT_DOMINATED);
        }

        @Test
        void returnsDominatesForTheSoleWeightlessPresence() {
            // A weightless colony still marks presence, and unopposed it wins its system, so the
            // selected bloc dominates a cell it holds alone even at zero weight.
            var footprints = listOrderedFootprints("hegemony", MarketFootprint.EMPTY);

            assertThat(FilteredPolitics.classifySelectedBlocPresence(
                    footprints,
                    Set.of("hegemony"),
                    "hegemony",
                    OPEN_CONTEST))
                .isEqualTo(SelectedBlocPresence.DOMINATES);
        }

        @Test
        void returnsPresentButDominatedForABlocPresentWithNoFootprintBesideAHolder() {
            // The step's own case: the selected bloc's only colony here is one the mechanic never
            // weighed, so it raises no footprint and loses the system - but it lives here, so it
            // draws hatched in its own palette rather than sinking into the rival's receded fill.
            var footprints = listOrderedFootprints("hegemony", buildWeightedFootprint(9, 5, 5));

            assertThat(FilteredPolitics.classifySelectedBlocPresence(
                    footprints,
                    Set.of("hegemony", "tritachyon"),
                    "tritachyon",
                    OPEN_CONTEST))
                .isEqualTo(SelectedBlocPresence.PRESENT_BUT_DOMINATED);
        }

        @Test
        void returnsPresentButDominatedForABlocPresentInASystemNobodyHolds() {
            // Nothing here was weighed at all, so there is no dominant bloc to compare against and
            // the system takes no holder. The spotlit bloc still lives in it, which is exactly what
            // the contested arm records - so the cell draws hatched rather than receding.
            assertThat(FilteredPolitics.classifySelectedBlocPresence(
                    Map.of(),
                    Set.of("pirates"),
                    "pirates",
                    OPEN_CONTEST))
                .isEqualTo(SelectedBlocPresence.PRESENT_BUT_DOMINATED);
        }
    }

    @Nested
    class IsSpotlitBloc {

        @Test
        void returnsFalseForARealFactionId() {
            // The synthetic key uses a sentinel a real ID cannot carry, so no faction is ever
            // mistaken for the spotlighted bloc and wrongly kept at full strength.
            assertThat(SpotlitBlocs.isSpotlitBloc("hegemony"))
                .isFalse();
        }
    }

}
