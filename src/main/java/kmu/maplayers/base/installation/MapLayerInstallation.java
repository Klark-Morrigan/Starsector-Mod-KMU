package kmu.maplayers.base.installation;

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
 * <p>Holds nothing yet. What this type settles is the lifetime - one installation per sector, made
 * and released by {@link MapLayerInstallations} - so that each holder moving in afterwards is a
 * small change against a lifetime that is already right, rather than one change inventing the
 * lifetime and moving a cache into it at once.
 */
public final class MapLayerInstallation {

    // Whether this installation has been released. Kept rather than inferred from an emptied
    // holder, because a caller can still be holding a reference the index has already let go of: a
    // resolution taken at the top of a frame outlives a removal that happens during it.
    private boolean isDisposed;

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
}
