package kmu.maplayers.politicalmap.base.render.hover;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.PolygonRegions;
import kmlib.opengl.GlVertexRuns;
import kmlib.opengl.PolygonTessellator;

import kmu.maplayers.base.hover.HoverHighlight;
import kmu.maplayers.base.hover.PoliticalMapHover;
import kmu.maplayers.politicalmap.base.render.territories.FactionTerritory;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;

import java.util.List;

/**
 * Works out what a hover lights up: which of the hovered faction's border loops encloses the
 * hovered cell, and that cell's own painted extent as fillable geometry.
 *
 * <p>The loop has to be searched for because the map bakes its national borders per faction,
 * not per cluster: a faction's {@link FactionTerritory#borderLoops()} carries one loop for
 * each of its disjoint clusters and one for each enclave bitten out of them, with nothing
 * naming which is which. The cluster the cursor is in is therefore identified geometrically -
 * by which loop contains the hovered cell - rather than by an index, which would mean keying
 * the whole territory build per cluster to answer a question only the hover asks.
 *
 * <p>Two details make that search exact. The cell is represented by the average of its
 * vertices rather than by the cursor itself: a cursor a pixel inside the cell's edge can fall
 * outside a frontier whose corners rounding has cut inward, which would drop the halo just as
 * the player pushes into a corner. And where loops nest - a faction's enclave inside a rival
 * inside that same faction's own cluster - three loops contain the point, so the smallest one
 * wins, which is the enclave's own frontier rather than the distant cluster's.
 *
 * <p>Both answers are memoised against the hovered cell and the geometry behind it, since
 * they change only when the cursor crosses into another cell or a rebuild replaces that
 * cell's shape - not sixty times a second while it rests on one.
 */
public final class HoverHighlightGeometry {
    // What the retained answer was resolved from. Held by identity, not by value: a rebuild or
    // an incremental re-shape replaces the whole record and the whole polygon rather than
    // editing either in place, so a reference that still matches is the same geometry.
    private String resolvedSystemId;
    private FactionTerritory resolvedTerritory;
    private List<double[]> resolvedFillPolygon;
    private HoverHighlight resolvedHighlight = HoverHighlight.NONE;

    /**
     * The geometry the current hover lights up.
     *
     * @param territories the frame's draw lists - the hovered cell's shape and its faction's
     *                    border loops both come from here, so the highlight can only ever
     *                    trace what was painted
     * @param hover       what the cursor is over this frame
     * @return the loops and runs to draw, or {@link HoverHighlight#NONE} when nothing is
     *         hovered or the hovered cell has no drawable shape
     */
    public HoverHighlight resolveHighlightFor(
            PoliticalMapTerritories territories,
            PoliticalMapHover hover) {
        if (!hover.isHovering()) {
            return HoverHighlight.NONE;
        }
        var systemId = hover.hoveredSystemId();
        var fillPolygon = territories.getFillPolygonByCellId().get(systemId);
        if (fillPolygon == null || fillPolygon.isEmpty()) {
            return HoverHighlight.NONE;
        }
        // A factionless cell (decivilised, or uninhabited while its outline is drawn) fuses
        // into no territory, so it has no frontier and resolves a null one - its cell still
        // washes.
        var owner = territories.getOwnerBySystemId().get(systemId);
        var territory = owner == null
                ? null
                : territories.getFactionTerritoryByFactionId().get(owner.factionId());
        if (systemId.equals(resolvedSystemId)
                && territory == resolvedTerritory
                && fillPolygon == resolvedFillPolygon) {
            return resolvedHighlight;
        }
        resolvedSystemId = systemId;
        resolvedTerritory = territory;
        resolvedFillPolygon = fillPolygon;
        resolvedHighlight = buildHighlight(territory, fillPolygon);
        return resolvedHighlight;
    }

    // Assembles the two halves: the frontier loop enclosing the cell, and the cell - clamped to
    // that frontier - washed and traced from one resolved set of loops.
    private static HoverHighlight buildHighlight(
            FactionTerritory territory,
            List<double[]> fillPolygon) {
        var enclosingLoop = findEnclosingLoop(territory, fillPolygon);
        // Resolve the wash to boundary loops once, then fill and trace both come off it - so the
        // wash and its outline are the same region by construction (as the territory fill and its
        // border already are), and the clip runs a single tessellation rather than one per half.
        var washLoops = clipCellToFrontier(fillPolygon, enclosingLoop);
        return new HoverHighlight(
                enclosingLoop == null ? List.of() : List.of(enclosingLoop),
                PolygonTessellator.tessellateToTriangles(washLoops),
                washLoops.stream().map(GlVertexRuns::flattenVertices).toList());
    }

    // The hovered cell as the boundary loops its wash fills and traces, clamped to the frontier it
    // sits inside so neither spills past the rounded national border - it stops at the exact line
    // the border strokes instead of keeping the sharp mitered corner the border's rounding cut
    // away, the same clip the territory fill applies to itself. A factionless or "No color" cell
    // has no frontier (null loop), so it resolves to the cell's own boundary. The clip can bite the
    // extent into more than one loop, so it returns however many the overlap has.
    private static List<List<double[]>> clipCellToFrontier(
            List<double[]> fillPolygon,
            float[] enclosingLoop) {
        var cell = List.of(fillPolygon);
        if (enclosingLoop == null) {
            return PolygonTessellator.tessellateToBoundaryLoops(cell);
        }
        return PolygonTessellator.tessellateIntersectionToBoundaryLoops(
                cell,
                List.of(GlVertexRuns.unflattenVertices(enclosingLoop)));
    }

    // The hovered cluster's frontier: the smallest of its faction's border loops that encloses
    // the cell, or null for a factionless cell, or when no loop encloses it - the border is
    // "No color", so the faction baked none. Smallest rather than first because nested loops all
    // enclose the point and only the innermost is the cell's own cluster; area is compared by
    // magnitude since a hole ring winds against its outer ring.
    private static float[] findEnclosingLoop(
            FactionTerritory territory,
            List<double[]> fillPolygon) {
        if (territory == null) {
            return null;
        }
        // A point standing in for the whole cell, well clear of its edges: the mean of its
        // vertices. Not the cursor, which can rest a pixel inside an edge.
        var point = Points.computeMean(fillPolygon);
        float[] smallestLoop = null;
        var smallestArea = Double.MAX_VALUE;
        for (var loop : territory.borderLoops()) {
            var ring = GlVertexRuns.unflattenVertices(loop);
            if (!PolygonRegions.isPointInsideRing(ring, point[0], point[1])) {
                continue;
            }
            var area = Math.abs(PolygonRegions.computeSignedArea(ring));
            if (area < smallestArea) {
                smallestArea = area;
                smallestLoop = loop;
            }
        }
        return smallestLoop;
    }

}
