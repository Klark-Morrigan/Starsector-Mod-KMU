package kmu.maplayers.base.refresh;

import kmu.maplayers.base.installation.MapLayerInstallations;

import java.util.Set;

/**
 * The running sector's {@link MapLayerRefreshBoard}, reached without holding a sector.
 *
 * <p>A board is one sector's, since every signal on it is a fact about one sector - but a producer
 * driven by a settings change is handed no sector to ask for, a control on a screen naming none.
 * Such a caller resolves the live sector's installation, which is the one place a global read stands
 * in for a sector nobody passed down; a caller that does hold a sector, or the installation over
 * one, reaches its board directly rather than through here, so an event in one sector cannot mark
 * another's cache stale.
 *
 * <p>Final class with a private constructor: a resolution, no instances.
 */
public final class MapLayerRefresh {

    private MapLayerRefresh() {
        // resolution onto the live sector's board, no instances.
    }

    /**
     * @param signal the coarse change to read
     * @return how many times {@code signal} has been raised on the live sector's board
     */
    public static int getRevision(MapLayerRefreshSignal signal) {
        return resolveLiveBoard().getRevision(signal);
    }

    /**
     * Raises {@code signal} on the live sector's board: whatever it names went stale, so every
     * consumer of that sector folding it in rebuilds.
     *
     * @param signal the coarse change that occurred
     */
    public static void requestRefresh(MapLayerRefreshSignal signal) {
        resolveLiveBoard().requestRefresh(signal);
    }

    /**
     * Marks one of the live sector's systems grouping-stale, so the overlay re-derives just it and
     * its neighbours rather than rescanning every system.
     *
     * @param systemId the system whose owner may have changed; null is ignored
     */
    public static void markSystemGroupingStale(String systemId) {
        resolveLiveBoard().markSystemGroupingStale(systemId);
    }

    /**
     * Removes and returns the live sector's systems marked grouping-stale since the last drain, so
     * the plugin processes each staleness once.
     *
     * @return the drained stale system ids; empty when none are pending
     */
    public static Set<String> drainStaleGroupingSystemIds() {
        return resolveLiveBoard().drainStaleGroupingSystemIds();
    }

    // The live sector's board, or the detached installation's where no sector has the machinery
    // installed - the overlay being behind a switch a player can leave off, which is an ordinary
    // state rather than a fault to branch on here.
    private static MapLayerRefreshBoard resolveLiveBoard() {
        return MapLayerInstallations
            .resolveInstallationForLiveSector()
            .resolveRefreshBoard();
    }
}
