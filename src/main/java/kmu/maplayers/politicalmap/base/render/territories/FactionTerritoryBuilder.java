package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.math.geometry.PolygonRegions;
import kmlib.math.geometry.RingRegion;
import kmlib.opengl.GlVertexRuns;
import kmlib.opengl.PolygonTessellator;
import kmlib.starsector.ui.render.gl.UiElementPaint;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.render.clusters.BorderSmoothing;
import kmu.maplayers.base.render.clusters.ClusterBorderTrace;
import kmu.maplayers.base.render.clusters.FillSplit;
import kmu.maplayers.base.render.clusters.HatchBuildDiagnostics;
import kmu.maplayers.base.render.clusters.SplitFillBuilder;
import kmu.maplayers.base.render.clusters.StyledCluster;
import kmu.maplayers.base.render.clusters.StyledClusterGroup;
import kmu.maplayers.base.render.clusters.TracedFill;
import kmu.maplayers.base.theme.BorderSmoothingStyle;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.politics.FilteredPolitics;
import kmu.maplayers.politicalmap.base.render.style.MapPalettes;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Bakes one bloc's cells into the {@link StyledClusterGroup} the renderer paints: every body
 * the bloc holds as its own {@link StyledCluster}, traced across the systems in it so a
 * multi-system body reads as one continuous frontier, under the one set of paints the bloc
 * draws in wherever it is.
 *
 * <p>What it produces is a framework draw record, but what it decides is political throughout -
 * whose palette the fill resolves against, which systems the filter has left contested, which
 * cells the spotlight recedes. So it keeps its own vocabulary and stays on this side of the
 * seam, handing over a record that says nothing about factions.
 *
 * <p>Fill and border come from the same loops - triangulated for the one, flattened for the
 * other - so they can never drift apart, whichever smoothing passes ran. Disjoint bodies and
 * enclaves each come back as their own ring and are sorted into bodies from their winding and
 * containment, so rebuilding a bloc from its current members alone re-splits or re-merges its
 * bodies when the incremental refresh gains or loses one.
 *
 * <p>Membership is the cells a bloc draws, not the systems it holds: those differ wherever a
 * bloc's territory includes a cell no star of its own sits in, and it is the cells that carry the
 * edges a border is traced from.
 */
public final class FactionTerritoryBuilder {

    // Builds only; never instantiated.
    private FactionTerritoryBuilder() {
    }

    /**
     * Builds one bloc's territory from its member cells.
     *
     * @param territories   this pass's retained holding, theme, and filter state
     * @param geometryCache the raw cells the border is traced from
     * @param blocId        the bloc's holder - a faction id under the faction view, or
     *                      one of the filter's synthetic spotlight keys
     * @param memberCellIds the cells this bloc draws
     * @return the bloc's bodies with the paints they share, or null when it paints neither fill
     *         nor border, or when its cells yield no borderable geometry
     */
    public static StyledClusterGroup buildFactionTerritory(
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
        var holder = territories
            .getHolderBySystemId()
            .get(cellGrouping.resolveDrawnSystemIdOf(memberCellIds.get(0)));

        var palette = MapPalettes.resolveEffectivePalette(
            adjustment,
            holder,
            territories.getDesaturationPalette());

        var fillColour = MapPalettes.pickPaletteColour(
            style.fill().colour(),
            palette);

        var borderColour = MapPalettes.pickPaletteColour(
            style.outer().colour(),
            palette);

        if (fillColour == null && borderColour == null) {
            return null;
        }

        // One trace for the whole territory: the national border's rings, and - for a spotlit
        // footprint - the per-state sub-cluster rings its fill splits into, so border and fill
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

        // The bloc's loops sorted back into the bodies they bound: a bloc holding cells in two
        // places traces two outer rings plus whatever enclaves each encloses, and which enclave
        // belongs to which body is what decides where its fill stops.
        var clusterRegions = PolygonRegions.groupRingsIntoRegions(borderLoops);
        if (clusterRegions.isEmpty()) {
            return null;
        }

        // The spotlighted bloc's whole footprint - dominated and contested systems alike - shares
        // one key, so it clusters under one bloc and then splits its fill inside each of its
        // bodies. Whether that split happens is the fill builder's own call.
        var tracedFill = new SplitFillBuilder(
                geometryCache.getCellEdgesByCellId(),
                cellGrouping,
                borderTrace,
                territories.getGlobalStyle().hatch(),
                HatchBuildDiagnostics.createRunObserver())
            .traceFill(
                FilteredPolitics.isSpotlitBloc(blocId),
                FillSplit.splitMembersByFillState(
                    cellGrouping,
                    memberCellIds,
                    territories.getContestedSystemIds(),
                    territories.getUnfilledSystemIds()),
                blocId,
                fillColour);

        return new StyledClusterGroup(
            buildClusters(clusterRegions, tracedFill, borderColour),
            new UiElementPaint(
                fillColour,
                adjustment.muteOpacity(style.fill().opacity())),
            new UiElementPaint(
                borderColour,
                adjustment.muteOpacity(style.outer().opacity())),
            (float) style.outerWidth());
    }

