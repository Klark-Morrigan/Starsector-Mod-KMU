package kmu.maplayers.politicalmap.base.render.debug;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.opengl.GlVertexRuns;
import kmlib.opengl.PolygonTessellator;
import kmlib.starsector.markets.DecivilisedMarkets;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.geometry.CellShaper;
import kmu.maplayers.base.render.regions.BorderSmoothing;
import kmu.maplayers.base.render.regions.ClusterBorderTrace;
import kmu.maplayers.base.theme.BorderSmoothingStyle;
import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.maplayers.politicalmap.base.politics.SectorPolitics;
import kmu.maplayers.politicalmap.base.render.style.FactionlessStyleResolver;
import kmu.maplayers.politicalmap.base.render.style.RenderStyleReader;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Builds the debug border-tracing overlay: for each owned faction cluster, its border
 * loops captured at every smoothing stage, so the diagnostic renderer can show what each
 * pass did to the geometry.
 *
 * <p>Self-contained: it resolves its own ownership and factionless visibility from the
 * sector and builds none of the production draw lists, so while the debug toggle is on it
 * fully replaces the normal build rather than deriving from it. The one politics scan it
 * runs is the same one the normal build would have - the normal build is skipped in debug
 * mode - so it is no extra cost, just done here instead.
 *
 * <p>It traces the same inset rings the production
 * {@link kmu.maplayers.politicalmap.base.render.territories.TerritoryBuilder} does and runs
 * them through the shared {@link BorderSmoothing} passes, keeping the result after each
 * stage rather than only the final one. The two smoothing gates are honored exactly as in
 * production: a stage is captured only when its pass ran, and sanding (when on) feeds
 * rounding, so with sanding off the rounded stage rounds the base directly. The overlay's
 * layers therefore mirror the pipeline the normal render would have produced.
 *
 * <p>Owned faction and independent clusters run the full base -> despiked -> rounded
 * pipeline. Drawn factionless cells (decivilised, or uninhabited when the player turns
 * them on) contribute only base and rounded: a lone convex inset cell has no needle
 * protrusions, so it skips sanding. Factionless cells respect the same visibility the
 * normal render applies - one draws only when its category's outline color is not "No
 * color" - so the overlay does not flood the map with every uninhabited system's cell.
 */
public final class DebugBorderTracingBuilder {
    // Builds only; never instantiated.
    private DebugBorderTracingBuilder() {
    }

    // Resolves ownership from the sector and traces every owned cluster (plus the drawn
    // factionless cells) into the three stage lists. Independent of the production
    // drawables, so the plugin builds this instead of them in debug mode, not alongside.
    public static PoliticalMapDebugTerritories buildDebugDrawables(
            CellGeometryCache geometryCache,
            SectorAPI sector) {

        var ownerBySystemId = SectorPolitics.resolveDominantOwnerBySystemId(sector);

        // The agnostic geometry groups the drawn cells, resolving each to the system it draws
        // as and that system to its faction id.
        var cellGrouping = DominantOwner.mapCellGrouping(
                geometryCache.getSystemIdByCellId(),
                ownerBySystemId);

        var decivilisedSystemIds = DecivilisedMarkets.findRevealedDecivilisedSystemIds(sector);
        // The same trace and the same smoothing profile the production build reads, so a stage
        // captured here is the geometry the normal render would have drawn rather than one this
        // builder assembled from its own reads of the same knobs.
        var borderTrace = ClusterBorderTrace.readFromLunaSettings();
        var borderSmoothing = RenderStyleReader.readBorderSmoothingStyle();
        var baseLoops = new ArrayList<float[]>();
        var despikedLoops = new ArrayList<float[]>();
        var roundedLoops = new ArrayList<float[]>();

        for (var memberCellIds : cellGrouping.groupCellIdsByKey().values()) {
            // Whole clusters, so no neighbour is coincident: every boundary edge takes the
            // uniform channel, exactly as the drawn national border does.
            var insetRings = borderTrace.traceRings(
                    memberCellIds,
                    geometryCache.getCellEdgesByCellId(),
                    cellGrouping);
            if (insetRings.isEmpty()) {
                continue;
            }

            // Same pipeline as buildFactionTerritory, but keep each stage. Base first, then
            // sand and round only when gated on, so what is captured is exactly what would
            // have been drawn.
            var base = PolygonTessellator.tessellateToBoundaryLoops(insetRings);
            addFlattenedLoops(baseLoops, base);
            var smoothed = base;
            if (borderSmoothing.shouldSandSpikes()) {
                smoothed = BorderSmoothing.sandBorderSpikes(smoothed, borderSmoothing);
                addFlattenedLoops(despikedLoops, smoothed);
            }
            if (borderSmoothing.shouldRoundCorners()) {
                smoothed = BorderSmoothing.roundBorderCorners(smoothed, borderSmoothing);
                addFlattenedLoops(roundedLoops, smoothed);
            }
        }
        addFactionlessOutlines(
                geometryCache,
                cellGrouping,
                decivilisedSystemIds,
                borderSmoothing,
                baseLoops,
                roundedLoops);

        return new PoliticalMapDebugTerritories(
                baseLoops,
                despikedLoops,
                roundedLoops);
    }

