package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.opengl.GlVertexRuns;
import kmlib.opengl.Hatching;
import kmlib.opengl.PolygonTessellator;

import kmu.maplayers.politicalmap.base.geometry.CellGrouping;
import kmu.maplayers.politicalmap.base.geometry.PoliticalMapGeometryCache;
import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.maplayers.politicalmap.base.render.PoliticalBorderTrace;
import kmu.maplayers.politicalmap.base.render.territories.FillSplit.FillState;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns one bloc footprint's {@link FillSplit} into the triangles and hatch lines its fill
 * paints, inside the single national border the footprint already traced.
 *
 * <p>Built per territory around the trace context that whole fill shares - the cells, their
 * grouping, the border trace, and the smoothed border loops - so the several tessellation
 * steps read one consistent snapshot instead of threading five arguments through each hop.
 *
 * <p>Each drawn state fills from its own traced rings rather than from its members'
 * individual cells, so no per-cell inset truncation can leave an unfilled wedge where two
 * members meet at a corner against a rival. Both drawn fills are then clipped to the smoothed
 * national border loops, so neither keeps the mitered corner the border's rounding cut and
 * pokes out past the frontier the border strokes.
 */
final class SplitFillBuilder {
    // The suffixes that split a bloc's one grouping key into a key per fill state, so the border
    // tracer traces the solid, hatched, and unfilled members as separate regions rather than the
    // one body their shared key makes them. Appended to the bloc's own key, which already carries a
    // sentinel prefix no real bloc id can hold, so no derived key can collide with a rival's.
    private static final String SOLID_SUB_REGION_SUFFIX = "#solid";
    private static final String HATCHED_SUB_REGION_SUFFIX = "#hatched";
    private static final String UNFILLED_SUB_REGION_SUFFIX = "#unfilled";

    private final PoliticalMapTerritories territories;
    private final PoliticalMapGeometryCache geometryCache;
    private final CellGrouping cellGrouping;
    private final PoliticalBorderTrace borderTrace;
    private final List<List<double[]>> borderLoops;

    SplitFillBuilder(
            PoliticalMapTerritories territories,
            PoliticalMapGeometryCache geometryCache,
            CellGrouping cellGrouping,
            PoliticalBorderTrace borderTrace,
            List<List<double[]>> borderLoops) {

        this.territories = territories;
        this.geometryCache = geometryCache;
        this.cellGrouping = cellGrouping;
        this.borderTrace = borderTrace;
        this.borderLoops = borderLoops;
    }

    /**
     * Builds one territory's fill, taking the per-state split only where it is needed.
     *
     * <p>A territory whose members do not all fill solid splits its fill per state inside its
     * one frontier - solid where the bloc holds, hatched where it is present but dominated,
     * empty where it is held-but-unfilled - so the states read apart without the border
     * fracturing. A spotlit bloc always splits, since its fill is per-state even when it
     * dominates everywhere it is present. Every other territory fills solid as one region
     * tessellated from the same smoothed loops the border strokes, so fill and border match
     * exactly and the split's cost is paid only by the territories that need it.
     *
     * @param isSpotlit  whether this is the filter's spotlighted footprint
     * @param split      the footprint's members by fill state
     * @param blocId     the bloc's grouping key, which the sub-region keys are derived from
     * @param fillColor  the resolved fill colour, or null for a "No color" fill that draws
     *                   no region at all
     * @return the fill's solid triangles and hatch segments
     */
    TerritoryFill buildFill(
            boolean isSpotlit,
            FillSplit split,
            String blocId,
            Color fillColor) {

        if (fillColor == null) {
            return new TerritoryFill(
                    GlVertexRuns.NO_VERTICES,
                    GlVertexRuns.NO_VERTICES);
        }
        if (!isSpotlit && !split.hasNonSolidMembers()) {
            return new TerritoryFill(
                    PolygonTessellator.tessellateToTriangles(borderLoops),
                    GlVertexRuns.NO_VERTICES);
        }
        return buildPerStateFill(split, blocId);
    }

