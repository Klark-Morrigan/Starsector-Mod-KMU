package kmu.maplayers.politicalmap.base.render.ribbon;

import kmlib.math.geometry.RingPath;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The band paths traced inside one build's cell shapes, kept for as long as those shapes stand.
 *
 * <p>A path is settled by the ring it was traced inside and the sizes it was traced at, and a
 * cluster name moves neither. A bake runs whenever a name may have moved - which is every colony
 * flip, since a re-fit can place a name on a cell the flip never touched - so without this every
 * cell in the sector pays a full trace, a miter inset and fold splice and clearance walk and
 * arc-length walk apiece, to arrive at the path it discarded a moment earlier. A cell's ring is
 * decided by which of its edges are same-owner seams, so after a flip the cells that need tracing
 * again are the handful that were re-shaped.
 *
 * <p>Held with no key and no revision of its own, which is the whole of why it is safe. A key is a
 * rule someone has to keep true; a cache living inside the object whose lifetime it must match is
 * true by construction - a rebuild mints fresh territories and every path traced against the old
 * shapes goes with them, and a size moved on the sliders bumps the settings revision, which
 * rebuilds them. Within one build, the write that replaces a cell's shape drops that cell's path
 * at the same time.
 *
 * <p>What is kept includes the paths that came back with nothing to lay a band on. A cell too
 * narrow for the band is an answer the trace worked for - the whole inset ladder was walked to
 * reach it - so it is worth keeping for exactly the reason a usable path is.
 */
public final class CellRingPathCache {

    private final Map<String, RingPath> ringPathByCellId = new LinkedHashMap<>();

    /**
     * The path standing for one cell, traced inside the shape that cell holds now.
     *
     * @param cellId the cell to answer for
     * @return its traced path, or null where none has been traced inside its current shape - which
     *         is a cell whose ring is yet to be walked, not a cell that has no path
     */
    public RingPath findRingPathOf(String cellId) {
        return ringPathByCellId.get(cellId);
    }

    /**
     * Keeps the path just traced inside one cell's current shape.
     *
     * @param cellId   the cell it was traced for
     * @param ringPath the traced path, holding no stretch where the cell had no room for a band
     */
    public void putRingPath(String cellId, RingPath ringPath) {
        ringPathByCellId.put(cellId, ringPath);
    }

    /**
     * Drops one cell's path, because the shape it was traced inside is no longer the shape that
     * cell holds.
     *
     * @param cellId the cell that was re-shaped or stopped drawing
     */
    public void dropRingPathOf(String cellId) {
        ringPathByCellId.remove(cellId);
    }
}
