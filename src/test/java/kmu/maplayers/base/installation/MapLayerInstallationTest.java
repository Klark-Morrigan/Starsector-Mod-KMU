package kmu.maplayers.base.installation;

import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MovableSystemSectorFake;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.base.refresh.MovableSystemSectorFake.FORCED_ONTO_MAP;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what an installation holds: a refresh board and a motion tracker of its own, so that what
 * went stale in one sector is not what any other sector rebuilds for, and so that a system's drift
 * is judged against where its own sector last saw it.
 *
 * <p>Built here rather than resolved through {@link MapLayerInstallations}, since the claim is
 * about the holder itself and not about the index that hands one out.
 */
class MapLayerInstallationTest {

    // The id both staged sectors give their system. Nothing forbids two sectors generating a
    // system under one id, and it is the case a shared holder gets wrong rather than merely
    // draws twice.
    private static final String SHARED_SYSTEM_ID = "a";

    private final MapLayerInstallation installation = new MapLayerInstallation();
    private final MapLayerInstallation otherInstallation = new MapLayerInstallation();

    @Nested
    class ResolveMovingSystems {

        @Test
        void yieldsTheOneTrackerThisInstallationKeeps() {
            // The poll observes into it and the geometry cache reads the movers out of it, each
            // resolving separately - so two resolutions handing back two trackers would leave the
            // cache reading a set nothing ever wrote.
            assertThat(installation.resolveMovingSystems())
                .isSameAs(installation.resolveMovingSystems());
        }

        @Test
        void yieldsATrackerOfItsOwnSoOneSectorsDriftIsNotJudgedAgainstAnothers() {
            // An observation is keyed by system id. Sharing a tracker, the second sector's system
            // would be measured against the position the first sector's system of that id was last
            // seen at - so a system that never moved reads as drifting, and one that did reads as
            // still.
            var sectorFake = new MovableSystemSectorFake(SHARED_SYSTEM_ID);
            var otherSectorFake = new MovableSystemSectorFake(SHARED_SYSTEM_ID);

            sectorFake.observePositionsInto(installation.resolveMovingSystems(), FORCED_ONTO_MAP);
            sectorFake.moveSystemClearOfItsLastPosition();
            sectorFake.observePositionsInto(installation.resolveMovingSystems(), FORCED_ONTO_MAP);

            // The other sector's system sits exactly where it started and never moves.
            otherSectorFake.observePositionsInto(
                otherInstallation.resolveMovingSystems(),
                FORCED_ONTO_MAP);
            otherSectorFake.observePositionsInto(
                otherInstallation.resolveMovingSystems(),
                FORCED_ONTO_MAP);

            assertThat(installation.resolveMovingSystems().getMovingSystemIds())
                .containsExactly(SHARED_SYSTEM_ID);
            assertThat(otherInstallation.resolveMovingSystems().getMovingSystemIds())
                .isEmpty();
        }
    }

    @Nested
    class ResolveRefreshBoard {

        @Test
        void yieldsTheOneBoardThisInstallationKeeps() {
            // A producer and a consumer resolve the board separately, so two resolutions handing
            // back two boards would have every raise land where nothing reads it.
            assertThat(installation.resolveRefreshBoard())
                .isSameAs(installation.resolveRefreshBoard());
        }

        @Test
        void yieldsABoardOfItsOwnSoOneSectorsStaleSystemsAreNotAnothers() {
            // The stale set is bare system ids, and nothing forbids two sectors from generating a
            // system under the same one - so a shared board is where two sectors corrupt each
            // other silently rather than merely draw each other's picture.
            installation.resolveRefreshBoard().markSystemGroupingStale("sys");

            assertThat(otherInstallation.resolveRefreshBoard().drainStaleGroupingSystemIds())
                .isEmpty();
            assertThat(installation.resolveRefreshBoard().drainStaleGroupingSystemIds())
                .containsExactly("sys");
        }

        @Test
        void yieldsABoardOfItsOwnSoOneSectorsRevisionIsNotAnothers() {
            // Shared counters would leave neither sector able to be stale on its own: either
            // sector's change would rebuild both.
            installation.resolveRefreshBoard().requestRefresh(MapLayerCommonRefreshSignal.GEOMETRY);

            assertThat(otherInstallation
                    .resolveRefreshBoard()
                    .getRevision(MapLayerCommonRefreshSignal.GEOMETRY))
                .isZero();

            assertThat(installation
                    .resolveRefreshBoard()
                    .getRevision(MapLayerCommonRefreshSignal.GEOMETRY))
                .isEqualTo(1);
        }
    }
}
