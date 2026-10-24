package kmu.maplayers.base.ribbon;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.ribbon.overlay.CellRibbonPath;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Everything one build lays inside its cells' rings: each cell's band, that band's path for the
 * diagnostic overlay, and the rings the band was traced along.
 *
 * <p>Three stores rather than one map of a three-field record, because they are filled for
 * different cells and most cells are in none of them: a ring is walked and a band laid only where
 * the layer planned something, and a path exists only while the overlay is on. A record per cell
 * would make every cell pay for all three.
 *
 * <p>What makes them one type is that they are dropped together. Each was fitted to one particular
 * ring, so a cell re-shaped and left holding any of them would draw the last shape's band inside
 * this shape's cell. One drop per cell is what lets whoever records a cell's shape clear all three by
 * construction, rather than by every write site remembering the same three fields.
 *
 * <p>Live and mutable throughout: a full rebuild fills it, and an incremental refresh replaces just
 * the cells it re-shaped. The maps it hands back are the live ones, since the emission walks them
 * per frame and a defensive copy would cost a frame.
 */
public final class CellRibbonStore {

    private final Map<SystemKey, CellRibbon> ribbonByCellKey = new LinkedHashMap<>();
    private final Map<SystemKey, CellRibbonPath> ribbonPathByCellKey = new LinkedHashMap<>();

    private final CellRingPathCache ringPathCache = new CellRingPathCache();

    /**
     * @return each cell that draws a band, keyed by cell key; a cell drawing none is absent rather
     *         than present with an empty band
     */
    public Map<SystemKey, CellRibbon> getRibbonByCellKey() {
        return ribbonByCellKey;
    }

    /**
     * @return each cell the diagnostic overlay has a band path for, keyed by cell key; empty while
     *         the overlay is off
     */
    public Map<SystemKey, CellRibbonPath> getRibbonPathByCellKey() {
        return ribbonPathByCellKey;
    }

    /**
     * @return the rings this build's bands are laid along, the cache a bake asks before it traces a
     *         cell and writes whatever it does trace into; live rather than a copy, since a bake
     *         reading a snapshot of it would trace every cell afresh
     */
    public CellRingPathCache getRingPathCache() {
        return ringPathCache;
    }

    /**
     * Records one cell's band, baked inside the ring that cell already holds.
     *
     * @param cellKey the cell this band is for
     * @param ribbon  the baked band, or {@link CellRibbon#NONE} where the cell draws none
     */
    public void putCellRibbon(SystemKey cellKey, CellRibbon ribbon) {

        // A bandless cell is left out of the map rather than holding an empty value, so the render
        // pass walks only the cells that draw one - which is a small share of them.
        if (ribbon.isEmpty()) {
            ribbonByCellKey.remove(cellKey);

        } else {
            ribbonByCellKey.put(cellKey, ribbon);
        }
    }

    /**
     * Records one cell's band path for the diagnostic overlay, traced inside the ring that cell
     * already holds.
     *
     * <p>A bake with the overlay switched off hands over nothing for every cell, which is what
     * clears the paths a bake taken while it was on left behind.
     *
     * @param cellKey    the cell this path is for
     * @param ribbonPath the traced path, or {@link CellRibbonPath#NONE} where none was traced
     */
    public void putCellRibbonPath(SystemKey cellKey, CellRibbonPath ribbonPath) {

        // Left out of the map rather than held as an empty value, exactly as a bandless cell is:
        // the overlay walks only the cells with a path to draw, which is none of them while it is
        // off.
        if (ribbonPath.isEmpty()) {
            ribbonPathByCellKey.remove(cellKey);

        } else {
            ribbonPathByCellKey.put(cellKey, ribbonPath);
        }
    }

    /**
     * Drops everything laid inside one cell's ring: its band, that band's diagnostic path, and the
     * traced rings themselves.
     *
     * <p>For whoever replaces a cell's shape or stops drawing it, since each was fitted to the ring
     * that is going - a band left behind would draw the last shape's band inside this shape's cell,
     * and a ring left behind would have the next bake lay one there.
     *
     * @param cellKey the cell that was re-shaped or stopped drawing
     */
    public void dropRingFittedWorkOf(SystemKey cellKey) {

        ribbonByCellKey.remove(cellKey);
        ribbonPathByCellKey.remove(cellKey);

        ringPathCache.dropRingPathsOf(cellKey);
    }
}
