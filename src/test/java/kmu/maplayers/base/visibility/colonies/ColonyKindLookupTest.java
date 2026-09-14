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
 * Pins what a reader holding an ID and no colony is told about the place behind it: the fold off a
 * colony set, and what an ID the fold never met reads as. Each member's cases live in a
 * {@link Nested} group so the suite reports as a per-member tree.
 *
 * <p>The unmet ID is the case worth pinning rather than the plain one. A claim row carries the ID
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
        void namesEachColonyOfThePlaceByTheKindItIs() {

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
        void foldsEveryColonyPresentRatherThanTheOnesAProjectionAdmits() {
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
        void passesOverAColonyWithNoIdToKeyItOn() {
            // Nothing to pair a row with, so nothing is stored - which is not the same as storing
            // an entry under nothing and handing one colony's kind to every other unnamed one.
            var lookup = ColonyKindLookup.readKindsIn(
                new Colonies(List.of(new Colony(mock(MarketAPI.class), true))),
                ColonyKnowledge.observingUnderTheFog());

            assertThat(lookup.kindByColonyId())
                .isEmpty();
        }

        @Test
        void readsAPlaceThatCouldNotBeWalkedAsNothingKnown() {

            assertThat(ColonyKindLookup.readKindsIn(null, ColonyKnowledge.observingUnderTheFog()))
                .isEqualTo(ColonyKindLookup.NONE);
        }

        @Test
        void readsAnUnstatedClassificationAsNothingKnown() {
            // A reader with no pass behind it has nothing to classify by, and inventing a kind for
            // every colony would have a box call places what nobody worked out.
            assertThat(ColonyKindLookup.readKindsIn(Colonies.NONE, null))
                .isEqualTo(ColonyKindLookup.NONE);
        }
    }

    @Nested
    class ReadKindOf {

        @Test
        void readsAnIdTheFoldNeverMetAsAnOrdinaryColony() {
            // The direction a classification errs in everywhere else: overstating a place by one
            // settlement rather than calling a living colony a ruin.
            assertThat(ColonyKindLookup.NONE.readKindOf(DERELICT_ID))
                .isEqualTo(ColonyKind.COLONY);
        }
    }

    @Nested
    class Construct {

        @Test
        void readsAnAbsentMapAsNothingKnown() {
            assertThat(new ColonyKindLookup(null).kindByColonyId())
                .isEmpty();
        }

        @Test
        void keepsTheKindsItWasBuiltWithWhenTheSourceMapChangesLater() {

            var kindByColonyId = new HashMap<String, ColonyKind>();
            kindByColonyId.put(DERELICT_ID, ColonyKind.SPACE_DERELICT);

            var lookup = new ColonyKindLookup(kindByColonyId);

            kindByColonyId.clear();

            assertThat(lookup.readKindOf(DERELICT_ID))
                .isEqualTo(ColonyKind.SPACE_DERELICT);
        }

        @Test
        void rejectsAnAttemptToChangeTheKinds() {
            // Folded once for a whole box and read by every line in it, so one line able to change
            // it would be reclassifying the system underneath the others.
            var lookup = new ColonyKindLookup(
                Map.of(DERELICT_ID, ColonyKind.SPACE_DERELICT));

            assertThatThrownBy(() -> lookup.kindByColonyId().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // The ID a row is paired back to its colony by. Colonies are built without one because almost
    // nothing reads it, and every unnamed colony would otherwise share one entry.
    private static MarketAPI nameColony(MarketAPI colony, String colonyId) {

        when(colony.getId())
            .thenReturn(colonyId);

        return colony;
    }
}