    // Tessellates each drawn state as its own region: the solid members into the triangle soup,
    // the contested members into their own soup the hatch generator then clips diagonal lines to.
    // The unfilled state is deliberately never tessellated - it holds ground for the bloc's border
    // and label but paints no fill of its own.
    private TerritoryFill buildPerStateFill(FillSplit split, String blocId) {
        var subRegionKeys = mapSubRegionKeyBySystemId(split, blocId);
        var solidTriangles = tessellateSubRegion(FillState.SOLID, split, subRegionKeys);
        var hatchedTriangles = tessellateSubRegion(FillState.HATCHED, split, subRegionKeys);
        var hatch = territories.getGlobalStyle().hatch();
        return new TerritoryFill(
                solidTriangles,
                Hatching.computeHatchSegments(
                        hatchedTriangles,
                        hatch.angleRadians(),
                        hatch.spacing()));
    }

    // Keys the footprint's three fill states apart, so the border tracer - which fuses cells sharing
    // a key - traces the solid, hatched, and unfilled members as separate regions rather than as the
    // one body their shared footprint key makes them. Every system outside the footprint keeps its
    // real key, so an edge from a member to a rival or to empty space classifies exactly as it does
    // when the whole footprint is traced, and the sub-regions' outer edge therefore lands where the
    // national border draws it. Suffixing the footprint's own key leaves the derived keys as
    // collision-free as it already is.
    private Map<String, String> mapSubRegionKeyBySystemId(FillSplit split, String blocId) {
        var keys = new HashMap<>(
                DominantOwner.mapFactionIdBySystemId(territories.getOwnerBySystemId()));
        putSubRegionKeys(keys, split, FillState.SOLID, blocId + SOLID_SUB_REGION_SUFFIX);
        putSubRegionKeys(keys, split, FillState.HATCHED, blocId + HATCHED_SUB_REGION_SUFFIX);
        putSubRegionKeys(keys, split, FillState.UNFILLED, blocId + UNFILLED_SUB_REGION_SUFFIX);
        return keys;
    }

    private static void putSubRegionKeys(
            Map<String, String> keys,
            FillSplit split,
            FillState state,
            String subRegionKey) {

        for (var systemId : split.resolveMembersOf(state).systemIds()) {
            keys.put(systemId, subRegionKey);
        }
    }

    // Tessellates one of the footprint's states into a GL_TRIANGLES soup from the rings tracing
    // its cells as a single region, so a state fills as one continuous area with no per-cell
    // seam or truncation inside it. The other states' systems are the coincident neighbours, whose
    // shared edge insets by nothing so the states abut with no channel between them. The traced
    // rings are clipped to the smoothed national border loops rather than tessellated as traced:
    // their shared inter-state seam is interior to both operands and survives the clip untouched, so
    // the states still meet exactly along it, while their outer edge is clamped onto the exact
    // line the national border strokes. Empty when the state holds no members or the trace yields
    // no drawable ring.
    private float[] tessellateSubRegion(
            FillState state,
            FillSplit split,
            Map<String, String> subRegionKeyBySystemId) {

        var members = split.resolveMembersOf(state);
        if (members.cellIds().isEmpty()) {
            return GlVertexRuns.NO_VERTICES;
        }
        var rings = borderTrace.traceRings(
                members.cellIds(),
                geometryCache.getCellEdgesByCellId(),
                new CellGrouping(cellGrouping.systemIdByCellId(), subRegionKeyBySystemId),
                split.resolveCoincidentSystemIdsOf(state));
        if (rings.isEmpty()) {
            return GlVertexRuns.NO_VERTICES;
        }
        return PolygonTessellator.tessellateIntersectionToTriangles(rings, borderLoops);
    }

    /**
     * A bloc footprint's fill as the two runs it paints: the solid triangle soup for the held
     * cells and the hatch GL_LINES for the contested ones.
     *
     * <p>The unfilled state carries no geometry - it paints nothing - so a territory with no
     * hatched members leaves the hatch empty and one that fills solid throughout carries only
     * its solid region.
     */
    record TerritoryFill(float[] solidTriangles, float[] hatchSegments) {
    }
}
