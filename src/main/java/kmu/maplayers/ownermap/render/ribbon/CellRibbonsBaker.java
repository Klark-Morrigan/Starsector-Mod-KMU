package kmu.maplayers.ownermap.render.ribbon;

import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.IterationScope;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.labels.anchor.StandingClusterAnchors;
import kmu.maplayers.base.profiling.MapBuildCounters;
import kmu.maplayers.base.ribbon.CellRibbon;
import kmu.maplayers.base.ribbon.CellRibbonStore;
import kmu.maplayers.base.ribbon.RibbonBakePhases;
import kmu.maplayers.base.ribbon.overlay.CellRibbonPath;
import kmu.maplayers.ownermap.owners.SectorWalk;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusters;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
 * when its cell is re-shaped. So the cells' own band store is where the bands are written, and its
 * kept rings are what the source lays them along, walking a ring only where none stands.
 *
 * <p>Everything a pass is settled by arrives as one {@link RibbonBakeSettings} its caller read.
 * With the bands switched off, the pass builds no band source at all: no mechanic is resolved, no
 * sizes are laid out, no name room is measured, and every cell is answered "no band". That takes
 * the counting off the rebuild as well as the bands off the map, which is the half of the switch a
 * player cannot see and the half that costs.
 *
 * <p>The diagnostic overlay's paths are traced in the same loop. Beside the bands rather than as a
 * pass of its own
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

    // The cells alone, not the build they belong to: every cell this pass reads is in the first map,
    // every band it lays goes in the store, and nothing it does per cell asks the build anything
    // else. What the factory below needs the whole build for is settled before the first cell is
    // baked.
    private final Map<SystemKey, List<double[]>> fillPolygonByCellKey;
    private final Map<SystemKey, SystemKey> systemKeyByCellKey;
    private final CellRibbonStore ribbonStore;

    // Empty on a pass with the bands switched off, which bakes every cell bare.
    private final Optional<CellRibbonSource> ribbonSource;

    private final boolean isRibbonPathShown;

    private CellRibbonsBaker(
            Map<SystemKey, List<double[]>> fillPolygonByCellKey,
            Map<SystemKey, SystemKey> systemKeyByCellKey,
            CellRibbonStore ribbonStore,
            Optional<CellRibbonSource> ribbonSource,
            boolean isRibbonPathShown) {

        this.fillPolygonByCellKey = fillPolygonByCellKey;
        this.systemKeyByCellKey = systemKeyByCellKey;
        this.ribbonStore = ribbonStore;
        this.ribbonSource = ribbonSource;
        this.isRibbonPathShown = isRibbonPathShown;
    }

    /**
     * Samples everything one pass's bands are baked from.
     *
     * @param clusters        the build being baked over; its cells are what a band is read from and
     *                        written back to, and the source its owners came from is what the counts
     *                        are read through
     * @param geometryCache   the cells' geometry, for the system each draws as and its site
     * @param walk            the walk of the sector the counts are made off; whose walk it is, and
     *                        so how current it is, is the caller's to decide
     * @param standingAnchors the cluster names' placements, whose boxes the bands keep out of, with the
     *                        face they were fitted in
     * @param settings        the player's band knobs, read once for this pass by its caller
     * @return the pass, ready to bake whichever cells the caller names
     */
    public static CellRibbonsBaker createForPass(
            OwnerMapClusters clusters,
            CellGeometryCache geometryCache,
            SectorWalk walk,
            StandingClusterAnchors standingAnchors,
            RibbonBakeSettings settings) {

        // The cell-to-system lookup is taken here, once, rather than per cell inside the loop: the
        // read hands back a fresh unmodifiable view over the live cells, so asking per cell would
        // mint a wrapper per cell to answer one lookup. Taking it apart from the cache is also what
        // lets this state the one map it reads instead of holding the cache it lives in.
        return new CellRibbonsBaker(
            clusters.getPaintedCells().getFillPolygonByCellKey(),
            geometryCache.getSystemKeyByCellKey(),
            clusters.getPaintedCells().getRibbonStore(),
            createRibbonSource(clusters, geometryCache, walk, standingAnchors, settings),
            settings.isRibbonPathShown());
    }

    /** Bakes the band of every drawn cell, replacing whatever each was carrying. */
    public void bakeAllCellRibbons() {
        bakeCellRibbonsOf(fillPolygonByCellKey.keySet());
    }

    /**
     * Bakes the bands of the named cells alone, leaving every other cell's as it stands.
     *
     * <p>For the incremental refresh, whose cells are the handful a marked change disturbed. A
     * cell that draws nothing is skipped rather than being an error: the caller names the cells
     * something happened to, and one of them losing the last thing standing in it is one of the
     * things that can have happened.
     *
     * @param cellKeys the cells to re-bake
     */
    public void bakeCellRibbonsOf(Collection<SystemKey> cellKeys) {

        // A band's count walks what a system holds once per bake, and under some mechanics settles
        // a contest over it - so this is the one part of a rebuild that could rival the known
        // label-fit stall, and it is profiled on its own so a rebuild that slows down says which
        // half slowed. One scope over the whole loop rather than one per cell, since a scope per
        // cell would cost about what a cell costs.
        try (var bakeScope = ActiveProfiler
                .resolveProfiler()
                .openIterations(RibbonBakePhases.BAKE_SECTION)) {

            var bakedCells = bakeCellRibbons(cellKeys, bakeScope);

            // The cells asked for are what the bake is paid per; how many of them came back with
            // anything to draw is a fact about this one call rather than a volume of work.
            bakeScope.addCount(MapBuildCounters.CELLS, cellKeys.size());
            bakeScope.tagCall("banded=" + bakedCells);
        }
    }

    // Bakes each named cell's band inside the shape that cell already records, reporting how many
    // of them came back with anything to draw and charging what each cell cost to the pass's own
    // scope, a turn per cell.
    private int bakeCellRibbons(Collection<SystemKey> cellKeys, IterationScope bakeScope) {

        var bakedCells = 0;

        for (var cellKey : cellKeys) {

            var fillPolygon = fillPolygonByCellKey.get(cellKey);
            if (fillPolygon == null) {
                continue;
            }

            // Named by the cell, so the slowest turn of the pass says which cell it was over -
            // which is the one fact a mean over the whole sector cannot carry.
            bakeScope.beginIteration(cellKey.systemId());

            var ribbon = ribbonSource
                .map(source -> source.buildCellRibbon(
                    cellKey,
                    systemKeyByCellKey.get(cellKey),
                    fillPolygon,
                    bakeScope))
                .orElse(CellRibbon.NONE);

            ribbonStore.putCellRibbon(cellKey, ribbon);
            ribbonStore.putCellRibbonPath(cellKey, traceCellRibbonPath(cellKey, fillPolygon));

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
    // change rebuilds every cell. A pass with the bands switched off traces nothing either: no
    // layout is in play, so a ring drawn from sizes nothing is laid at would be a diagnostic of its
    // own arithmetic.
    private CellRibbonPath traceCellRibbonPath(SystemKey cellKey, List<double[]> fillPolygon) {

        if (!isRibbonPathShown) {
            return CellRibbonPath.NONE;
        }
        return ribbonSource
            .map(source -> source.traceCellRibbonPath(
                cellKey,
                systemKeyByCellKey.get(cellKey),
                fillPolygon))
            .orElse(CellRibbonPath.NONE);
    }

    // The pass's band source, or none where the player has the bands switched off. Nothing is
    // sampled for a switched-off pass: no mechanic resolved, no system index built, no name room
    // measured, and the cells' own traced rings left where they are rather than reached for.
    private static Optional<CellRibbonSource> createRibbonSource(
            OwnerMapClusters clusters,
            CellGeometryCache geometryCache,
            SectorWalk walk,
            StandingClusterAnchors standingAnchors,
            RibbonBakeSettings settings) {

        if (!settings.areRibbonsDrawn()) {
            return Optional.empty();
        }

        var surface = RibbonBakeSurface.createForPass(
            clusters,
            geometryCache,
            standingAnchors,
            settings.isRibbonKeptClearOfNames(),
            settings.nameClearance());

        return Optional.of(CellRibbonSource.createForPass(
            walk,
            clusters.getBuildInputs().getViewReading().source(),
            surface,
            settings.ribbonStyle(),
            settings.planRules()));
    }
}
