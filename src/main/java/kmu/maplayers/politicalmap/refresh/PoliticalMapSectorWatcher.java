package kmu.maplayers.politicalmap.refresh;

import kmu.maplayers.base.refresh.MapLayerSectorWatcher;

/**
 * The political map's staleness poll, as a script class of its own so it is installed and cleared
 * on the political map's timetable and no other layer's.
 *
 * <p>The engine removes transient scripts by exact class, so a watcher class shared with another
 * layer would let one layer's install or removal evict the other's poll.
 */
public final class PoliticalMapSectorWatcher extends MapLayerSectorWatcher {

    /**
     * @param stalenessSource the political map's reading of what changed since the last poll
     */
    public PoliticalMapSectorWatcher(PoliticalMapStalenessSource stalenessSource) {
        super(stalenessSource);
    }
}