    // Appends each drawn factionless cell's outline as a base loop and, when rounding is
    // gated on, a rounded loop. A factionless cell is a single convex inset polygon - no
    // chaining, no spikes to sand - so its two stages are the raw inset (base) and its
    // rounded corners, produced through the same shared rounding the clusters use. Skips
    // owned cells (traced as clusters above) and any category whose outline is "No color",
    // matching the normal render's visibility so the overlay stays legible.
    private static void addFactionlessOutlines(
            CellGeometryCache geometryCache,
            CellGrouping cellGrouping,
            Set<String> decivilisedSystemIds,
            BorderSmoothingStyle borderSmoothing,
            List<float[]> baseLoops,
            List<float[]> roundedLoops) {

        // The whole theme rather than the two factionless bundles separately, so a category
        // resolved by the shared rule indexes straight into it - the same lookup the production
        // draw makes, which is what keeps the overlay showing the cells the map would show.
        var renderStyle = RenderStyleReader.readRenderStyle();
        for (var entry : geometryCache.getCellEdgesByCellId().entrySet()) {
            if (cellGrouping.resolveGroupKeyOf(entry.getKey()) != null) {
                continue;
            }
            // A factionless cell resolves its decivilised/uninhabited style through the system
            // it draws as; a cell with no system of its own is uninhabited ground.
            var drawnSystemId = cellGrouping.resolveDrawnSystemIdOf(entry.getKey());
            var style = renderStyle.categoryStyle(FactionlessStyleResolver.resolveCategoryOf(
                    decivilisedSystemIds, drawnSystemId));
            if (!style.outer().isDrawn()) {
                continue;
            }
            var shaped = CellShaper.shapeCell(
                    entry.getValue(),
                    null,
                    cellGrouping.groupKeyBySystemId(),
                    CellShaper.BORDER_INSET_DISTANCE);
            if (shaped.fillPolygon().isEmpty()) {
                continue;
            }
            var base = List.of(shaped.fillPolygon());
            addFlattenedLoops(baseLoops, base);
            if (borderSmoothing.shouldRoundCorners()) {
                addFlattenedLoops(
                        roundedLoops,
                        BorderSmoothing.roundBorderCorners(base, borderSmoothing));
            }
        }
    }

    // Flattens each loop of a stage into a GL run and appends it to that stage's list.
    private static void addFlattenedLoops(List<float[]> runs, List<List<double[]>> loops) {
        for (var loop : loops) {
            runs.add(GlVertexRuns.flattenVertices(loop));
        }
    }
}
