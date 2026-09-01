package kmu.maplayers.base.refresh;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.maplayers.base.installation.MapLayerInstallations;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins what the facade adds over a board, which is the resolution and nothing else: a raise lands
 * on the running sector's board rather than on another sector's, and a caller reaching it with no
 * game loaded still has somewhere to raise a signal.
 *
 * <p>The board's own contract - a counter per signal, a set that drains once - is
 * {@link MapLayerRefreshBoardTest}'s. Everything here is asserted through an installation's board
 * rather than through a second facade call, so a resolution answering consistently off the wrong
 * sector could not pass.
 *
 * <p>Two sectors are installed on for every case, one of them running, since a resolution that
 * simply took the only installation there was would satisfy a suite that installed one.
 *
 * <p>The index is process-wide, so every case starts from a cleared one.
 */
class MapLayerRefreshTest {

    private final SectorAPI liveSectorMock = mock(SectorAPI.class);
    private final SectorAPI otherSectorMock = mock(SectorAPI.class);

    private MapLayerInstallation liveInstallation;
    private MapLayerInstallation otherInstallation;

    @BeforeEach
    void installMachineryOnBothSectors() {

        MapLayerInstallations.disposeEveryInstallation();

        liveInstallation = MapLayerInstallations.installMachineryOn(liveSectorMock);
        otherInstallation = MapLayerInstallations.installMachineryOn(otherSectorMock);
    }

    @Nested
    class RequestRefresh {

        @Test
        void requestRefreshRaisesTheSignalOnTheRunningSectorsBoardAlone() {
            // The producers that land here are settings changes and sidebar toggles, which name no
            // sector - so a raise reaching a sector the player is not in would rebuild an overlay
            // nothing is drawing while leaving the one on screen stale.
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

        @Test
        void requestRefreshRaisesOnTheDetachedBoardWithNoGameLoaded() {
            // The overlay sits behind a switch a player can leave off, and a settings toggle is
            // reachable with no game at all - so a caller arriving here without a sector has to
            // find a board rather than a fault.
            //
            // Reached through a sector nothing was installed on: the detached board is nobody's
            // sector and is therefore never replaced, so its count is read as a step rather than as
            // an absolute - any other suite driving a seam with no game loaded has been raising
            // signals on this same one.
            var uninstalledSectorMock = mock(SectorAPI.class);

            var detachedBoard = MapLayerInstallations
                .resolveInstallationFor(uninstalledSectorMock)
                .resolveRefreshBoard();

            var revisionBefore = detachedBoard.getRevision(MapLayerCommonRefreshSignal.GEOMETRY);

            raiseWithNoGameLoaded(() ->
                MapLayerRefresh.requestRefresh(MapLayerCommonRefreshSignal.GEOMETRY));

            assertThat(detachedBoard.getRevision(MapLayerCommonRefreshSignal.GEOMETRY))
                .isEqualTo(revisionBefore + 1);
        }
    }

    // Drives one call through the facade as the game would: with a sector loaded, which is the whole
    // of what this facade adds and the one thing vanilla's own seams cannot name. The raise answers
    // nothing, so what it did is read back off the board.
    private void raiseThroughLiveSector(Runnable callThroughFacade) {

        readWithLoadedSector(liveSectorMock, () -> {
            callThroughFacade.run();
            return null;
        });
    }

    // A raise made with no game at all, which is what a settings toggle outside a campaign is.
    private void raiseWithNoGameLoaded(Runnable callThroughFacade) {

        readWithLoadedSector(null, () -> {
            callThroughFacade.run();
            return null;
        });
    }

    // The one place the running sector is stood up, so no case can differ in how it poses one.
    private static <T> T readWithLoadedSector(
            SectorAPI loadedSector,
            Supplier<T> readThroughFacade) {

        try (var globalMock = mockStatic(Global.class)) {

            globalMock
                .when(Global::getSector)
                .thenReturn(loadedSector);

            return readThroughFacade.get();
        }
    }
}
