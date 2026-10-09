package kmu.maplayers.politicalmap.views;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.widgets.lists.ListSortModes;

import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.ownermap.owners.OwnerReadingFake;
import kmu.maplayers.ownermap.owners.holders.base.HolderCategories;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderGroupingFixture;
import kmu.maplayers.ownermap.picker.OwnerMetricsFake;
import kmu.maplayers.ownermap.picker.OwnerStatsReadFake;
import kmu.maplayers.ownermap.picker.RankedOwner;
import kmu.maplayers.ownermap.picker.SelectableOwner;
import kmu.maplayers.politicalmap.refresh.PoliticalMapRefreshSignal;
import kmu.mods.nexerelin.NexerelinAlliances;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Pins what every view of the political map answers alike: the alliance set as its one live input,
 * the live alliance set as who stands together, the holder categories, and a picker read over one
 * sampling of the grouping.
 *
 * <p>Read off the interface's own defaults rather than off a concrete view, so each is pinned once.
 * A view's own suite then pins only what that view decides.
 */
final class PoliticalHolderViewTests {

    private static final HolderGrouping SAMPLED_GROUPING =
        HolderGroupingFixture.buildGroupOf("hegemony", "persean");

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
                    .when(NexerelinAlliances::resolveGrouping)
                    .thenReturn(SAMPLED_GROUPING);

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
    class ReadPickerUnderOneGrouping {

        // The vocabulary a case is not about; the shared assembly bundles whatever it is handed.
        private static final ListSortModes<RankedOwner<OwnerMetricsFake>> ANY_MODES =
            new ListSortModes<>(List.of(), null);

        @Test
        void samplesTheGroupingOnce() {
            // The alliances view samples Nexerelin live, so a second sampling could hand the walk and
            // the rows two different memberships.
            doReturn(SAMPLED_GROUPING).when(viewMock).resolveGrouping();
            doReturn(OwnerReadingFake.createAnsweringNothing())
                .when(viewMock)
                .resolveOwnerReading(any(), any());

            viewMock.readPickerUnderOneGrouping(
                mock(SectorAPI.class),
                grouping -> OwnerStatsReadFake.createRowsOnlyFake(Map.<String, OwnerMetricsFake>of()),
                ANY_MODES);

            verify(viewMock, times(1))
                .resolveGrouping();
        }

        @Test
        void readsTheWalkAndNamesTheRowsUnderThatSampling() {
            // The rows come off the walk's numbers, so they are named and gated under the grouping
            // the walk was read under rather than under one of their own.
            var sectorMock = mock(SectorAPI.class);
            var walkedGroupings = new ArrayList<HolderGrouping>();

            doReturn(SAMPLED_GROUPING).when(viewMock).resolveGrouping();
            doReturn(OwnerReadingFake.createNaming(Map.of("pact", "Pact")))
                .when(viewMock)
                .resolveOwnerReading(any(), any());

            var read = viewMock.readPickerUnderOneGrouping(
                sectorMock,
                grouping -> {
                    walkedGroupings.add(grouping);
                    return OwnerStatsReadFake.createRowsOnlyFake(Map.of("pact", OwnerMetricsFake.EMPTY_FAKE));
                },
                ANY_MODES);

            assertThat(walkedGroupings)
                .singleElement()
                .isSameAs(SAMPLED_GROUPING);
            assertThat(read.picker().items())
                .containsExactly(new RankedOwner<>(
                    new SelectableOwner("pact", "Pact", null),
                    OwnerMetricsFake.EMPTY_FAKE));

            verify(viewMock)
                .resolveOwnerReading(same(sectorMock), same(SAMPLED_GROUPING));
            verify(viewMock)
                .resolveSelectableBlocGate(same(SAMPLED_GROUPING));
        }
    }
}
