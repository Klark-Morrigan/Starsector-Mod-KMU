package kmu.maplayers.base.visibility.colonies;

import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.markets.colonies.Colonies;
import kmlib.starsector.markets.colonies.Colony;
import kmlib.testfixtures.starsector.markets.colonies.ColonyMarketFixture;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins what a reader holding an id and no colony is told about the place behind it: the fold off a
 * colony set, and what an id the fold never met reads as. Each member's cases live in a
 * {@link Nested} group so the suite reports as a per-member tree.
 *
 * <p>The unmet id is the case worth pinning rather than the plain one. A claim row carries the id
 * of the market it was scored from and nothing of the place behind it, so an account listing a row
 * the fold never met has to go on reading rather than fail - and it must read as the kind that
 * says nothing, not as one that calls a living colony a ruin.
 */
final class ColonyKindLookupTest {

    private static final String DERELICT_ID = "sentinel_gantries";
    private static final String NEIGHBOUR_ID = "jangala";

    @Nested
    class ReadKindsIn {

        @Test
        void names_each_colony_of_the_place_by_the_kind_it_is() {

            var derelict = nameColony(ColonyMarketFixture.buildDerelictStation(), DERELICT_ID);
            var neighbour = nameColony(
                ColonyMarketFixture.buildVisibleColony("hegemony"),
                NEIGHBOUR_ID);

            var lookup = ColonyKindLookup.readKindsIn(
                new Colonies(List.of(
                    new Colony(derelict, false),
                    new Colony(neighbour, true))),
                ColonyKnowledge.observingUnderTheFog());

            assertThat(lookup.readKindOf(DERELICT_ID))
                .isEqualTo(ColonyKind.SPACE_DERELICT);
            assertThat(lookup.readKindOf(NEIGHBOUR_ID))
                .isEqualTo(ColonyKind.COLONY);
        }

        @Test
        void folds_every_colony_present_rather_than_the_ones_a_projection_admits() {
            // What the lookup answers is what a place is, not what may be said of it. A reader
            // asking has already decided which rows it lists, so narrowing here would leave a
            // listed row unanswered for reasons that reader had nothing to do with.
            var undiscoveredDerelict = nameColony(
                ColonyMarketFixture.buildUndiscoveredDerelictStation(),
                DERELICT_ID);

            var lookup = ColonyKindLookup.readKindsIn(
                new Colonies(List.of(new Colony(undiscoveredDerelict, false))),
                ColonyKnowledge.observingUnderTheFog());

            assertThat(lookup.readKindOf(DERELICT_ID))
                .isEqualTo(ColonyKind.SPACE_DERELICT);
        }

        @Test
        void passes_over_a_colony_with_no_id_to_key_it_on() {
            // Nothing to pair a row with, so nothing is stored - which is not the same as storing
            // an entry under nothing and handing one colony's kind to every other unnamed one.
            var lookup = ColonyKindLookup.readKindsIn(
                new Colonies(List.of(new Colony(mock(MarketAPI.class), true))),
                ColonyKnowledge.observingUnderTheFog());

            assertThat(lookup.kindByColonyId())
                .isEmpty();
        }

        @Test
        void reads_a_place_that_could_not_be_walked_as_nothing_known() {

            assertThat(ColonyKindLookup.readKindsIn(null, ColonyKnowledge.observingUnderTheFog()))
                .isEqualTo(ColonyKindLookup.NONE);
        }

        @Test
        void reads_an_unstated_classification_as_nothing_known() {
            // A reader with no pass behind it has nothing to classify by, and inventing a kind for
            // every colony would have a box call places what nobody worked out.
            assertThat(ColonyKindLookup.readKindsIn(Colonies.NONE, null))
                .isEqualTo(ColonyKindLookup.NONE);
        }
    }

    @Nested
    class ReadKindOf {

        @Test
        void reads_an_id_the_fold_never_met_as_an_ordinary_colony() {
            // The direction a classification errs in everywhere else: overstating a place by one
            // settlement rather than calling a living colony a ruin.
            assertThat(ColonyKindLookup.NONE.readKindOf(DERELICT_ID))
                .isEqualTo(ColonyKind.COLONY);
        }
    }

    @Nested
    class Construct {

        @Test
        void reads_an_absent_map_as_nothing_known() {
            assertThat(new ColonyKindLookup(null).kindByColonyId())
                .isEmpty();
        }

        @Test
        void keeps_the_kinds_it_was_built_with_when_the_source_map_changes_later() {

            var kindByColonyId = new HashMap<String, ColonyKind>();
            kindByColonyId.put(DERELICT_ID, ColonyKind.SPACE_DERELICT);

            var lookup = new ColonyKindLookup(kindByColonyId);

            kindByColonyId.clear();

            assertThat(lookup.readKindOf(DERELICT_ID))
                .isEqualTo(ColonyKind.SPACE_DERELICT);
        }

        @Test
        void rejects_an_attempt_to_change_the_kinds() {
            // Folded once for a whole box and read by every line in it, so one line able to change
            // it would be reclassifying the system underneath the others.
            var lookup = new ColonyKindLookup(
                Map.of(DERELICT_ID, ColonyKind.SPACE_DERELICT));

            assertThatThrownBy(() -> lookup.kindByColonyId().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // The id a row is paired back to its colony by. Colonies are built without one because almost
    // nothing reads it, and every unnamed colony would otherwise share one entry.
    private static MarketAPI nameColony(MarketAPI colony, String colonyId) {

        when(colony.getId())
            .thenReturn(colonyId);

        return colony;
    }
}
