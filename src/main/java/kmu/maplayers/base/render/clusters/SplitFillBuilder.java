package kmu.maplayers.base.render.clusters;

import kmlib.opengl.GlVertexRuns;
import kmlib.opengl.Hatching;
import kmlib.opengl.PolygonTessellator;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.render.clusters.FillSplit.FillState;
import kmu.maplayers.base.theme.HatchStyle;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns one cluster's {@link FillSplit} into the triangles and hatch lines its fill
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

    // The suffixes that split a cluster's one owner into a key per fill state, so the border
    // tracer traces the solid, hatched, and unfilled members as separate clusters rather than the
    // one body their shared key makes them. Appended to the cluster's own key, which already carries
    // a sentinel prefix no real owner can hold, so no derived key can collide with another
    // cluster's.
    private static final String SOLID_SUB_CLUSTER_SUFFIX = "#solid";
    private static final String HATCHED_SUB_CLUSTER_SUFFIX = "#hatched";
    private static final String UNFILLED_SUB_CLUSTER_SUFFIX = "#unfilled";

    private final Map<String, List<CellEdge>> cellEdgesByCellId;
    private final CellGrouping cellGrouping;
    private final ClusterBorderTrace borderTrace;
    private final List<List<double[]>> borderLoops;
    private final HatchStyle hatch;

    /**
     * @param cellEdgesByCellId the raw cell adjacency the sub-cluster rings are traced from -
     *                          the same map the trace itself takes, rather than whatever cache
     *                          the caller happens to hold it in
     * @param cellGrouping      which system each cell draws as, paired with each system's
     *                          owner - the keys the sub-clusters are derived from
     * @param borderTrace       the trace parameters the whole fill shares with its border
     * @param borderLoops       the smoothed frontier every drawn state is clipped to
     * @param hatch             the sector-wide hatch geometry the hatched sub-cluster is cut with
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
     * border fracturing. A spotlit cluster always splits, since its fill is per-state even when
     * every member is in the same state. Every other territory fills solid as one cluster
     * tessellated from the same smoothed loops the border strokes, so fill and border match
     * exactly and the split's cost is paid only by the territories that need it.
     *
     * @param isSpotlit  whether this is the filter's spotlighted footprint
     * @param split      the footprint's members by fill state
     * @param owner  the cluster's owner, which the sub-cluster keys are derived from
     * @param fillColor  the resolved fill colour, or null for a "No color" fill that draws
     *                   no cluster at all
     * @return the fill's solid triangles and hatch segments
     */
    public ClusterFill buildFill(
            boolean isSpotlit,
            FillSplit split,
            String owner,
            Color fillColor) {

        if (fillColor == null) {
            return new ClusterFill(
                GlVertexRuns.NO_VERTICES,
                GlVertexRuns.NO_VERTICES);
        }
        if (!isSpotlit && !split.hasNonSolidMembers()) {
            return new ClusterFill(
                PolygonTessellator.tessellateToTriangles(borderLoops),
                GlVertexRuns.NO_VERTICES);
        }
        return buildPerStateFill(split, owner);
    }

    // Tessellates each drawn state as its own cluster: the solid members into the triangle soup,
    // the hatched members into their own soup the hatch generator then clips diagonal lines to.
    // The unfilled state is deliberately never tessellated - it holds ground for the cluster's
    // border and label but paints no fill of its own.
    private ClusterFill buildPerStateFill(FillSplit split, String owner) {

        var subClusterOwners = mapSubClusterOwnerBySystemId(split, owner);
        var solidTriangles = tessellateSubCluster(FillState.SOLID, split, subClusterOwners);
        var hatchedTriangles = tessellateSubCluster(FillState.HATCHED, split, subClusterOwners);

        return new ClusterFill(
            solidTriangles,
            Hatching.computeHatchSegments(
                hatchedTriangles,
                hatch.angleRadians(),
                hatch.spacing()));
    }

    // Keys the footprint's three fill states apart, so the border tracer - which fuses cells sharing
    // a key - traces the solid, hatched, and unfilled members as separate clusters rather than as the
    // one body their shared footprint key makes them. Every system outside the footprint keeps its
    // real key, so an edge from a member to a rival or to empty space classifies exactly as it does
    // when the whole footprint is traced, and the sub-clusters' outer edge therefore lands where the
    // frontier draws it. Suffixing the footprint's own key leaves the derived keys as
    // collision-free as it already is.
    private Map<String, String> mapSubClusterOwnerBySystemId(FillSplit split, String owner) {

        var keys = new HashMap<>(cellGrouping.ownerBySystemId());

        putSubClusterOwners(keys, split, FillState.SOLID, owner + SOLID_SUB_CLUSTER_SUFFIX);
        putSubClusterOwners(keys, split, FillState.HATCHED, owner + HATCHED_SUB_CLUSTER_SUFFIX);
        putSubClusterOwners(keys, split, FillState.UNFILLED, owner + UNFILLED_SUB_CLUSTER_SUFFIX);

        return keys;
    }

    private static void putSubClusterOwners(
            Map<String, String> keys,
            FillSplit split,
            FillState state,
            String subClusterOwner) {

        for (var systemId : split.resolveMembersOf(state).systemIds()) {
            keys.put(systemId, subClusterOwner);
        }
    }

    // Tessellates one of the footprint's states into a GL_TRIANGLES soup from the rings tracing
    // its cells as a single cluster, so a state fills as one continuous area with no per-cell
    // seam or truncation inside it. The other states' systems are the coincident neighbours, whose
    // shared edge insets by nothing so the states abut with no channel between them. The traced
    // rings are clipped to the smoothed frontier loops rather than tessellated as traced:
    // their shared inter-state seam is interior to both operands and survives the clip untouched, so
    // the states still meet exactly along it, while their outer edge is clamped onto the exact
    // line the frontier strokes. Empty when the state holds no members or the trace yields
    // no drawable ring.
    private float[] tessellateSubCluster(
            FillState state,
            FillSplit split,
            Map<String, String> subClusterOwnerBySystemId) {

        var members = split.resolveMembersOf(state);
        if (members.cellIds().isEmpty()) {
            return GlVertexRuns.NO_VERTICES;
        }
        var rings = borderTrace.traceRings(
            members.cellIds(),
            cellEdgesByCellId,
            new CellGrouping(cellGrouping.systemIdByCellId(), subClusterOwnerBySystemId),
            split.resolveCoincidentSystemIdsOf(state));

        if (rings.isEmpty()) {
            return GlVertexRuns.NO_VERTICES;
        }
        return PolygonTessellator.tessellateIntersectionToTriangles(rings, borderLoops);
    }

    /**
     * One cluster's fill as the two runs it paints: the solid triangle soup for the solid-state
     * cells and the hatch GL_LINES for the hatched ones. Named for the cluster rather than for
     * what holds it, since the pair is the same two runs whatever a layer groups its cells by.
     *
     * <p>The unfilled state carries no geometry - it paints nothing - so a cluster with no
     * hatched members leaves the hatch empty and one that fills solid throughout carries only
     * its solid triangles.
     */
    public record ClusterFill(
        float[] solidTriangles,
        float[] hatchSegments) {
    }
}
