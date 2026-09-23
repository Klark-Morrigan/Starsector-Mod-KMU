package kmu.maplayers.politicalmap.base.render.debug;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.opengl.PolygonTessellator;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.geometry.CellShaper;
import kmu.maplayers.base.geometry.EdgeInset;
import kmu.maplayers.base.render.clusters.BorderSmoothing;
import kmu.maplayers.base.render.clusters.ClusterBorderTrace;
import kmu.maplayers.base.render.clusters.debug.ClusterBorderStageCollector;
import kmu.maplayers.base.render.clusters.debug.ClusterBorderStageOverlay;
import kmu.maplayers.base.theme.RenderStyle;
import kmu.maplayers.politicalmap.base.PoliticalMapInhabitation;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.politics.SectorPolitics;
import kmu.maplayers.politicalmap.base.render.ContentInputs;
import kmu.maplayers.politicalmap.base.render.style.FactionlessStyleResolver;
import kmu.maplayers.politicalmap.base.render.style.RenderStyleReader;

import java.util.List;
import java.util.Set;

/**
 * Builds the debug border-tracing overlay: for each owned faction cluster, its border
 * loops captured at every smoothing stage, so the diagnostic renderer can show what each
 * pass did to the geometry.
 *
 * <p>Self-contained: it resolves its own holding and factionless visibility from the
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
 * pipeline. Drawn factionless cells (inhabited but unheld, or uninhabited when the player turns
 * them on) contribute only base and rounded: a lone convex inset cell has no needle
 * protrusions, so it skips sanding. Factionless cells respect the same visibility the
 * normal render applies - one draws only when its category's outline colour is not "No
 * colour" - so the overlay does not flood the map with every uninhabited system's cell.
 */
public final class DebugBorderTracingBuilder {

    // Builds only; never instantiated.
    private DebugBorderTracingBuilder() {
    }

    // Resolves holding from the sector and traces every owned cluster (plus the drawn
    // factionless cells) into the three stage lists. Independent of the production
    // drawables, so the plugin builds this instead of them in debug mode, not alongside.
    public static ClusterBorderStageOverlay buildDebugDrawables(
            CellGeometryCache geometryCache,
            SectorAPI sector,
            ContentInputs contentInputs) {

        // This overlay's own reading of the sector: it never filters and groups nothing, so it
        // opens a plain identity pass rather than being handed one - there is no rebuild above it
        // to inherit from. Held in a local because both reads below take it, which is what makes
        // the overlay's holding and its inhabitation answer off one walk of each system.
        var pass = HolderPass.readFromLunaSettings(sector, HolderGrouping.identity());
        var ownerBySystemKey = SectorPolitics.resolveDominantHolderBySystemKey(pass);

        // The agnostic geometry groups the drawn cells, resolving each to the system it draws
        // as and that system to its faction id.
        var cellGrouping = DominantHolder.mapCellGrouping(
            geometryCache.getSystemKeyByCellKey(),
            ownerBySystemKey);

        // The same inhabitation read the production build classifies its factionless cells by,
        // through the same seam, so the overlay shows the cells the map would show.
        var inhabitedSystemKeys = PoliticalMapInhabitation.readInhabitedSystemKeys(pass);

        // The same trace and the same theme the production build reads, so a stage captured here is
        // the geometry the normal render would have drawn rather than one this builder assembled
        // from its own reads of the same knobs. The whole theme rather than the smoothing alone,
        // because the factionless pass below indexes into it by category - read twice, the outlines
        // could be gated by one reading of the knobs and smoothed by another.
        //
        // The uninhabited outline's own switch arrives with the rebuild rather than being read: it
        // is a sidebar preference, sampled once per rebuild, and a second reading here could show
        // cells the production draw would not.
        var borderTrace = ClusterBorderTrace.readFromLunaSettings();
        var renderStyle = RenderStyleReader.readRenderStyle(
            contentInputs.isUninhabitedOutlineDrawn());

        var borderSmoothing = renderStyle.global().borderSmoothing();
        var stageCollector = new ClusterBorderStageCollector();

        for (var memberCellKeys : cellGrouping.groupCellKeysByOwner().values()) {

            // Whole clusters, so no neighbour is coincident: every boundary edge takes the
            // uniform channel, exactly as the drawn national border does.
            var insetRings = borderTrace.traceRings(
                memberCellKeys,
                geometryCache.getCellEdgesByCellKey(),
                cellGrouping);

            if (insetRings.isEmpty()) {
                continue;
            }

            // buildFactionTerritory's pipeline with each stage kept: base first, then sand and
            // round only when gated on, so what is captured is what each pass was handed.
            //
            // Short of it by the resolve that pipeline runs AFTER rounding, which is not a
            // stage of its own to capture - it tidies the rounded loops rather than shaping
            // them, and a crossing it closes was opened by an arc this overlay is drawn to
            // show. So the last stage here is the rounding's own output, which is the one a
            // reader looking at why a border bulges wants to see.
            var base = PolygonTessellator.tessellateToBoundaryLoops(insetRings);
            stageCollector.captureBaseStage(base);
            var smoothed = base;

            if (borderSmoothing.spikeSanding().shouldSandSpikes()) {
                smoothed = BorderSmoothing.sandBorderSpikes(
                    smoothed,
                    borderSmoothing.spikeSanding());

                stageCollector.captureDespikedStage(smoothed);
            }
            if (borderSmoothing.cornerRounding().shouldRoundCorners()) {
                smoothed = BorderSmoothing.roundBorderCorners(
                    smoothed,
                    borderSmoothing.cornerRounding());

                stageCollector.captureRoundedStage(smoothed);
            }
        }
        // Only the rounding half: the factionless pass has no sanding stage to capture, so the
        // sanding numbers never reach it.
        addFactionlessOutlines(
            geometryCache,
            cellGrouping,
            inhabitedSystemKeys,
            renderStyle,
            stageCollector);

        return stageCollector.buildOverlay();
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
            Set<SystemKey> inhabitedSystemKeys,
            RenderStyle renderStyle,
            ClusterBorderStageCollector stageCollector) {

        // The rebuild's one reading of the theme, handed over whole rather than re-read here, so a
        // category resolved by the shared rule indexes straight into it - the same lookup the
        // production draw makes, which is what keeps the overlay showing the cells the map would
        // show.
        var cornerRounding = renderStyle.global().borderSmoothing().cornerRounding();

        for (var entry : geometryCache.getCellEdgesByCellKey().entrySet()) {
            if (cellGrouping.resolveOwnerOf(entry.getKey()) != null) {
                continue;
            }
            // A factionless cell resolves its settled/uninhabited style through the system it
            // draws as; a cell with no system of its own is uninhabited.
            var drawnSystemKey = cellGrouping.resolveDrawnSystemKeyOf(entry.getKey());
            var style = renderStyle.categoryStyle(FactionlessStyleResolver.resolveCategoryOf(
                inhabitedSystemKeys,
                drawnSystemKey));

            if (!style.outer().isDrawn()) {
                continue;
            }
            var shaped = CellShaper.shapeCell(
                entry.getValue(),
                null,
                cellGrouping.ownerBySystemKey(),
                EdgeInset.asTheMapDraws());

            if (shaped.fillPolygon().isEmpty()) {
                continue;
            }
            var base = List.of(shaped.fillPolygon());
            stageCollector.captureBaseStage(base);

            if (cornerRounding.shouldRoundCorners()) {
                stageCollector.captureRoundedStage(
                    BorderSmoothing.roundBorderCorners(base, cornerRounding));
            }
        }
    }
}
