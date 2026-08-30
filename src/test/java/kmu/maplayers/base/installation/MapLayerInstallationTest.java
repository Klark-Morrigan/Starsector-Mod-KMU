package kmu.maplayers.base.installation;

import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what an installation holds: one refresh board of its own, so that what went stale in one
 * sector is not what any other sector rebuilds for.
 *
 * <p>Built here rather than resolved through {@link MapLayerInstallations}, since the claim is
 * about the holder itself and not about the index that hands one out.
 */
class MapLayerInstallationTest {

    private final MapLayerInstallation installation = new MapLayerInstallation();
    private final MapLayerInstallation otherInstallation = new MapLayerInstallation();

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
