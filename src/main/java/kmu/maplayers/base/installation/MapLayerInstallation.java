package kmu.maplayers.base.installation;

import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.base.refresh.MovingSystems;

/**
 * One sector's installed map machinery: what the map layers derive from that sector and remember
 * between frames, made when the layers are installed on it and released when they are removed.
 *
 * <p>Everything the layers draw is derived from one sector, so every holder behind that drawing is
 * a fact about one sector rather than about the process. Two sectors sharing one holder would not
 * merely be unsupported: the caches behind the drawing reconcile by system id, so a system present
 * in both at different positions is not seen to have moved, and each sector keeps the shapes the
 * other cut rather than overwriting them.
 *
 * <p>What this type settles is the lifetime: one installation per sector, made and released by
 * {@link MapLayerInstallations}. Every holder it gathers is emptied by that one release, so a holder
 * needs no discard of its own and no way to be told which sector it is now looking at.
 */
public final class MapLayerInstallation {

    // Whether this installation has been released. Kept rather than inferred from an emptied
    // holder, because a caller can still be holding a reference the index has already let go of: a
    // resolution taken at the top of a frame outlives a removal that happens during it.
    private boolean isDisposed;

    // Where this sector's systems were last seen, and so which of them are drifting rather than
    // sitting still. Made with the installation and released with it, which is what leaves a
    // sector's tracking starting from nothing rather than from the positions the sector before it
    // saw.
    private final MovingSystems movingSystems = new MovingSystems();

    // What went stale in this sector since each consumer last looked. Made with the installation
    // and released with it, which is what leaves a sector's staleness starting from nothing rather
    // than from whatever the sector before it left marked.
    private final MapLayerRefreshBoard refreshBoard = new MapLayerRefreshBoard();

    /**
     * Releases what this installation holds, after which it answers {@link #isDisposed}.
     *
     * <p>Called by {@link MapLayerInstallations} when a sector's machinery is replaced, removed, or
     * discarded on load. Releasing rather than dropping is what keeps a holder with something to
     * hand back - a GL buffer, a registered script - from being left to the collector.
     */
    public void disposeMachinery() {
        isDisposed = true;
    }

    /**
     * @return whether this installation has been released, so a caller holding one it did not just
     *         resolve can tell that the sector behind it has gone rather than drawing through it
     */
    public boolean isDisposed() {
        return isDisposed;
    }

    /**
     * @return the tracker this sector's poll observes hyperspace positions into and its geometry
     *         reads the movers out of, so a system in one sector cannot be judged to have drifted
     *         by what another sector saw
     */
    public MovingSystems resolveMovingSystems() {
        return movingSystems;
    }

    /**
     * @return the board this sector's producers raise refresh signals on and its overlays read them
     *         from, so a change in one sector cannot mark another sector's cache stale
     */
    public MapLayerRefreshBoard resolveRefreshBoard() {
        return refreshBoard;
    }
}
