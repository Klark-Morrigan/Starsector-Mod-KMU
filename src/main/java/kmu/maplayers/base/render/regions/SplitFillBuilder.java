package kmu.maplayers.base.render.regions;

import kmlib.opengl.GlVertexRuns;
import kmlib.opengl.Hatching;
import kmlib.opengl.PolygonTessellator;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.render.regions.FillSplit.FillState;
import kmu.maplayers.base.theme.HatchStyle;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns one region's {@link FillSplit} into the triangles and hatch lines its fill
 * paints, inside the single frontier the footprint already traced.
 *
 * <p>Built per territory around the trace context that whole fill shares - the cells, their
 * grouping, the border trace, the smoothed border loops, and the hatch geometry - so the
 * several tessellation steps read one consistent snapshot instead of threading the lot
 * through each hop.
 *
 * <p>Each drawn state fills from its own traced rings rather than from its members'
 * individual cells, so no per-cell inset truncation can leave an unfilled wedge where two
 * members meet at a corner against a rival. Both drawn fills are then clipped to the smoothed
 * frontier loops, so neither keeps the mitered corner the border's rounding cut and
 * pokes out past the line the border strokes.
 */
public final class SplitFillBuilder {
    // The suffixes that split a region's one grouping key into a key per fill state, so the border
    // tracer traces the solid, hatched, and unfilled members as separate regions rather than the
    // one body their shared key makes them. Appended to the region's own key, which already carries
    // a sentinel prefix no real grouping key can hold, so no derived key can collide with another
    // region's.
    private static final String SOLID_SUB_REGION_SUFFIX = "#solid";
    private static final String HATCHED_SUB_REGION_SUFFIX = "#hatched";
    private static final String UNFILLED_SUB_REGION_SUFFIX = "#unfilled";

    private final Map<String, List<CellEdge>> cellEdgesByCellId;
    private final CellGrouping cellGrouping;
    private final ClusterBorderTrace borderTrace;
    private final List<List<double[]>> borderLoops;
    private final HatchStyle hatch;

    /**
     * @param cellEdgesByCellId the raw cell adjacency the sub-region rings are traced from -
     *                          the same map the trace itself takes, rather than whatever cache
     *                          the caller happens to hold it in
     * @param cellGrouping      which system each cell draws as, paired with each system's
     *                          grouping key - the keys the sub-regions are derived from
     * @param borderTrace       the trace parameters the whole fill shares with its border
     * @param borderLoops       the smoothed frontier every drawn state is clipped to
     * @param hatch             the sector-wide hatch geometry the hatched sub-region is cut with
     */
    public SplitFillBuilder(
            Map<String, List<CellEdge>> cellEdgesByCellId,
            CellGrouping cellGrouping,
            ClusterBorderTrace borderTrace,
            List<List<double[]>> borderLoops,
            HatchStyle hatch) {

        this.cellEdgesByCellId = cellEdgesByCellId;
        this.cellGrouping = cellGrouping;
        this.borderTrace = borderTrace;
        this.borderLoops = borderLoops;
        this.hatch = hatch;
    }

    /**
     * Builds one territory's fill, taking the per-state split only where it is needed.
     *
     * <p>A territory whose members do not all fill solid splits its fill per state inside its
     * one frontier - one area per {@link FillState} - so the states read apart without the
     * border fracturing. A spotlit region always splits, since its fill is per-state even when
     * every member is in the same state. Every other territory fills solid as one region
     * tessellated from the same smoothed loops the border strokes, so fill and border match
     * exactly and the split's cost is paid only by the territories that need it.
     *
     * @param isSpotlit  whether this is the filter's spotlighted footprint
     * @param split      the footprint's members by fill state
     * @param regionKey  the region's grouping key, which the sub-region keys are derived from
     * @param fillColor  the resolved fill colour, or null for a "No color" fill that draws
     *                   no region at all
     * @return the fill's solid triangles and hatch segments
     */
    public RegionFill buildFill(
            boolean isSpotlit,
            FillSplit split,
            String regionKey,
            Color fillColor) {

        if (fillColor == null) {
            return new RegionFill(
                    GlVertexRuns.NO_VERTICES,
                    GlVertexRuns.NO_VERTICES);
        }
        if (!isSpotlit && !split.hasNonSolidMembers()) {
            return new RegionFill(
                    PolygonTessellator.tessellateToTriangles(borderLoops),
                    GlVertexRuns.NO_VERTICES);
        }
        return buildPerStateFill(split, regionKey);
    }

    // Tessellates each drawn state as its own region: the solid members into the triangle soup,
    // the hatched members into their own soup the hatch generator then clips diagonal lines to.
    // The unfilled state is deliberately never tessellated - it holds ground for the region's
    // border and label but paints no fill of its own.
    private RegionFill buildPerStateFill(FillSplit split, String regionKey) {
        var subRegionKeys = mapSubRegionKeyBySystemId(split, regionKey);
        var solidTriangles = tessellateSubRegion(FillState.SOLID, split, subRegionKeys);
        var hatchedTriangles = tessellateSubRegion(FillState.HATCHED, split, subRegionKeys);
        return new RegionFill(
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
    // frontier draws it. Suffixing the footprint's own key leaves the derived keys as
    // collision-free as it already is.
    private Map<String, String> mapSubRegionKeyBySystemId(FillSplit split, String regionKey) {
        var keys = new HashMap<>(cellGrouping.ownerBySystemId());
        putSubRegionKeys(keys, split, FillState.SOLID, regionKey + SOLID_SUB_REGION_SUFFIX);
        putSubRegionKeys(keys, split, FillState.HATCHED, regionKey + HATCHED_SUB_REGION_SUFFIX);
        putSubRegionKeys(keys, split, FillState.UNFILLED, regionKey + UNFILLED_SUB_REGION_SUFFIX);
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
    // rings are clipped to the smoothed frontier loops rather than tessellated as traced:
    // their shared inter-state seam is interior to both operands and survives the clip untouched, so
    // the states still meet exactly along it, while their outer edge is clamped onto the exact
    // line the frontier strokes. Empty when the state holds no members or the trace yields
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
                cellEdgesByCellId,
                new CellGrouping(cellGrouping.systemIdByCellId(), subRegionKeyBySystemId),
                split.resolveCoincidentSystemIdsOf(state));
        if (rings.isEmpty()) {
            return GlVertexRuns.NO_VERTICES;
        }
        return PolygonTessellator.tessellateIntersectionToTriangles(rings, borderLoops);
    }

    /**
     * One region's fill as the two runs it paints: the solid triangle soup for the solid-state
     * cells and the hatch GL_LINES for the hatched ones. Named for the region rather than for
     * what holds it, since the pair is the same two runs whatever a layer groups its cells by.
     *
     * <p>The unfilled state carries no geometry - it paints nothing - so a region with no
     * hatched members leaves the hatch empty and one that fills solid throughout carries only
     * its solid triangles.
     */
    public record RegionFill(float[] solidTriangles, float[] hatchSegments) {
    }
}
