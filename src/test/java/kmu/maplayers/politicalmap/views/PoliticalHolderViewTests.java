package kmu.maplayers.politicalmap.views;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.cache.revisions.RevisionStamp;
import kmlib.starsector.factions.alliances.AllianceRecord;
import kmlib.starsector.factions.alliances.FactionAlliances;

import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.ownermap.owners.holders.base.HolderCategories;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.preferences.OwnerNameFormatChoice;
import kmu.maplayers.politicalmap.refresh.PoliticalMapRefreshSignal;
import kmu.mods.nexerelin.NexerelinAlliances;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kmu.starsector.SectorFactionFixtures.stubNamedFaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins what every view of the political map answers alike: the alliance set's revision, the live
 * alliance set as who stands together, the holder categories, and the faction reading.
 *
 * <p>Read off the interface's own defaults rather than off a concrete view, so each is pinned once.
 * A view's own suite then pins only what that view decides.
 */
final class PoliticalHolderViewTests {

    // The live alliance set a case poses: Hegemony and Persean standing together, Tri-Tachyon alone.
    private static final FactionAlliances SAMPLED_ALLIANCES = FactionAlliances.buildFrom(List.of(
        new AllianceRecord("pact", "Pact", List.of("hegemony", "persean"))));

    // The interface with every default answering for real and nothing else stated, so a case reads
    // the default alone.
    private final PoliticalHolderView viewMock = mock(PoliticalHolderView.class, CALLS_REAL_METHODS);

    @Nested
    class ReadContentRevisions {

        // A membership change must move the revisions - that is what repaints the alliances view's
        // fills and every view's bands without a reload.
        @Test
        void stampsTheAllianceRevision() {

            // ARRANGE
            var board = new MapLayerRefreshBoard();
            var expectedRevisions = RevisionStamp.of(1);

            board.requestRefresh(PoliticalMapRefreshSignal.ALLIANCES);

            // ACT
            var actualRevisions = viewMock.readContentRevisions(board);

            // ASSERT
            assertThat(actualRevisions)
                .isEqualTo(expectedRevisions);
        }

        // A Mute/Desaturate flip is not stamped here. The bake samples the alliances view's
        // non-allied recede with every other preference and compares the values, so a flip that
        // leaves the recede where it was must cost nothing.
        @Test
        void standsStillWhenTheRecedeStyleRevisionMoves() {

            // ARRANGE
            var board = new MapLayerRefreshBoard();
            var expectedRevisions = RevisionStamp.of(0);

            board.requestRefresh(MapLayerCommonRefreshSignal.RECEDE_STYLE);

            // ACT
            var actualRevisions = viewMock.readContentRevisions(board);

            // ASSERT
            assertThat(actualRevisions)
                .isEqualTo(expectedRevisions);
        }

        // Only the alliance set is stamped, so a signal no view renders its content from leaves the
        // revisions fixed rather than churning the view.
        @Test
        void standsStillWhenTheGeometryRevisionMoves() {

            // ARRANGE
            var board = new MapLayerRefreshBoard();
            var expectedRevisions = RevisionStamp.of(0);

            board.requestRefresh(MapLayerCommonRefreshSignal.GEOMETRY);

            // ACT
            var actualRevisions = viewMock.readContentRevisions(board);

            // ASSERT
            assertThat(actualRevisions)
                .isEqualTo(expectedRevisions);
        }

        // One stateless view answers for every sector, so the board handed in is the only thing
        // telling two sectors' asks apart. An alliance formed under one sector must move that
        // sector's revisions and leave the other's exactly where they were - a view reading an
        // ambient board instead would repaint whichever sector happened to be running.
        @Test
        void readsTheBoardItIsHandedRatherThanAnother() {

            // ARRANGE
            var board = new MapLayerRefreshBoard();
            var otherBoard = new MapLayerRefreshBoard();
            var expectedRevisions = RevisionStamp.of(1);
            var expectedOtherRevisions = RevisionStamp.of(0);

            board.requestRefresh(PoliticalMapRefreshSignal.ALLIANCES);

            // ACT
            var actualRevisions = viewMock.readContentRevisions(board);
            var actualOtherRevisions = viewMock.readContentRevisions(otherBoard);

            // ASSERT
            assertThat(actualRevisions)
                .isEqualTo(expectedRevisions);
            assertThat(actualOtherRevisions)
                .isEqualTo(expectedOtherRevisions);
        }
    }

    @Nested
    class ResolveContestAffiliation {

        // Who stands together in a contest is the live alliance set whatever a view paints as one
        // bloc: allies painted apart still read as allies.
        @Test
        void readsTheLiveAllianceSet() {

            // ARRANGE
            try (var alliancesMock = mockStatic(NexerelinAlliances.class)) {

                alliancesMock
                    .when(NexerelinAlliances::resolveFactionAlliances)
                    .thenReturn(SAMPLED_ALLIANCES);

                // ACT
                var affiliation = viewMock.resolveContestAffiliation();

                // ASSERT
                assertThat(affiliation.areOwnersAllied("hegemony", "persean"))
                    .isTrue();
                assertThat(affiliation.areOwnersAllied("hegemony", "tritachyon"))
                    .isFalse();
            }
        }
    }

    @Nested
    class ResolveCategories {

        @Test
        void declaresTheHolderCategories() {

            // ARRANGE
            var expectedCategories = HolderCategories.INSTANCE;

            // ACT
            var actualCategories = viewMock.resolveCategories();

            // ASSERT
            assertThat(actualCategories)
                .isSameAs(expectedCategories);
        }
    }

    @Nested
    class ResolveOwnerReading {

        // One answer read back, enough to show the reading is the faction reading over the handed
        // sector. The rest of what it answers is pinned by that reading's own suite.
        @Test
        void namesABlocByItsFactionOffTheHandedSector() {

            // ARRANGE
            var sectorMock = mock(SectorAPI.class);
            var expectedName = "Hegemony";

            stubNamedFaction(sectorMock, "hegemony", "Hegemony");

            // ACT
            var actualName = viewMock
                .resolveOwnerReading(sectorMock, HolderGrouping.identity())
                .resolveName("hegemony", OwnerNameFormatChoice.SHORT);

            // ASSERT
            assertThat(actualName)
                .isEqualTo(expectedName);
        }
    }
}
