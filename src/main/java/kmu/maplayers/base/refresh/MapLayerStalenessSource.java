package kmu.maplayers.base.refresh;

/**
 * What one map layer decides has gone stale since it was last asked, and the refreshes it
 * raises for that.
 *
 * <p>Some of the changes a drawn map depends on come with no engine event at all - a gate
 * activating, a system cut off, an AI faction founding a colony in a system already drawn -
 * so the only way to find them is to re-read the sector and diff it against the last read.
 * What to re-read, what counts as a change, and which refresh a change earns are all
 * answers only the layer's own model holds, so {@link MapLayerSectorWatcher} supplies
 * the cadence and asks for the rest through here.
 *
 * <p>Each call is diffed against the one before it, so an implementation carries its own
 * baselines rather than being handed the previous read. The first call has nothing to diff
 * against and so marks nothing; it only seeds those baselines.
 */
public interface MapLayerStalenessSource {

    /**
     * Re-reads whatever this layer decides staleness from, marks stale everything that moved
     * since the previous call, and records the new baselines for the next one.
     *
     * <p>Called on the campaign thread on a coarse interval rather than per frame, so one
     * call may walk the whole sector.
     */
    void markChangesSinceLastPoll();
}
