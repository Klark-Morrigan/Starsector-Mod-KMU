package kmu.maplayers.ownermap.render.clusters;

import kmlib.opengl.PolygonTessellator;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.geometry.CellShaper;
import kmu.maplayers.base.geometry.EdgeInset;
import kmu.maplayers.base.render.clusters.BorderSmoothing;
import kmu.maplayers.base.render.clusters.debug.ClusterBorderStageCollector;
import kmu.maplayers.base.render.clusters.debug.ClusterBorderStageOverlay;
import kmu.maplayers.base.theme.BorderSmoothingStyle;
import kmu.maplayers.base.theme.CornerRoundingStyle;
import kmu.maplayers.ownermap.owners.ResolvedOwners;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.render.style.MapStyling;
import kmu.maplayers.ownermap.render.style.UnownedStyleResolver;

import java.util.List;
import java.util.Set;

/**
 * Builds the debug border-tracing overlay: for each owned cluster, its border
 * loops captured at every smoothing stage, so the diagnostic renderer can show what each
 * pass did to the geometry.
 *
 * <p>It builds none of the production draw lists, so while the debug toggle is on it fully
 * replaces the normal build rather than deriving from it. The owners it traces are handed over,
 * resolved through the active view's own source - the resolve the normal build would have made,
 * the normal build being skipped in debug mode - so it is no extra cost, just spent here instead.
 *
 * <p>It traces the same inset rings the production {@link OwnerMapBuilder} does and runs
 * them through the shared {@link BorderSmoothing} passes, keeping the result after each
 * stage rather than only the final one. The two smoothing gates are honoured exactly as in
 * production: a stage is captured only when its pass ran, and sanding (when on) feeds
 * rounding, so with sanding off the rounded stage rounds the base directly. The overlay's
 * layers therefore mirror the pipeline the normal render would have produced.
 *
 * <p>Owned clusters run the full base -> despiked -> rounded
 * pipeline. Drawn unowned cells (inhabited, or uninhabited when the player turns
 * them on) contribute only base and rounded: a lone convex inset cell has no needle
 * protrusions, so it skips sanding. Unowned cells respect the same visibility the
 * normal render applies - one draws only when its category's outline colour is not "No
 * colour" - so the overlay does not flood the map with every uninhabited system's cell.
 */
public final class DebugBorderTracingBuilder {

    // Builds only; never instantiated.
    private DebugBorderTracingBuilder() {
    }

    /**
     * Traces every owned cluster, and every unowned cell the map outlines, into the three stage
     * lists. Built instead of the production drawables while the debug toggle is on, not beside
     * them.
     *
     * @param geometryCache the cut cells the production build would shape
     * @param owners        the owners the active view's source resolved for this rebuild
     * @param styling       the rebuild's one reading of the theme and the border trace
     * @return the overlay, one loop list per stage
     */
    public static ClusterBorderStageOverlay buildDebugDrawables(
            CellGeometryCache geometryCache,
            ResolvedOwners owners,
            MapStyling styling) {

        // The agnostic geometry groups the drawn cells, resolving each to the system it draws
        // as and that system to its owner ID.
        var cellGrouping = SystemOwner.mapCellGrouping(
            geometryCache.getSystemKeyByCellKey(),
            owners.ownerBySystemKey());

        var stageCollector = new ClusterBorderStageCollector();

        // Both passes take the rebuild's one styling whole rather than reading the knobs here: the
        // names over this overlay are styled from the same scheme, and a second reading could trace
        // or gate the outlines under knobs the names were not styled under.
        addClusterOutlines(geometryCache, cellGrouping, styling, stageCollector);

        // The same inhabitation answer the production build classifies its unowned cells by,
        // off the same source, so the overlay shows the cells the map would show.
        addUnownedOutlines(
            geometryCache,
            cellGrouping,
            owners.inhabitedSystemKeys(),
            styling,
            stageCollector);

        return stageCollector.buildOverlay();
    }

