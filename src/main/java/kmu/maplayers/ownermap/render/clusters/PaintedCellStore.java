package kmu.maplayers.ownermap.render.clusters;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.render.clusters.PaintedCell;
import kmu.maplayers.base.render.clusters.StyledCell;
import kmu.maplayers.base.render.clusters.StyledCell.FusedCell;
import kmu.maplayers.base.render.clusters.StyledCell.LoneCell;
import kmu.maplayers.base.ribbon.CellRibbonStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything one build holds per drawn cell: the draw record, the ring it was painted on, and the
 * presence bands laid inside those rings.
 *
 * <p>The bands sit in a {@link CellRibbonStore} of their own because a different pass writes them and
 * most cells carry none: a band reports what is held in a system, and most of the sector is cells
 * nobody lives in.
 *
 * <p>That store is held here because it is dropped with the rings. Everything in it is fitted to one
 * particular ring, so a cell re-shaped and left holding any of it would draw the last shape's band
 * inside this shape's cell. Owning it here means a re-shape clears it by construction, rather than
 * by two call sites on a larger model each remembering to.
 *
 * <p>Live and mutable throughout: a full rebuild fills it, and the incremental refresh replaces
 * just the cells an owner change touched. The maps it hands back are the live ones, since the
 * emission walks them per frame and the highlight memoises on their contents by identity - a
 * defensive copy would cost a frame and defeat that memo.
 *
 * <p>The draw records are also kept parted by form, which is how the emission walks them. Parted
 * on the first read after a write rather than on every read: the emission reads them several times a
 * frame, and only a rebuild or a refresh writes.
 */
public final class PaintedCellStore {

    private final Map<SystemKey, StyledCell> styledCellByCellKey = new LinkedHashMap<>();
    private final Map<SystemKey, List<double[]>> fillPolygonByCellKey = new LinkedHashMap<>();

    // The draw records as callers read them. Read-only, because every write has to go through the
    // put and remove below: those are what mark the parted lists stale, and a write made straight
    // into the map would leave the emission drawing the cells as they were before it.
    private final Map<SystemKey, StyledCell> styledCellView =
        Collections.unmodifiableMap(styledCellByCellKey);

    private final CellRibbonStore ribbonStore = new CellRibbonStore();

    // The draw records parted by form, in the map's own order, and whether a write has landed since
    // they were parted.
    private List<FusedCell> fusedCells = List.of();
    private List<LoneCell> loneCells = List.of();

    private boolean areCellFormsStale;

    /**
     * @return each drawn cell's draw record, keyed by cell key, read-only; a cell that puts no ink
     *         on the map is absent
     */
    public Map<SystemKey, StyledCell> getStyledCellByCellKey() {
        return styledCellView;
    }

    /**
     * @return every drawn cell that fused into a cluster, in the order the cells were recorded
     */
    public List<FusedCell> getFusedCells() {

        partCellsByFormIfStale();
        return fusedCells;
    }

    /**
     * @return every drawn cell that fused with nothing, in the order the cells were recorded
     */
    public List<LoneCell> getLoneCells() {

        partCellsByFormIfStale();
        return loneCells;
    }

    /**
     * @return each drawn cell's painted extent as {x, y} vertex pairs in world coordinates, the
     *         geometry a cursor position is resolved against
     */
    public Map<SystemKey, List<double[]>> getFillPolygonByCellKey() {
        return fillPolygonByCellKey;
    }

    /**
     * @return the presence bands laid inside the drawn cells, with their overlay paths and the rings
     *         they were traced along; live, since the band pass writes into it and the emission walks
     *         it per frame
     */
    public CellRibbonStore getRibbonStore() {
        return ribbonStore;
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

        areCellFormsStale = true;

        ribbonStore.dropRingFittedWorkOf(cellKey);
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

        areCellFormsStale = true;

        ribbonStore.dropRingFittedWorkOf(cellKey);
    }

    // Parts the draw records by form where a write has landed since they were last parted. A write
    // batch - a rebuild, or a refresh of a few cells - is parted once, on the first frame after it.
    private void partCellsByFormIfStale() {

        if (!areCellFormsStale) {
            return;
        }

        var partedFusedCells = new ArrayList<FusedCell>();
        var partedLoneCells = new ArrayList<LoneCell>();

        for (var styledCell : styledCellByCellKey.values()) {

            if (styledCell instanceof FusedCell fusedCell) {
                partedFusedCells.add(fusedCell);

            } else if (styledCell instanceof LoneCell loneCell) {
                partedLoneCells.add(loneCell);
            }
        }

        fusedCells = List.copyOf(partedFusedCells);
        loneCells = List.copyOf(partedLoneCells);

        areCellFormsStale = false;
    }
}
