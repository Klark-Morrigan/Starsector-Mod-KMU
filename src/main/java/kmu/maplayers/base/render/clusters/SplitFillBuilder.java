package kmu.maplayers.base.render.clusters;

import kmlib.math.geometry.RingRegion;
import kmlib.opengl.GlVertexRuns;
import kmlib.opengl.Hatching;
import kmlib.opengl.PolygonTessellator;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.render.clusters.FillSplit.FillState;
import kmu.maplayers.base.theme.HatchStyle;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns one owner's {@link FillSplit} into the triangles and hatch lines each of its clusters
 * fills, inside the boundary that cluster already traced.
 *
 * <p>Built per owner around the trace context the whole fill shares - the cells, their
 * grouping, the border trace, and the hatch geometry - so the several tessellation steps read
 * one consistent snapshot instead of threading the lot through each hop.
 *
 * <p>Each drawn state fills from its own traced rings rather than from its members'
 * individual cells, so no per-cell inset truncation can leave an unfilled wedge where two
 * members meet at a corner against a rival. Those rings are traced once for the owner and then
 * clipped to each cluster's smoothed loops in turn: the clip is what confines a state to the
 * body it is in, so a holding in two places needs one trace and one clip apiece rather than a
 * trace apiece. Clipping is also what stops a fill keeping the mitered corner the boundary's
 * rounding cut and poking out past the line that boundary strokes.
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
    private final HatchStyle hatch;

    /**
     * @param cellEdgesByCellId the raw cell adjacency the sub-cluster rings are traced from -
     *                          the same map the trace itself takes, rather than whatever cache
     *                          the caller happens to hold it in
     * @param cellGrouping      which system each cell draws as, paired with each system's
     *                          owner - the keys the sub-clusters are derived from
     * @param borderTrace       the trace parameters the whole fill shares with its border
     * @param hatch             the sector-wide hatch geometry the hatched sub-cluster is cut with
     */
    public SplitFillBuilder(
            Map<String, List<CellEdge>> cellEdgesByCellId,
            CellGrouping cellGrouping,
            ClusterBorderTrace borderTrace,
            HatchStyle hatch) {

        this.cellEdgesByCellId = cellEdgesByCellId;
        this.cellGrouping = cellGrouping;
        this.borderTrace = borderTrace;
        this.hatch = hatch;
    }

    /**
     * Builds one fill per cluster the owner holds, taking the per-state split only where it is
     * needed.
     *
     * <p>An owner whose members do not all fill solid splits each cluster's fill per state
     * inside that cluster's own boundary - one area per {@link FillState} - so the states read
     * apart without the boundary fracturing. A spotlit owner always splits, since its fill is
     * per-state even when every member is in the same state. Every other owner fills each
     * cluster solid from the same smoothed loops its boundary strokes, so fill and boundary
     * match exactly and the split's cost is paid only by the owners that need it.
     *
     * <p>Taken as every cluster at once rather than one call per cluster because the split's
     * rings are the owner's, not any one cluster's: they are traced once here and clipped per
     * cluster, where a call per cluster would re-trace the whole holding each time.
     *
     * @param isSpotlit      whether this is the filter's spotlighted owner
     * @param split          the owner's members by fill state
     * @param owner          the owner, which the sub-cluster keys are derived from
     * @param fillColour     the resolved fill colour, or null for a "No color" fill that draws
     *                       no cluster at all
     * @param clusterRegions each cluster's smoothed loops, outer ring plus enclaves
     * @return one fill per given cluster, in the same order
     */
    public List<ClusterFill> buildFills(
            boolean isSpotlit,
            FillSplit split,
            String owner,
            Color fillColour,
            List<RingRegion> clusterRegions) {

        if (fillColour == null) {
            return repeatEmptyFill(clusterRegions.size());
        }
        if (!isSpotlit && !split.hasNonSolidMembers()) {
            var fills = new ArrayList<ClusterFill>(clusterRegions.size());
            for (var region : clusterRegions) {
                fills.add(new ClusterFill(
                    PolygonTessellator.tessellateToTriangles(region.toRings()),
                    GlVertexRuns.NO_VERTICES));
            }
            return fills;
        }
        return buildPerStateFills(split, owner, clusterRegions);
    }

    // Tessellates each drawn state as its own cluster: the solid members into the triangle soup,
    // the hatched members into their own soup the hatch generator then clips diagonal lines to.
    // The unfilled state is deliberately never tessellated - it holds ground for the cluster's
    // boundary and label but paints no fill of its own.
    //
    // Both states' rings are traced once for the whole owner and then clipped per cluster, so a
    // state that spans two bodies contributes to each of them without being traced twice and
    // without either body's fill reaching into the other.
    private List<ClusterFill> buildPerStateFills(
            FillSplit split,
            String owner,
            List<RingRegion> clusterRegions) {

        var subClusterOwners = mapSubClusterOwnerBySystemId(split, owner);
        var solidRings = traceSubClusterRings(FillState.SOLID, split, subClusterOwners);
        var hatchedRings = traceSubClusterRings(FillState.HATCHED, split, subClusterOwners);

        var fills = new ArrayList<ClusterFill>(clusterRegions.size());
        for (var region : clusterRegions) {
            var clusterRings = region.toRings();
            fills.add(new ClusterFill(
                clipToCluster(solidRings, clusterRings),
                Hatching.computeHatchSegments(
                    clipToCluster(hatchedRings, clusterRings),
                    hatch.angleRadians(),
                    hatch.spacing())));
        }
        return fills;
    }

    // One empty fill per cluster, for an owner that paints no fill at all: the clusters still
    // exist (their boundaries stroke, their ground holds a label), so the list has to line up
    // with them rather than come back empty.
    private static List<ClusterFill> repeatEmptyFill(int clusterCount) {
        var fills = new ArrayList<ClusterFill>(clusterCount);
        for (var i = 0; i < clusterCount; i++) {
            fills.add(new ClusterFill(
                GlVertexRuns.NO_VERTICES,
                GlVertexRuns.NO_VERTICES));
        }
        return fills;
    }

    // The part of one state's traced rings falling inside one cluster, as a triangle soup. The
    // clip does double duty: it confines the state to this body, and it clamps the state's outer
    // edge onto the exact line the boundary strokes, since the traced rings still carry the
    // mitered corners the smoothing rounded off.
    private static float[] clipToCluster(
            List<List<double[]>> stateRings,
            List<List<double[]>> clusterRings) {

        if (stateRings.isEmpty()) {
            return GlVertexRuns.NO_VERTICES;
        }
        return PolygonTessellator.tessellateIntersectionToTriangles(stateRings, clusterRings);
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

    // Traces one of the owner's states as its own cluster across the whole holding, so the state
    // fills as one continuous area with no per-cell seam or truncation inside it. The other
    // states' systems are the coincident neighbours, whose shared edge insets by nothing so the
    // states abut with no channel between them - which is why the states still meet exactly along
    // their shared seam after each is clipped to a cluster: that seam is interior to both
    // operands and the clip leaves it untouched. Empty when the state holds no members.
    private List<List<double[]>> traceSubClusterRings(
            FillState state,
            FillSplit split,
            Map<String, String> subClusterOwnerBySystemId) {

        var members = split.resolveMembersOf(state);
        if (members.cellIds().isEmpty()) {
            return List.of();
        }
        return borderTrace.traceRings(
            members.cellIds(),
            cellEdgesByCellId,
            new CellGrouping(cellGrouping.systemIdByCellId(), subClusterOwnerBySystemId),
            split.resolveCoincidentSystemIdsOf(state));
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
