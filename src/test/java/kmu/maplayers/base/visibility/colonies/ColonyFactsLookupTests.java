package kmu.maplayers.base.visibility.colonies;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.markets.colonies.Colonies;
import kmlib.starsector.markets.colonies.Colony;
import kmlib.testfixtures.starsector.markets.colonies.ColonyMarketFixture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.visibility.colonies.OpenlyKnownColonyFixture.ACADEMY_ENTITY_ID;
import static kmu.maplayers.base.visibility.colonies.OpenlyKnownColonyFixture.clearRegistrations;
import static kmu.maplayers.base.visibility.colonies.OpenlyKnownColonyFixture.registerTheAcademy;
import static kmu.maplayers.base.visibility.colonies.OpenlyKnownColonyFixture.standOnEntity;

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
 * the fold never met has to go on reading rather than fail - and every answer must be the one that
 * states no finding.
 */
final class ColonyFactsLookupTests {

    // No sector to hand the alliance read: the alliances these cases pose answer alike for any.
    private static final SectorAPI NO_SECTOR = null;

    private static final ColonyKnowledge NO_KNOWLEDGE = null;

    private static final String ACADEMY_ID = "galatia_academy";
    private static final String BASE_ID = "daybreak";
    private static final String DERELICT_ID = "sentinel_gantries";
    private static final String NEIGHBOUR_ID = "jangala";

    @BeforeEach
    void vouchForTheAcademy() {
        registerTheAcademy();
    }

    @AfterEach
    void clearRegisteredEntityIds() {
        clearRegistrations();
    }

    @Nested
    class ReadFactsIn {

        @Test
        void namesEachColonyOfThePlaceByTheKindItIs() {

            var derelict = nameColony(ColonyMarketFixture.buildDerelictStation(), DERELICT_ID);
            var neighbour = nameColony(
                ColonyMarketFixture.buildVisibleColony("hegemony"),
                NEIGHBOUR_ID);

            var lookup = ColonyFactsLookup.readFactsIn(
                new Colonies(List.of(
                    new Colony(derelict, false),
                    new Colony(neighbour, true))),
                ColonyKnowledge.observingUnderTheFog(NO_SECTOR));

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

            var lookup = ColonyFactsLookup.readFactsIn(
                new Colonies(List.of(new Colony(undiscoveredDerelict, false))),
                ColonyKnowledge.observingUnderTheFog(NO_SECTOR));

            assertThat(lookup.readKindOf(DERELICT_ID))
                .isEqualTo(ColonyKind.SPACE_DERELICT);
        }

        @Test
        void holdsTheColoniesOfThePlaceThePlayerHasYetToFind() {

            var undiscoveredDerelict = nameColony(
                ColonyMarketFixture.buildUndiscoveredDerelictStation(),
                DERELICT_ID);

            var neighbour = nameColony(
                ColonyMarketFixture.buildVisibleColony("hegemony"),
                NEIGHBOUR_ID);

            var lookup = ColonyFactsLookup.readFactsIn(
                new Colonies(List.of(
                    new Colony(undiscoveredDerelict, false),
                    new Colony(neighbour, true))),
                NO_KNOWLEDGE);

            assertThat(lookup.isDiscoveredColony(DERELICT_ID))
                .isFalse();
            assertThat(lookup.isDiscoveredColony(NEIGHBOUR_ID))
                .isTrue();
        }

        @Test
        void readsTheEntitysOwnFlagRatherThanWhetherTheColonyIsConcealed() {
            // The two axes are separate everywhere else in the fog, and a box calling a raided
            // pirate base undiscovered would be reporting the wrong one of them.
            var concealedColony = nameColony(
                ColonyMarketFixture.buildFoundConcealedColony("pirates"),
                NEIGHBOUR_ID);

            var lookup = ColonyFactsLookup.readFactsIn(
                new Colonies(List.of(new Colony(concealedColony, true))),
                NO_KNOWLEDGE);

            assertThat(lookup.isDiscoveredColony(NEIGHBOUR_ID))
                .isTrue();
        }

        @Test
        void holdsTheConcealedColoniesOfThePlaceTheSectorOpenlyPointsAt() {

            var academy = standOnEntity(
                nameColony(ColonyMarketFixture.buildFoundConcealedColony("independent"), ACADEMY_ID),
                ACADEMY_ENTITY_ID);

            var base = standOnEntity(
                nameColony(ColonyMarketFixture.buildFoundConcealedColony("pirates"), BASE_ID),
                "station_daybreak");

            var lookup = ColonyFactsLookup.readFactsIn(
                new Colonies(List.of(
                    new Colony(academy, false),
                    new Colony(base, false))),
                NO_KNOWLEDGE);

            assertThat(lookup.isOpenlyKnownColony(ACADEMY_ID))
                .isTrue();
            assertThat(lookup.isOpenlyKnownColony(BASE_ID))
                .isFalse();
        }

        @Test
        void passesOverAColonyHeldInTheOpenOnARegisteredEntity() {
            // The concealment is asked first, so the registry is consulted for the few colonies a
            // finding could ever be made about - and an open colony, which never earns the word,
            // cannot be excused of it.
            var openColony = standOnEntity(
                nameColony(ColonyMarketFixture.buildVisibleColony("independent"), ACADEMY_ID),
                ACADEMY_ENTITY_ID);

            var lookup = ColonyFactsLookup.readFactsIn(
                new Colonies(List.of(new Colony(openColony, true))),
                NO_KNOWLEDGE);

            assertThat(lookup.isOpenlyKnownColony(ACADEMY_ID))
                .isFalse();
        }

