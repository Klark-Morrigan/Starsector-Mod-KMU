package kmu.maplayers.politicalmap.base.render.ribbon;

import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.IterationScope;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.labels.anchor.ClusterAnchor;
import kmu.maplayers.base.profiling.MapBuildCounters;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;
import kmu.settings.KmuPoliticalMapDiagnosticsSettings;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Bakes the presence bands over cells that have already been shaped and named.
 *
 * <p>Its own pass, run after the rest of a rebuild rather than inside the cell loop, because a
 * band needs two things settled that no single cell knows about: the shape it runs inside, and
 * where every cluster name on the map ended up. The names are fitted after the cells are shaped -
 * a name is placed inside the border its cluster's cells trace - so a band baked as its cell was
 * shaped would be laid before any name had a place, and the name would then be drawn across it.
 * Baking last is what lets the band keep out of the names' way instead - which is the shipped
 * answer rather than the only one, a player being free to hand the room back to the band and have
 * the name draw across it after all.
 *
 * <p>The bands are read out of the cells' own recorded shapes rather than off a shaping pass, so
 * the incremental refresh re-bakes the cells it disturbed through the very same call the full
 * rebuild bakes all of them through - which is what keeps an incrementally-updated band identical
 * to the one a full rebuild would lay.
 *
 * <p>The rings those bands run along are read back from the cells too, and for the reason the pass
 * runs as often as it does: a bake happens whenever a name may have moved, while a ring moves only
 * when its cell is re-shaped. So the store the cells keep their traced rings in is handed to the
 * source, which walks a ring only where none stands.
 *
 * <p>The diagnostic overlay's paths are traced in the same loop, under a toggle read once per pass
 * like everything else a pass is settled by. Beside the bands rather than as a pass of its own
 * because a path is traced inside the very shape the band beside it was laid in, and two passes
 * over the same cells could only ever agree about that by both being run.
 *
 * <p>What a pass spent is reported per cell and split four ways, since a bake does four separable
 * things - counts, traces, carves and strokes - that grow on different axes. See
 * {@link RibbonBakePhases}: the pass opens one scope over the whole loop and marks each phase as
 * the cell in hand finishes it, so the readout states what a cell costs rather than what a bake
 * does.
 *
 * <p>Sampled once per pass and then asked, like the band source it holds: what a pass bakes from -
 * the cells, their geometry, and the room the names took - must not vary between the cells of one
 * pass, and holding it is what makes baking every cell and baking a handful the same operation
 * over the same snapshot rather than two calls that have to be handed matching inputs.
 */
public final class CellRibbonsBaker {

    private final PoliticalMapTerritories territories;
    private final Map<String, String> systemIdByCellId;
    private final CellRibbonSource ribbonSource;
    private final boolean isRibbonPathShown;

    private CellRibbonsBaker(
            PoliticalMapTerritories territories,
            Map<String, String> systemIdByCellId,
            CellRibbonSource ribbonSource,
            boolean isRibbonPathShown) {

        this.territories = territories;
        this.systemIdByCellId = systemIdByCellId;
        this.ribbonSource = ribbonSource;
        this.isRibbonPathShown = isRibbonPathShown;
    }

    /**
     * Samples everything one pass's bands are baked from.
     *
     * @param territories    the built cells, read for their shapes and written back with their
     *                       bands
     * @param geometryCache  the cells' geometry, for the system each draws as and its site
     * @param pass           the reading of the sector the counts are made off, folded by the
     *                       grouping the cells were painted under; whose reading it is, and so how
     *                       current it is, is the caller's to decide
     * @param clusterAnchors the cluster names' placements, whose boxes the bands keep out of
     * @return the pass, ready to bake whichever cells the caller names
     */
    public static CellRibbonsBaker createForPass(
            PoliticalMapTerritories territories,
            CellGeometryCache geometryCache,
            HolderPass pass,
            List<ClusterAnchor> clusterAnchors) {

        // The cell-to-system lookup is taken here, once, rather than per cell inside the loop: the
        // read hands back a fresh unmodifiable view over the live cells, so asking per cell would
        // mint a wrapper per cell to answer one lookup. Taking it apart from the cache is also what
        // lets this state the one map it reads instead of holding the cache it lives in.
        return new CellRibbonsBaker(
            territories,
            geometryCache.getSystemIdByCellId(),
            CellRibbonSource.createForPass(
                pass,
                territories.getViewGrouping().view(),
                RibbonBakeSurface.createForPass(territories, geometryCache, clusterAnchors)),
            KmuPoliticalMapDiagnosticsSettings.shouldShowPoliticalMapRibbonPaths());
    }

