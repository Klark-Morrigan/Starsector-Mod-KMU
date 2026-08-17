package kmu.maplayers.politicalmap.base.politics;

import kmu.maplayers.politicalmap.base.dominance.MarketFootprint;
import kmu.maplayers.politicalmap.base.politics.FilteredPolitics.SelectedBlocPresence;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static kmu.maplayers.politicalmap.base.dominance.MarketFootprintFixtures.buildWeightedFootprint;
import static kmu.maplayers.politicalmap.base.dominance.MarketFootprintFixtures.listOrderedFootprints;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link FilteredPolitics}'s pure rules on hand-built footprints: the three-way presence
 * classification that decides how a spotlighted bloc draws in each system, and the group-key
 * helper the render layer reads to tell a spotlit cell from a receding one. The presence-aware
 * holder assembly - the synthetic key, palette reuse, the contested set, and the real-holder
 * fallback - reads the live economy, so it is covered in {@link FilteredPoliticsIntegrationTest}.
 */
class FilteredPoliticsTest {

    @Nested
    class ClassifySelectedBlocPresence {

        @Test
        void returnsAbsentWhenTheSelectedBlocHasNoFootprint() {
            // A rival holds the system and the selected bloc owns nothing here, so its real
            // holder draws (receded) rather than the spotlighted bloc.
            var footprints = listOrderedFootprints("hegemony", buildWeightedFootprint(9, 5, 5));

            assertThat(FilteredPolitics.classifySelectedBlocPresence(footprints, "tritachyon"))
                .isEqualTo(SelectedBlocPresence.ABSENT);
        }

        @Test
        void returnsAbsentForEmptyFootprints() {
            // An uninhabited system has no footprint for any bloc, so the selected bloc is
            // absent there like everywhere it owns nothing.
            assertThat(FilteredPolitics.classifySelectedBlocPresence(Map.of(), "hegemony"))
                .isEqualTo(SelectedBlocPresence.ABSENT);
        }

        @Test
        void returnsDominatesWhenTheSelectedBlocWinsTheSystem() {

            var footprints = listOrderedFootprints(
                "hegemony",
                buildWeightedFootprint(9, 5, 5),
                "tritachyon",
                buildWeightedFootprint(3, 3, 0));

            assertThat(FilteredPolitics.classifySelectedBlocPresence(footprints, "hegemony"))
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

            assertThat(FilteredPolitics.classifySelectedBlocPresence(footprints, "tritachyon"))
                .isEqualTo(SelectedBlocPresence.PRESENT_BUT_DOMINATED);
        }

        @Test
        void returnsDominatesForTheSoleWeightlessPresence() {
            // A weightless colony still marks presence, and unopposed it wins its system, so the
            // selected bloc dominates a cell it holds alone even at zero weight.
            var footprints = listOrderedFootprints("hegemony", MarketFootprint.EMPTY);

            assertThat(FilteredPolitics.classifySelectedBlocPresence(footprints, "hegemony"))
                .isEqualTo(SelectedBlocPresence.DOMINATES);
        }
    }

    @Nested
    class IsSpotlitBloc {

        @Test
        void returnsFalseForARealFactionId() {
            // The synthetic key uses a sentinel a real id cannot carry, so no faction is ever
            // mistaken for the spotlighted bloc and wrongly kept at full strength.
            assertThat(FilteredPolitics.isSpotlitBloc("hegemony"))
                .isFalse();
        }
    }

}
