package kmu.maplayers.base.ribbon;

import kmlib.math.geometry.RingPath;
import kmlib.starsector.systems.SystemKey;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The band paths traced inside one build's cell shapes, kept for as long as those shapes stand.
 *
 * <p>A path is settled by the ring it was traced inside and the inset it was traced at, and a
 * cluster name moves neither. A bake runs whenever a name may have moved - which is every change the
 * layer marks, since a re-fit can place a name on a cell the change never touched - so without this
 * every cell in the sector pays a full trace, a mitre inset and fold splice and clearance walk and
 * arc-length walk apiece, to arrive at the path it discarded a moment earlier. A cell's ring is
 * decided by which of its edges are seams inside one cluster, so after a change the cells that need
 * tracing again are the handful that were re-shaped.
 *
 * <p>Kept per inset rather than one path per cell, because the diagnostic overlay reads which inset
 * a cell's band ended up on, and a single kept path cannot say whether the pad was given up to get
 * it. Holding each inset's path as walked lets the overlay read the very rings the band pass walked
 * rather than walking them a second time.
 *
 * <p>Held with no key and no revision of its own: the write that replaces a cell's shape drops that
 * cell's paths, and a rebuild mints a fresh cache. Why that is the whole of what keeps it safe is
 * <a href="README.md#a-ring-that-outlives-the-bake-that-walked-it">the band register</a>'s.
 *
 * <p>What is kept includes the paths that came back with nothing to lay a band on. A cell too
 * narrow for the band is an answer the trace worked for, so it is worth keeping for exactly the
 * reason a usable path is.
 */
public final class CellRingPathCache {

    private final Map<SystemKey, Map<RibbonPathInset, RingPath>> ringPathByInsetByCellKey =
        new LinkedHashMap<>();

    /**
     * The path standing for one cell at one inset, traced inside the shape that cell holds now.
     *
     * @param cellKey the cell to answer for
     * @param inset   the inset the path was traced at
     * @return its traced path, or null where none has been traced at that inset inside the cell's
     *         current shape - which is a ring yet to be walked, not a cell that has no path
     */
    public RingPath findRingPathOf(SystemKey cellKey, RibbonPathInset inset) {

        var ringPathByInset = ringPathByInsetByCellKey.get(cellKey);
        return ringPathByInset == null
            ? null
            : ringPathByInset.get(inset);
    }

    /**
     * Keeps the path just traced inside one cell's current shape.
     *
     * @param cellKey  the cell it was traced for
     * @param inset    the inset it was traced at
     * @param ringPath the traced path, holding no stretch where the cell had no room for a band
     */
    void putRingPath(SystemKey cellKey, RibbonPathInset inset, RingPath ringPath) {

        ringPathByInsetByCellKey
            .computeIfAbsent(cellKey, key -> new EnumMap<>(RibbonPathInset.class))
            .put(inset, ringPath);
    }

    /**
     * Drops every path of one cell, because the shape they were traced inside is no longer the shape
     * that cell holds.
     *
     * @param cellKey the cell that was re-shaped or stopped drawing
     */
    void dropRingPathsOf(SystemKey cellKey) {
        ringPathByInsetByCellKey.remove(cellKey);
    }
}