    /** Bakes the band of every drawn cell, replacing whatever each was carrying. */
    public void bakeAllCellRibbons() {
        bakeCellRibbonsOf(territories.getFillPolygonByCellId().keySet());
    }

    /**
     * Bakes the bands of the named cells alone, leaving every other cell's as it stands.
     *
     * <p>For the incremental refresh, whose cells are the handful a colony change disturbed. A
     * cell that draws nothing is skipped rather than being an error: the caller names the cells
     * something happened to, and one of them losing its last colony is one of the things that
     * can have happened.
     *
     * @param cellIds the cells to re-bake
     */
    public void bakeCellRibbonsOf(Collection<String> cellIds) {

        // A band's count walks a system's colonies once per bake, and on the claims layer settles a
        // contest over them - so this is the one part of a rebuild that could rival the known
        // label-fit stall, and it is profiled on its own so a rebuild that slows down says which
        // half slowed. One scope over the whole loop rather than one per cell, since a scope per
        // cell would cost about what a cell costs.
        try (var bakeScope = ActiveProfiler
                .resolveProfiler()
                .openIterations(RibbonBakePhases.BAKE_SECTION)) {

            var bakedCells = bakeCellRibbons(cellIds, bakeScope);

            // The cells asked for are what the bake is paid per; how many of them came back with
            // anything to draw is a fact about this one call rather than a volume of work.
            bakeScope.addCount(MapBuildCounters.CELLS, cellIds.size());
            bakeScope.tagCall("banded=" + bakedCells);
        }
    }

    // Bakes each named cell's band inside the shape that cell already records, reporting how many
    // of them came back with anything to draw and charging what each cell cost to the pass's own
    // scope, a turn per cell.
    private int bakeCellRibbons(Collection<String> cellIds, IterationScope bakeScope) {

        var bakedCells = 0;

        for (var cellId : cellIds) {

            var fillPolygon = territories.getFillPolygonByCellId().get(cellId);
            if (fillPolygon == null) {
                continue;
            }
            // Named by the cell, so the slowest turn of the pass says which cell it was over -
            // which is the one fact a mean over the whole sector cannot carry.
            bakeScope.beginIteration(cellId);

            var ribbon = ribbonSource.buildCellRibbon(
                cellId,
                systemIdByCellId.get(cellId),
                fillPolygon,
                bakeScope);

            territories.putCellRibbon(cellId, ribbon);
            territories.putCellRibbonPath(cellId, traceCellRibbonPath(cellId, fillPolygon));
            bakedCells += ribbon.isEmpty() ? 0 : 1;

            // Closed after the overlay's own trace, so a turn covers everything a cell costs the
            // pass; what the four phases leave unaccounted is the difference between their sum and
            // the turn.
            bakeScope.endIteration();
        }
        return bakedCells;
    }

    // The cell's path for the diagnostic overlay, or nothing at all while the player has it off -
    // which is what empties the paths a pass taken while it was on left behind, since a settings
    // change rebuilds every cell.
    //
    // A second trace of the same ring rather than a reading of the one the band was laid on: what
    // the overlay is asked about is the cells whose bands were never laid, so there is no result
    // to read on exactly the cells it exists for. The cost is a dev toggle's, paid only while it
    // is on, and it buys a production return type that carries nothing diagnostic.
    private CellRibbonPath traceCellRibbonPath(String cellId, List<double[]> fillPolygon) {

        return isRibbonPathShown
            ? ribbonSource.traceCellRibbonPath(systemIdByCellId.get(cellId), fillPolygon)
            : CellRibbonPath.NONE;
    }
}
