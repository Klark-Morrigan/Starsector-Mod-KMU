package kmu.maplayers.politicalmap.views;

import com.fs.starfarer.api.campaign.SectorAPI;

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
    class GetContentRevision {

        @Test
        void shiftsWhenTheAllianceRevisionMoves() {
            // A membership change must shift the revision - that is what repaints the alliances
            // view's fills and every view's bands without a reload.
            var board = new MapLayerRefreshBoard();
            var before = viewMock.getContentRevision(board);

            board.requestRefresh(PoliticalMapRefreshSignal.ALLIANCES);

            assertThat(viewMock.getContentRevision(board))
                .isNotEqualTo(before);
        }

        @Test
        void standsStillWhenTheRecedeStyleRevisionMoves() {
            // A Mute/Desaturate flip is not folded here. The bake samples the alliances view's
            // non-allied recede with every other preference and folds the values in, so a flip that
            // leaves the recede where it was must cost nothing.
            var board = new MapLayerRefreshBoard();
            var before = viewMock.getContentRevision(board);

            board.requestRefresh(MapLayerCommonRefreshSignal.RECEDE_STYLE);

            assertThat(viewMock.getContentRevision(board))
                .isEqualTo(before);
        }

        @Test
        void standsStillWhenTheGeometryRevisionMoves() {
            // Only the alliance set is folded in, so a signal no view renders its content from leaves
            // the contribution fixed rather than churning the view.
            var board = new MapLayerRefreshBoard();
            var before = viewMock.getContentRevision(board);

            board.requestRefresh(MapLayerCommonRefreshSignal.GEOMETRY);

            assertThat(viewMock.getContentRevision(board))
                .isEqualTo(before);
        }

        @Test
        void foldsTheBoardItIsHandedRatherThanAnother() {
            // One stateless view answers for every sector, so the board handed in is the only thing
            // telling two sectors' asks apart. An alliance formed under one sector must move that
            // sector's number and leave the other's exactly where it was - a view folding an
            // ambient board instead would repaint whichever sector happened to be running.
            var board = new MapLayerRefreshBoard();
            var otherBoard = new MapLayerRefreshBoard();
            var before = viewMock.getContentRevision(board);
            var otherBefore = viewMock.getContentRevision(otherBoard);

            board.requestRefresh(PoliticalMapRefreshSignal.ALLIANCES);

            assertThat(viewMock.getContentRevision(board))
                .isNotEqualTo(before);
            assertThat(viewMock.getContentRevision(otherBoard))
                .isEqualTo(otherBefore);
        }
    }

    @Nested
    class ResolveContestAffiliation {

        @Test
        void readsTheLiveAllianceSet() {
            // Who stands together in a contest is the live alliance set whatever a view paints as one
            // bloc: allies painted apart still read as allies.
            try (var alliancesMock = mockStatic(NexerelinAlliances.class)) {

                alliancesMock
                    .when(NexerelinAlliances::resolveFactionAlliances)
                    .thenReturn(SAMPLED_ALLIANCES);

                var affiliation = viewMock.resolveContestAffiliation();

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

            assertThat(viewMock.resolveCategories())
                .isSameAs(HolderCategories.INSTANCE);
        }
    }

    @Nested
    class ResolveOwnerReading {

        @Test
        void namesABlocByItsFactionOffTheHandedSector() {
            // One answer read back, enough to show the reading is the faction reading over the handed
            // sector. The rest of what it answers is pinned by that reading's own suite.
            var sectorMock = mock(SectorAPI.class);

            stubNamedFaction(sectorMock, "hegemony", "Hegemony");

            assertThat(viewMock
                    .resolveOwnerReading(sectorMock, HolderGrouping.identity())
                    .resolveName("hegemony", OwnerNameFormatChoice.SHORT))
                .isEqualTo("Hegemony");
        }
    }

}