    // Traces every owned cluster and captures the stages its border passes through. Whole
    // clusters, so no neighbour is coincident: every boundary edge takes the uniform channel,
    // exactly as the drawn cluster border does. A cluster whose trace yields no ring has nothing
    // to show at any stage and is skipped.
    //
    // The same trace and smoothing the production build reads, so a stage captured here is the
    // geometry the normal render would have drawn.
    private static void addClusterOutlines(
            CellGeometryCache geometryCache,
            CellGrouping cellGrouping,
            MapStyling styling,
            ClusterBorderStageCollector stageCollector) {

        var borderTrace = styling.borderTrace();
        var borderSmoothing = styling.renderStyle().global().borderSmoothing();
        var cellEdgesByCellKey = geometryCache.getCellEdgesByCellKey();

        for (var memberCellKeys : cellGrouping.groupCellKeysByOwner().values()) {

            var insetRings = borderTrace.traceRings(
                memberCellKeys,
                cellEdgesByCellKey,
                cellGrouping);

            if (!insetRings.isEmpty()) {
                captureClusterStages(insetRings, borderSmoothing, stageCollector);
            }
        }
    }

    // Appends each unowned cell the normal render outlines, so the overlay stays as legible as
    // the map it explains.
    private static void addUnownedOutlines(
            CellGeometryCache geometryCache,
            CellGrouping cellGrouping,
            Set<SystemKey> inhabitedSystemKeys,
            MapStyling styling,
            ClusterBorderStageCollector stageCollector) {

        // Only the rounding half: an unowned cell has no sanding stage to capture, so the sanding
        // numbers never reach it.
        var cornerRounding = styling.renderStyle().global().borderSmoothing().cornerRounding();

        for (var entry : geometryCache.getCellEdgesByCellKey().entrySet()) {

            if (isUnownedOutlineDrawn(entry.getKey(), cellGrouping, inhabitedSystemKeys, styling)) {
                captureUnownedStages(entry.getValue(), cellGrouping, cornerRounding, stageCollector);
            }
        }
    }

    // buildClusterGroup's pipeline with each stage kept: the resolved base, then whichever passes
    // the smoothing ran, reported to the collector by the smoothing itself.
    //
    // Short of it by the resolve that pipeline runs AFTER rounding, which is not a stage of its own
    // to capture - it tidies the rounded loops rather than shaping them, and a crossing it closes
    // was opened by an arc this overlay is drawn to show. So the last stage here is the rounding's
    // own output, which is the one a reader looking at why a border bulges wants to see.
    private static void captureClusterStages(
            List<List<double[]>> insetRings,
            BorderSmoothingStyle borderSmoothing,
            ClusterBorderStageCollector stageCollector) {

        var base = PolygonTessellator.tessellateToBoundaryLoops(insetRings);

        stageCollector.captureBaseStage(base);
        BorderSmoothing.smoothBorderLoops(base, borderSmoothing, stageCollector);
    }

    // One unowned cell's two stages: its raw inset as the base and, when rounding is gated on, its
    // rounded corners through the same shared rounding the clusters use. An unowned cell is a
    // single convex inset polygon - no chaining, no spikes to sand. A cell whose shape comes back
    // empty has nothing to show at either stage.
    private static void captureUnownedStages(
            List<CellEdge> cellEdges,
            CellGrouping cellGrouping,
            CornerRoundingStyle cornerRounding,
            ClusterBorderStageCollector stageCollector) {

        var shaped = CellShaper.shapeCell(
            cellEdges,
            null,
            cellGrouping.ownerBySystemKey(),
            EdgeInset.asTheMapDraws());

        if (shaped.fillPolygon().isEmpty()) {
            return;
        }

        stageCollector.captureBaseStage(List.of(shaped.fillPolygon()));

        // Through the single-loop entry the production lone cell rounds by, so the two outlines
        // cannot round apart.
        if (cornerRounding.shouldRoundCorners()) {

            stageCollector.captureRoundedStage(List.of(
                BorderSmoothing.roundLoopCorners(shaped.fillPolygon(), cornerRounding)));
        }
    }

    // Whether the normal render outlines this cell as unowned. An owned cell is traced as part of
    // its cluster instead, and a category whose outline is "No colour" draws nothing.
    //
    // The cell is settled or empty by the system it draws as - a cell with no system of its own is
    // empty - and takes its style through the lookup the production fills make.
    private static boolean isUnownedOutlineDrawn(
            SystemKey cellKey,
            CellGrouping cellGrouping,
            Set<SystemKey> inhabitedSystemKeys,
            MapStyling styling) {

        if (cellGrouping.resolveOwnerOf(cellKey) != null) {
            return false;
        }

        var drawnSystemKey = cellGrouping.resolveDrawnSystemKeyOf(cellKey);

        return styling
            .resolveUnownedStyle(UnownedStyleResolver.isSettledSystem(inhabitedSystemKeys, drawnSystemKey))
            .outer()
            .isDrawn();
    }
}
