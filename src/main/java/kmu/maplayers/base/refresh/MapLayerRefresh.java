package kmu.maplayers.base.refresh;

import kmu.maplayers.base.installation.MapLayerInstallations;

/**
 * Raises a signal on the running sector's {@link MapLayerRefreshBoard}, for a producer that holds no
 * sector.
 *
 * <p>A board is one sector's, since every signal on it is a fact about one sector - but a producer
 * driven by a settings change is handed no sector to ask for, a control on a screen naming none.
 * Such a caller resolves the live sector's installation, which is the one place a global read stands
 * in for a sector nobody passed down; a caller that does hold a sector, or the installation over
 * one, reaches its board directly rather than through here, so an event in one sector cannot mark
 * another's cache stale.
 *
 * <p>Raising is all that is left here. Every consumer folding a revision, and every producer that
 * can name the system it marked, holds the installation it means and reads that board, so a
 * resolution onto the running sector would be a way of asking the wrong one.
 *
 * <p>Final class with a private constructor: a resolution, no instances.
 */
public final class MapLayerRefresh {

    private MapLayerRefresh() {
        // resolution onto the live sector's board, no instances.
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

    // The live sector's board, or the detached installation's where no sector has the machinery
    // installed - the overlay being behind a switch a player can leave off, which is an ordinary
    // state rather than a fault to branch on here.
    private static MapLayerRefreshBoard resolveLiveBoard() {
        return MapLayerInstallations
            .resolveInstallationForLiveSector()
            .resolveRefreshBoard();
    }
}
