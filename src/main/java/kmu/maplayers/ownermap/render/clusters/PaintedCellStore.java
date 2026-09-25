package kmu.maplayers.ownermap.render.clusters;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.render.clusters.PaintedCell;
import kmu.maplayers.base.render.clusters.StyledCell;
import kmu.maplayers.ownermap.render.ribbon.CellRibbon;
import kmu.maplayers.ownermap.render.ribbon.CellRibbonPath;
import kmu.maplayers.ownermap.render.ribbon.CellRingPathCache;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything one build holds per drawn cell: the draw record, the ring it was painted on, the
 * presence band laid inside that ring, the band's path for the diagnostic overlay, and the traced
 * ring the band was laid along.
 *
 * <p>Five stores rather than one map of a five-field record, because they are written by different
 * passes and most cells are in only two of them: a band reports what is held in a system and most
 * of the sector is cells nobody lives in, and a path exists only while the player has the overlay
 * on. A record per cell would make every cell pay for all five.
 *
 * <p>What makes them one type is that they are dropped together. Everything after the draw record
 * is fitted to one particular ring, so a cell re-shaped and left holding any of it would draw the
 * last shape's band inside this shape's cell. Owning the five here means a re-shape clears them by
 * construction, rather than by two call sites on a larger model each remembering the same five
 * fields.
 *
 * <p>Live and mutable throughout: a full rebuild fills it, and the incremental refresh replaces
 * just the cells a holder change touched. The maps it hands back are the live ones, since the
 * emission walks them per frame and the highlight memoises on their contents by identity - a
 * defensive copy would cost a frame and defeat that memo.
 */
public final class PaintedCellStore {

    private final Map<SystemKey, StyledCell> styledCellByCellKey = new LinkedHashMap<>();

    private final Map<SystemKey, List<double[]>> fillPolygonByCellKey = new LinkedHashMap<>();

    private final Map<SystemKey, CellRibbon> ribbonByCellKey = new LinkedHashMap<>();

    private final Map<SystemKey, CellRibbonPath> ribbonPathByCellKey = new LinkedHashMap<>();

    private final CellRingPathCache ringPathCache = new CellRingPathCache();

    /**
     * @return each drawn cell's draw record, keyed by cell key; a cell that puts no ink on the map
     *         is absent
     */
    public Map<SystemKey, StyledCell> getStyledCellByCellKey() {
        return styledCellByCellKey;
    }

    /**
     * @return each drawn cell's painted extent as {x, y} vertex pairs in world coordinates, the
     *         geometry a cursor position is resolved against
     */
    public Map<SystemKey, List<double[]>> getFillPolygonByCellKey() {
        return fillPolygonByCellKey;
    }

    /**
     * @return each cell that draws a presence band, keyed by cell key; a cell drawing none is
     *         absent rather than present with an empty band
     */
    public Map<SystemKey, CellRibbon> getRibbonByCellKey() {
        return ribbonByCellKey;
    }

    /**
     * @return each cell the diagnostic overlay has a band path for, keyed by cell key; empty while
     *         the player has the overlay off
     */
    public Map<SystemKey, CellRibbonPath> getRibbonPathByCellKey() {
        return ribbonPathByCellKey;
    }

    /**
     * @return the rings this build's bands are laid along, the cache the band pass asks before it
     *         traces a cell and writes whatever it does trace into; live rather than a copy, since
     *         a pass reading a snapshot of it would trace every cell afresh
     */
    public CellRingPathCache getRingPathCache() {
        return ringPathCache;
    }

    /** @return whether no cell draws at all, so the renderer can skip the GL state push */
    public boolean isEmpty() {
        return styledCellByCellKey.isEmpty();
    }

    /**
     * Records one cell's draw record together with the ring it was painted on, the pair the cursor
     * read depends on staying aligned, and clears whatever was fitted to the ring it replaces.
     *
     * <p>The write path for both maps, rather than each caller putting into them separately: a cell
     * that draws and a cell that answers a hover must be the same set. The two arrive as one
     * {@link PaintedCell} for the same reason one step earlier - a caller that could pass a ring of
     * its own choosing could pass one the cell never painted, and a hover would then light a shape
     * the map does not draw.
     *
     * @param cellKey     the cell this record is for
     * @param paintedCell its draw record and the ring that record was built from - the cell's
     *                    painted extent, with the border inset, frontier setback, keep-out
     *                    clipping and its own corner rounding already applied
     */
    public void putPaintedCell(
            SystemKey cellKey,
            PaintedCell paintedCell) {

        styledCellByCellKey.put(cellKey, paintedCell.styledCell());
        fillPolygonByCellKey.put(cellKey, paintedCell.paintedExtent());
        dropRingFittedWork(cellKey);
    }

    /**
     * Records one cell's presence band, baked inside the ring that cell already holds.
     *
     * <p>Written on its own pass rather than beside the ring, because a band is settled from more
     * than the cell it sits in: it keeps clear of the cluster names, and those are placed only once
     * every cell has been shaped. So the ring goes in first and the band follows, and the band pass
     * reads the ring back off this store rather than being handed one - which is what keeps the two
     * describing the same ring without either caller having to remember the other.
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
     * <p>Written by the band pass beside the band itself, so a cell can never show a path the band
     * it carries was not laid on. A pass with the overlay switched off hands over nothing for every
     * cell, which is what clears the paths a pass taken while it was on left behind.
     *
     * @param cellKey    the cell this path is for
     * @param ribbonPath the traced path, or {@link CellRibbonPath#NONE} where none was traced
     */
    public void putCellRibbonPath(SystemKey cellKey, CellRibbonPath ribbonPath) {

        // Left out of the map rather than held as an empty value, exactly as a bandless cell is:
        // the overlay walks only the cells with a path to draw, which is none of them while the
        // player has it off.
        if (ribbonPath.isEmpty()) {
            ribbonPathByCellKey.remove(cellKey);
        } else {
            ribbonPathByCellKey.put(cellKey, ribbonPath);
        }
    }

    /**
     * Drops one cell entirely - it draws nothing, so it can be hovered over no more than it can be
     * seen.
     *
     * @param cellKey the cell that no longer draws
     */
    public void removePaintedCell(SystemKey cellKey) {
        styledCellByCellKey.remove(cellKey);
        fillPolygonByCellKey.remove(cellKey);
        dropRingFittedWork(cellKey);
    }

    // Everything laid inside one cell's ring: its band, that band's diagnostic path, and the traced
    // ring itself. Dropped whenever the ring is replaced or the cell stops drawing, since each was
    // fitted to the ring that is going - a band left behind would draw the last shape's stripe
    // inside this shape's cell, and a ring left behind would have the next bake lay one there.
    private void dropRingFittedWork(SystemKey cellKey) {
        ribbonByCellKey.remove(cellKey);
        ribbonPathByCellKey.remove(cellKey);
        ringPathCache.dropRingPathOf(cellKey);
    }
}