    /**
     * Builds every bloc's territory into the territories, keyed by its holder. Each bloc
     * is independent - its cluster(s) trace only its own cells - so the incremental refresh can
     * rebuild one entry without touching the rest.
     *
     * @param territories   the pass state to write the territories into
     * @param geometryCache the raw cells the borders are traced from
     */
    public static void buildAllFactionTerritories(
            PoliticalMapTerritories territories,
            CellGeometryCache geometryCache) {

        var grouped = resolveCellGroupingOf(territories, geometryCache).groupCellIdsByOwner();
        for (var bloc : grouped.entrySet()) {

            var territory = buildFactionTerritory(
                territories,
                geometryCache,
                bloc.getKey(),
                bloc.getValue());

            if (territory != null) {
                territories.getStyledClusterGroupByOwnerId().put(bloc.getKey(), territory);
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

        // Resolved a second time after smoothing, because rounding a corner can push one arc
        // through another and open a crossing the first resolve could not have seen. That also
        // leaves every loop in the winding convention the grouping reads - outer
        // counter-clockwise, enclave clockwise - which a smoothed-but-unresolved loop is not
        // guaranteed to be.
        return PolygonTessellator.tessellateToBoundaryLoops(
            BorderSmoothing.smoothBorderLoops(
                PolygonTessellator.tessellateToBoundaryLoops(insetRings),
                borderSmoothing));
    }

    // Cuts each body's fill and flattens its loops into the GL runs the stroke walks. The fill is
    // asked for where the body is named, so a body cannot be paired with another body's geometry.
    // A "No color" border bakes no loops at all rather than runs the draw pass would skip; the
    // body still carries its fill, which is what an unstroked bloc draws.
    private static List<StyledCluster> buildClusters(
            List<RingRegion> clusterRegions,
            TracedFill tracedFill,
            Color borderColour) {

        var isBorderDrawn = borderColour != null;
        var clusters = new ArrayList<StyledCluster>(clusterRegions.size());
        for (var clusterRegion : clusterRegions) {
            var fill = tracedFill.buildFillFor(clusterRegion);
            clusters.add(new StyledCluster(
                fill.solidTriangles(),
                fill.hatchSegments(),
                isBorderDrawn
                    ? GlVertexRuns.flattenVertices(clusterRegion.outerRing())
                    : GlVertexRuns.NO_VERTICES,
                isBorderDrawn
                    ? flattenLoops(clusterRegion.holeRings())
                    : List.of()));
        }
        return clusters;
    }

    // One stroked run per closed loop, in the order the loops arrived.
    private static List<float[]> flattenLoops(List<List<double[]>> loops) {
        var runs = new ArrayList<float[]>(loops.size());
        for (var loop : loops) {
            runs.add(GlVertexRuns.flattenVertices(loop));
        }
        return runs;
    }

    // The drawn cells' grouping this bloc is traced against: which system each cell draws as,
    // off the geometry cache, paired with each owned system's bloc key.
    private static CellGrouping resolveCellGroupingOf(
            PoliticalMapTerritories territories,
            CellGeometryCache geometryCache) {

        return DominantHolder.mapCellGrouping(
            geometryCache.getSystemIdByCellId(),
            territories.getHolderBySystemId());
    }
}
