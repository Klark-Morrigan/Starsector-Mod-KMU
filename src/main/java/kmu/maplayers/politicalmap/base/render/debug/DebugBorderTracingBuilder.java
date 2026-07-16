package kmu.maplayers.politicalmap.base.render.debug;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.opengl.GlVertexRuns;
import kmlib.opengl.PolygonTessellator;
import kmlib.starsector.markets.DecivilisedMarkets;

import kmu.maplayers.politicalmap.base.geometry.CellShaper;
import kmu.maplayers.politicalmap.base.geometry.FrontierSettings;
import kmu.maplayers.politicalmap.base.geometry.PoliticalMapGeometryCache;
import kmu.maplayers.politicalmap.base.geometry.SystemClusterBorders;
import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.maplayers.politicalmap.base.politics.SectorPolitics;
import kmu.maplayers.politicalmap.base.render.style.PoliticalMapStyle;
import kmu.maplayers.politicalmap.base.render.style.RenderStyleReader;
import kmu.maplayers.politicalmap.base.render.territories.BorderSmoothing;
import kmu.settings.FactionPaletteChoice;
import kmu.settings.KmuLunaSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
            PoliticalMapGeometryCache geometryCache,
            SectorAPI sector) {
        var ownerBySystemId = SectorPolitics.resolveDominantOwnerBySystemId(sector);
        // The agnostic geometry clusters by grouping key, so key by each system's faction id.
        var groupKeyBySystemId = DominantOwner.mapFactionIdBySystemId(ownerBySystemId);
        var decivilisedSystemIds = DecivilisedMarkets.findRevealedDecivilisedSystemIds(sector);
        var weldTolerance = KmuLunaSettings.getPoliticalMapBorderWeldTolerance();
        var miterLimit = KmuLunaSettings.getPoliticalMapBorderMiterLimit();
        var isSandingOn = KmuLunaSettings.shouldSandBorderSpikes();
        var isRoundingOn = KmuLunaSettings.shouldRoundBorderCorners();
        // The same frontier snapshot the production build reads, so an owned cluster's
        // border reaches around a dead star in the overlay exactly as on the live map.
        // Read once here and shared with the factionless outlines below.
        var frontier = FrontierSettings.readFromLunaSettings(geometryCache.getSiteBySystemId());
        var baseLoops = new ArrayList<float[]>();
        var despikedLoops = new ArrayList<float[]>();
        var roundedLoops = new ArrayList<float[]>();
        for (var memberSystemIds
                : DominantOwner.groupSystemIdsByFactionId(ownerBySystemId).values()) {
            var insetRings = SystemClusterBorders.traceBorderRings(
                    memberSystemIds,
                    geometryCache.getCellEdgesBySystemId(),
                    groupKeyBySystemId,
                    PoliticalMapStyle.BORDER_INSET_DISTANCE,
                    weldTolerance,
                    miterLimit,
                    frontier);
            if (insetRings.isEmpty()) {
                continue;
            }
            // Same pipeline as buildFactionTerritory, but keep each stage. Base first, then
            // sand and round only when gated on, so what is captured is exactly what would
            // have been drawn.
            var base = PolygonTessellator.tessellateToBoundaryLoops(insetRings);
            addFlattenedLoops(baseLoops, base);
            var smoothed = base;
            if (isSandingOn) {
                smoothed = BorderSmoothing.sandBorderSpikes(smoothed);
                addFlattenedLoops(despikedLoops, smoothed);
            }
            if (isRoundingOn) {
                smoothed = BorderSmoothing.roundBorderCorners(smoothed);
                addFlattenedLoops(roundedLoops, smoothed);
            }
        }
        addFactionlessOutlines(
                geometryCache,
                groupKeyBySystemId,
                decivilisedSystemIds,
                isRoundingOn,
                frontier,
                baseLoops,
                roundedLoops);
        return new PoliticalMapDebugTerritories(baseLoops, despikedLoops, roundedLoops);
    }

    // Appends each drawn factionless cell's outline as a base loop and, when rounding is
    // gated on, a rounded loop. A factionless cell is a single convex inset polygon - no
    // chaining, no spikes to sand - so its two stages are the raw inset (base) and its
    // rounded corners, produced through the same shared rounding the clusters use. Skips
    // owned cells (traced as clusters above) and any category whose outline is "No color",
    // matching the normal render's visibility so the overlay stays legible.
    private static void addFactionlessOutlines(
            PoliticalMapGeometryCache geometryCache,
            Map<String, String> groupKeyBySystemId,
            Set<String> decivilisedSystemIds,
            boolean isRoundingOn,
            FrontierSettings frontier,
            List<float[]> baseLoops,
            List<float[]> roundedLoops) {
        var decivilisedStyle = RenderStyleReader.readDecivilisedStyle();
        var uninhabitedStyle = RenderStyleReader.readUninhabitedStyle();
        for (var entry : geometryCache.getCellEdgesBySystemId().entrySet()) {
            if (groupKeyBySystemId.containsKey(entry.getKey())) {
                continue;
            }
            var style = decivilisedSystemIds.contains(entry.getKey())
                    ? decivilisedStyle
                    : uninhabitedStyle;
            if (style.outerColor() == FactionPaletteChoice.NONE) {
                continue;
            }
            var shaped = CellShaper.shapeCell(
                    entry.getKey(),
                    entry.getValue(),
                    null,
                    groupKeyBySystemId,
                    PoliticalMapStyle.BORDER_INSET_DISTANCE,
                    frontier);
            if (shaped.fillPolygon().isEmpty()) {
                continue;
            }
            var base = List.of(shaped.fillPolygon());
            addFlattenedLoops(baseLoops, base);
            if (isRoundingOn) {
                addFlattenedLoops(roundedLoops, BorderSmoothing.roundBorderCorners(base));
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