        @Test
        void passesOverAConcealedColonyStandingOnNoEntity() {
            // A market whose entity the sector has taken away is still a market a box lists, so the
            // fold answers rather than failing on it.
            var marketMock = nameColony(mock(MarketAPI.class), BASE_ID);

            when(marketMock.isHidden())
                .thenReturn(true);

            var lookup = ColonyFactsLookup.readFactsIn(
                new Colonies(List.of(new Colony(marketMock, false))),
                NO_KNOWLEDGE);

            assertThat(lookup.isOpenlyKnownColony(BASE_ID))
                .isFalse();
        }

        @Test
        void passesOverAColonyWithNoIdToKeyItOn() {
            // Nothing to pair a row with, so nothing is stored - which is not the same as storing
            // an entry under nothing and handing one colony's facts to every other unnamed one.
            var academy = standOnEntity(
                ColonyMarketFixture.buildFoundConcealedColony("independent"),
                ACADEMY_ENTITY_ID);

            when(academy.getId())
                .thenReturn(null);

            var lookup = ColonyFactsLookup.readFactsIn(
                new Colonies(List.of(
                    new Colony(mock(MarketAPI.class), true),
                    new Colony(academy, false))),
                ColonyKnowledge.observingUnderTheFog(NO_SECTOR));

            assertThat(lookup)
                .isEqualTo(ColonyFactsLookup.NONE);
        }

        @Test
        void readsAPlaceThatCouldNotBeWalkedAsNothingKnown() {

            assertThat(ColonyFactsLookup.readFactsIn(null, ColonyKnowledge.observingUnderTheFog(NO_SECTOR)))
                .isEqualTo(ColonyFactsLookup.NONE);
        }

        @Test
        void leavesEveryKindUnstatedWhereNoClassificationIsStated() {
            // A reader with no pass behind it has nothing to classify by, and inventing a kind for
            // every colony would have a box call places what nobody worked out.
            var derelict = nameColony(ColonyMarketFixture.buildDerelictStation(), DERELICT_ID);

            var lookup = ColonyFactsLookup.readFactsIn(
                new Colonies(List.of(new Colony(derelict, false))),
                NO_KNOWLEDGE);

            assertThat(lookup.kindByColonyId())
                .isEmpty();
        }
    }

    @Nested
    class ReadKindOf {

        @Test
        void readsAnIdTheFoldNeverMetAsAnOrdinaryColony() {
            // The direction a classification errs in everywhere else: overstating a place by one
            // settlement rather than calling a living colony a wreck.
            assertThat(ColonyFactsLookup.NONE.readKindOf(DERELICT_ID))
                .isEqualTo(ColonyKind.COLONY);
        }
    }

    @Nested
    class IsDiscoveredColony {

        @Test
        void readsAnIdTheFoldNeverMetAsFound() {
            // The direction that states no finding, a box being unable to call a colony undiscovered
            // on the strength of a fold nobody made.
            assertThat(ColonyFactsLookup.NONE.isDiscoveredColony(DERELICT_ID))
                .isTrue();
        }
    }

    @Nested
    class IsOpenlyKnownColony {

        @Test
        void readsAnIdTheFoldNeverMetAsASecret() {

            assertThat(ColonyFactsLookup.NONE.isOpenlyKnownColony(ACADEMY_ID))
                .isFalse();
        }

        @Test
        void readsARowCarryingNoIdAsASecret() {

            var lookup = new ColonyFactsLookup(Map.of(), Set.of(), Set.of(ACADEMY_ID));

            assertThat(lookup.isOpenlyKnownColony(null))
                .isFalse();
        }
    }

    @Nested
    class Construct {

        @Test
        void readsAbsentFactsAsNothingKnown() {

            assertThat(new ColonyFactsLookup(null, null, null))
                .isEqualTo(ColonyFactsLookup.NONE);
        }

        @Test
        void keepsTheFactsItWasBuiltWithWhenTheSourcesChangeLater() {

            var kindByColonyId = new HashMap<String, ColonyKind>();
            kindByColonyId.put(DERELICT_ID, ColonyKind.SPACE_DERELICT);

            var undiscoveredColonyIds = new HashSet<String>();
            undiscoveredColonyIds.add(DERELICT_ID);

            var openlyKnownColonyIds = new HashSet<String>();
            openlyKnownColonyIds.add(ACADEMY_ID);

            var lookup = new ColonyFactsLookup(kindByColonyId, undiscoveredColonyIds, openlyKnownColonyIds);

            kindByColonyId.clear();
            undiscoveredColonyIds.clear();
            openlyKnownColonyIds.clear();

            assertThat(lookup.readKindOf(DERELICT_ID))
                .isEqualTo(ColonyKind.SPACE_DERELICT);
            assertThat(lookup.isDiscoveredColony(DERELICT_ID))
                .isFalse();
            assertThat(lookup.isOpenlyKnownColony(ACADEMY_ID))
                .isTrue();
        }

        @Test
        void rejectsAnAttemptToChangeTheFacts() {
            // Folded once for a whole box and read by every line in it, so one line able to change
            // it would be re-reading the system underneath the others.
            var lookup = new ColonyFactsLookup(
                Map.of(DERELICT_ID, ColonyKind.SPACE_DERELICT),
                Set.of(DERELICT_ID),
                Set.of(ACADEMY_ID));

            assertThatThrownBy(() -> lookup.kindByColonyId().clear())
                .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> lookup.undiscoveredColonyIds().clear())
                .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> lookup.openlyKnownColonyIds().clear())
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
