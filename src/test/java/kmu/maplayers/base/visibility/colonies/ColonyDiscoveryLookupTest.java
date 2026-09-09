package kmu.maplayers.base.visibility.colonies;

import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.markets.colonies.Colonies;
import kmlib.starsector.markets.colonies.Colony;
import kmlib.testfixtures.starsector.markets.colonies.ColonyMarketFixture;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins what a reader holding an id and no colony is told about whether the player has found it: the
 * fold off a colony set, and what an id the fold never met reads as. Each member's cases live in a
 * {@link Nested} group so the suite reports as a per-member tree.
 *
 * <p>The unmet id is the case worth pinning rather than the plain one, for the reason its sibling
 * fold's is ({@link ColonyKindLookupTest}): a row the fold never met has to go on reading, and here
 * it must read as found - calling a colony undiscovered is a finding the box has nothing behind.
 */
final class ColonyDiscoveryLookupTest {

    private static final String DERELICT_ID = "sentinel_gantries";
    private static final String NEIGHBOUR_ID = "jangala";

    @Nested
    class ReadDiscoveriesIn {

        @Test
        void holds_the_colonies_of_the_place_the_player_has_yet_to_find() {

            var undiscoveredDerelict = nameColony(
                ColonyMarketFixture.buildUndiscoveredDerelictStation(),
                DERELICT_ID);

            var neighbour = nameColony(
                ColonyMarketFixture.buildVisibleColony("hegemony"),
                NEIGHBOUR_ID);

            var lookup = ColonyDiscoveryLookup.readDiscoveriesIn(new Colonies(List.of(
                new Colony(undiscoveredDerelict, false),
                new Colony(neighbour, true))));

            assertThat(lookup.isDiscoveredColony(DERELICT_ID))
                .isFalse();
            assertThat(lookup.isDiscoveredColony(NEIGHBOUR_ID))
                .isTrue();
        }

        @Test
        void reads_the_entitys_own_flag_rather_than_whether_the_colony_is_concealed() {
            // The two axes are separate everywhere else in the fog, and a box calling a raided
            // pirate base undiscovered would be reporting the wrong one of them.
            var concealedColony = nameColony(
                ColonyMarketFixture.buildFoundConcealedColony("pirates"),
                NEIGHBOUR_ID);

            var lookup = ColonyDiscoveryLookup.readDiscoveriesIn(
                new Colonies(List.of(new Colony(concealedColony, true))));

            assertThat(lookup.isDiscoveredColony(NEIGHBOUR_ID))
                .isTrue();
        }

        @Test
        void passes_over_a_colony_with_no_id_to_key_it_on() {
            // Nothing to pair a row with, so nothing is stored - which is not the same as storing an
            // entry under nothing and calling every other unnamed colony undiscovered.
            var lookup = ColonyDiscoveryLookup.readDiscoveriesIn(
                new Colonies(List.of(new Colony(mock(MarketAPI.class), true))));

            assertThat(lookup.undiscoveredColonyIds())
                .isEmpty();
        }

        @Test
        void reads_a_place_that_could_not_be_walked_as_nothing_known() {

            assertThat(ColonyDiscoveryLookup.readDiscoveriesIn(null))
                .isEqualTo(ColonyDiscoveryLookup.NONE);
        }
    }

    @Nested
    class IsDiscoveredColony {

        @Test
        void reads_an_id_the_fold_never_met_as_found() {
            // The direction that states no finding, a box being unable to call a colony undiscovered on
            // the strength of a fold nobody made.
            assertThat(ColonyDiscoveryLookup.NONE.isDiscoveredColony(DERELICT_ID))
                .isTrue();
        }
    }

    @Nested
    class Construct {

        @Test
        void reads_an_absent_set_as_nothing_known() {
            assertThat(new ColonyDiscoveryLookup(null).undiscoveredColonyIds())
                .isEmpty();
        }

        @Test
        void keeps_the_discoveries_it_was_built_with_when_the_source_set_changes_later() {

            var undiscoveredColonyIds = new HashSet<String>();
            undiscoveredColonyIds.add(DERELICT_ID);

            var lookup = new ColonyDiscoveryLookup(undiscoveredColonyIds);

            undiscoveredColonyIds.clear();

            assertThat(lookup.isDiscoveredColony(DERELICT_ID))
                .isFalse();
        }

        @Test
        void rejects_an_attempt_to_change_the_discoveries() {
            // Folded once for a whole box and read by every line in it, so one line able to change
            // it would be re-reading the system underneath the others.
            var lookup = new ColonyDiscoveryLookup(Set.of(DERELICT_ID));

            assertThatThrownBy(() -> lookup.undiscoveredColonyIds().clear())
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
