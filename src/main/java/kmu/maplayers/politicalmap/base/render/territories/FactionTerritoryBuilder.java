package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.opengl.GlVertexRuns;
import kmlib.opengl.PolygonTessellator;
import kmlib.starsector.ui.render.gl.UiElementPaint;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.render.regions.BorderSmoothing;
import kmu.maplayers.base.render.regions.ClusterBorderTrace;
import kmu.maplayers.base.render.regions.FillSplit;
import kmu.maplayers.base.render.regions.SplitFillBuilder;
import kmu.maplayers.base.theme.BorderSmoothingStyle;
import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.maplayers.politicalmap.base.politics.FilteredPolitics;
import kmu.maplayers.politicalmap.base.render.style.MapPalettes;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Bakes one bloc's cells into the {@link FactionTerritory} the renderer paints: its fill and
 * its national border, traced across every system it holds so a multi-system cluster reads as
 * one continuous frontier.
 *
 * <p>Fill and border come from the same loops - triangulated for the one, flattened for the
 * other - so they can never drift apart, whichever smoothing passes ran. Disjoint clusters and
 * enclaves each come back as their own ring, so rebuilding a bloc from its current members
 * alone re-splits or re-merges its clusters when the incremental refresh gains or loses one.
 *
 * <p>Membership is the cells a bloc draws, not the systems it holds: those differ wherever a
 * bloc's ground includes a cell no star of its own sits in, and it is the cells that carry the
 * edges a border is traced from.
 */
public final class FactionTerritoryBuilder {

    // Builds only; never instantiated.
    private FactionTerritoryBuilder() {
    }

    /**
     * Builds one bloc's territory from its member cells.
     *
     * @param territories   this pass's retained ownership, theme, and filter state
     * @param geometryCache the raw cells the border is traced from
     * @param blocId        the bloc's grouping key - a faction id under the faction view, or
     *                      one of the filter's synthetic spotlight keys
     * @param memberCellIds the cells this bloc draws
     * @return the bloc's fill and border, or null when it paints neither, or when its cells
     *         yield no borderable geometry
     */
    public static FactionTerritory buildFactionTerritory(
            PoliticalMapTerritories territories,
            CellGeometryCache geometryCache,
            String blocId,
            List<String> memberCellIds) {

        var cellGrouping = resolveCellGroupingOf(territories, geometryCache);

        // A desaturated bloc's fill and border swap to the pass's shared desaturation palette
        // instead of its own two shades, exactly as its cells' seams do - both read this one call.
        var styling = territories.resolveBlocStyling(blocId);
        var style = styling.style();
        var adjustment = styling.adjustment();

        // Every system of a bloc shares its palette, so any member resolves the same colours.
        var owner = territories.getOwnerBySystemId()
                .get(cellGrouping.resolveDrawnSystemIdOf(memberCellIds.get(0)));
        var palette = MapPalettes.resolveEffectivePalette(
                adjustment,
                owner,
                territories.getDesaturationPalette());
        var fillColor = MapPalettes.pickPaletteColor(
                style.fill().color(),
                palette.primaryColor(),
                palette.secondaryColor());
        var borderColor = MapPalettes.pickPaletteColor(
                style.outer().color(),
                palette.primaryColor(),
                palette.secondaryColor());
        if (fillColor == null && borderColor == null) {
            return null;
        }

        // One trace for the whole territory: the national border's rings, and - for a spotlit
        // footprint - the per-state sub-region rings its fill splits into, so border and fill
        // offset under identical parameters and cannot drift apart.
        var borderTrace = ClusterBorderTrace.readFromLunaSettings();
        var insetRings = borderTrace.traceRings(
                memberCellIds,
                geometryCache.getCellEdgesByCellId(),
                cellGrouping);
        if (insetRings.isEmpty()) {
            return null;
        }
        var borderLoops = resolveSmoothedBorderLoops(
                insetRings,
                territories.getGlobalStyle().borderSmoothing());

        // The spotlighted bloc's whole footprint - dominated and contested systems alike - shares
        // one key, so it clusters into this single territory outlined by one frontier and then
        // splits its fill inside it. Whether that split happens is the fill builder's own call.
        var fill = new SplitFillBuilder(
                geometryCache.getCellEdgesByCellId(),
                cellGrouping,
                borderTrace,
                borderLoops,
                territories.getGlobalStyle().hatch())
                .buildFill(
                        FilteredPolitics.isSpotlitBloc(blocId),
                        FillSplit.splitMembersByFillState(
                                cellGrouping,
                                memberCellIds,
                                territories.getContestedSystemIds(),
                                territories.getUnfilledSystemIds()),
                        blocId,
                        fillColor);
        return new FactionTerritory(
                fill.solidTriangles(),
                fill.hatchSegments(),
                new UiElementPaint(
                        fillColor,
                        adjustment.muteOpacity(style.fill().opacity())),
                flattenBorderLoops(borderLoops, borderColor),
                new UiElementPaint(
                        borderColor,
                        adjustment.muteOpacity(style.outer().opacity())),
                (float) style.outerWidth());
    }

    /**
     * Builds every bloc's territory into the territories, keyed by its grouping key. Each bloc
     * is independent - its cluster(s) trace only its own cells - so the incremental refresh can
     * rebuild one entry without touching the rest.
     *
     * @param territories   the pass state to write the territories into
     * @param geometryCache the raw cells the borders are traced from
     */
    public static void buildAllFactionTerritories(
            PoliticalMapTerritories territories,
            CellGeometryCache geometryCache) {

        var grouped = resolveCellGroupingOf(territories, geometryCache).groupCellIdsByKey();
        for (var bloc : grouped.entrySet()) {
            var territory = buildFactionTerritory(
                    territories,
                    geometryCache,
                    bloc.getKey(),
                    bloc.getValue());
            if (territory != null) {
                territories.getFactionTerritoryByFactionId().put(bloc.getKey(), territory);
            }
        }
    }

    // Resolves the traced rings to their clean outer envelope first (positive winding drops any
    // neck self-crossing), then hands them to the shared smoothing passes. Smoothing before the
    // resolve would have any arc clipped off at the crossing and left a sharp corner; which
    // passes run, and in which order, is the profile's own answer rather than restated here.
    private static List<List<double[]>> resolveSmoothedBorderLoops(
            List<List<double[]>> insetRings,
            BorderSmoothingStyle borderSmoothing) {

        return BorderSmoothing.smoothBorderLoops(
                PolygonTessellator.tessellateToBoundaryLoops(insetRings),
                borderSmoothing);
    }

    // The border's stroked runs, one per closed loop. A "No color" border bakes none at all
    // rather than runs the draw pass would skip.
    private static List<float[]> flattenBorderLoops(
            List<List<double[]>> borderLoops,
            Color borderColor) {

        var borderRuns = new ArrayList<float[]>();
        if (borderColor == null) {
            return borderRuns;
        }
        for (var loop : PolygonTessellator.tessellateToBoundaryLoops(borderLoops)) {
            borderRuns.add(GlVertexRuns.flattenVertices(loop));
        }
        return borderRuns;
    }

    // The drawn cells' grouping this bloc is traced against: which system each cell draws as,
    // off the geometry cache, paired with each owned system's bloc key.
    private static CellGrouping resolveCellGroupingOf(
            PoliticalMapTerritories territories,
            CellGeometryCache geometryCache) {

        return DominantOwner.mapCellGrouping(
                geometryCache.getSystemIdByCellId(),
                territories.getOwnerBySystemId());
    }
}
