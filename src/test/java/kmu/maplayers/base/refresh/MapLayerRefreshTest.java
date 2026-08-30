package kmu.maplayers.base.refresh;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.installation.MapLayerInstallations;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins what the facade adds over a board, which is the resolution and nothing else: every call
 * lands on the running sector's board rather than on another sector's, and a caller reaching it
 * with no game loaded still has somewhere to raise a signal.
 *
 * <p>The board's own contract - a counter per signal, a set that drains once - is
 * {@link MapLayerRefreshBoardTest}'s. Everything here is asserted through an installation's board
 * rather than through a second facade call, so a resolution answering consistently off the wrong
 * sector could not pass.
 *
 * <p>The index is process-wide, so every case starts from a cleared one.
 */
class MapLayerRefreshTest {

    private final SectorAPI liveSectorMock = mock(SectorAPI.class);
    private final SectorAPI otherSectorMock = mock(SectorAPI.class);

    @BeforeEach
    void clearEveryInstallation() {
        MapLayerInstallations.disposeEveryInstallation();
    }

    @Nested
    class GetRevision {

        @Test
        void getRevisionReadsTheRunningSectorsBoard() {

            var liveInstallation = MapLayerInstallations.installMachineryOn(liveSectorMock);
            var otherInstallation = MapLayerInstallations.installMachineryOn(otherSectorMock);

            liveInstallation
                .resolveRefreshBoard()
                .requestRefresh(MapLayerCommonRefreshSignal.FILTER);
            otherInstallation
                .resolveRefreshBoard()
                .requestRefresh(MapLayerCommonRefreshSignal.FILTER);
            otherInstallation
                .resolveRefreshBoard()
                .requestRefresh(MapLayerCommonRefreshSignal.FILTER);

            assertThat(readThroughLiveSector(() ->
                    MapLayerRefresh.getRevision(MapLayerCommonRefreshSignal.FILTER)))
                .isEqualTo(1);
        }
    }

    @Nested
    class RequestRefresh {

        @Test
        void requestRefreshRaisesTheSignalOnTheRunningSectorsBoardAlone() {
            // The producers that land here are settings changes and sidebar toggles, which name no
            // sector - so a raise reaching a sector the player is not in would rebuild an overlay
            // nothing is drawing while leaving the one on screen stale.
            var liveInstallation = MapLayerInstallations.installMachineryOn(liveSectorMock);
            var otherInstallation = MapLayerInstallations.installMachineryOn(otherSectorMock);

            raiseThroughLiveSector(() ->
                MapLayerRefresh.requestRefresh(MapLayerCommonRefreshSignal.GEOMETRY));

            assertThat(liveInstallation
                    .resolveRefreshBoard()
                    .getRevision(MapLayerCommonRefreshSignal.GEOMETRY))
                .isEqualTo(1);

            assertThat(otherInstallation
                    .resolveRefreshBoard()
                    .getRevision(MapLayerCommonRefreshSignal.GEOMETRY))
                .isZero();
        }
    }

    @Nested
    class MarkSystemGroupingStale {

        @Test
        void markSystemGroupingStaleQueuesTheSystemOnTheRunningSectorsBoardAlone() {

            var liveInstallation = MapLayerInstallations.installMachineryOn(liveSectorMock);
            var otherInstallation = MapLayerInstallations.installMachineryOn(otherSectorMock);

            raiseThroughLiveSector(() -> MapLayerRefresh.markSystemGroupingStale("sys"));

            assertThat(liveInstallation.resolveRefreshBoard().drainStaleGroupingSystemIds())
                .containsExactly("sys");
            assertThat(otherInstallation.resolveRefreshBoard().drainStaleGroupingSystemIds())
                .isEmpty();
        }

        @Test
        void markSystemGroupingStaleQueuesOnTheDetachedBoardWithNoGameLoaded() {
            // The overlay sits behind a switch a player can leave off, and a settings toggle is
            // reachable with no game at all - so a caller arriving here without a sector has to
            // find a board rather than a fault.
            //
            // Drained first: the detached board is nobody's sector and is therefore never replaced,
            // so any other suite driving a seam with no game loaded has been raising signals on
            // this same one.
            var detachedBoard = MapLayerInstallations
                .resolveInstallationFor(liveSectorMock)
                .resolveRefreshBoard();

            detachedBoard.drainStaleGroupingSystemIds();

            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                MapLayerRefresh.markSystemGroupingStale("sys");
            }

            assertThat(detachedBoard.drainStaleGroupingSystemIds())
                .containsExactly("sys");
        }
    }

    @Nested
    class DrainStaleGroupingSystemIds {

        @Test
        void drainStaleGroupingSystemIdsTakesTheRunningSectorsQueue() {

            var liveInstallation = MapLayerInstallations.installMachineryOn(liveSectorMock);
            var otherInstallation = MapLayerInstallations.installMachineryOn(otherSectorMock);

            liveInstallation.resolveRefreshBoard().markSystemGroupingStale("live");
            otherInstallation.resolveRefreshBoard().markSystemGroupingStale("other");

            assertThat(readThroughLiveSector(MapLayerRefresh::drainStaleGroupingSystemIds))
                .containsExactly("live");
            assertThat(otherInstallation.resolveRefreshBoard().drainStaleGroupingSystemIds())
                .containsExactly("other");
        }
    }

    // Reads one answer back through the facade as the game would drive it: with a sector loaded,
    // which is the whole of what this facade adds and the one thing vanilla's own seams cannot
    // name.
    private <T> T readThroughLiveSector(Supplier<T> readThroughFacade) {

        try (var globalMock = mockStatic(Global.class)) {

            globalMock
                .when(Global::getSector)
                .thenReturn(liveSectorMock);

            return readThroughFacade.get();
        }
    }

    // The same, for the calls that answer nothing and are read back off the board instead.
    private void raiseThroughLiveSector(Runnable callThroughFacade) {

        try (var globalMock = mockStatic(Global.class)) {

            globalMock
                .when(Global::getSector)
                .thenReturn(liveSectorMock);

            callThroughFacade.run();
        }
    }
}
