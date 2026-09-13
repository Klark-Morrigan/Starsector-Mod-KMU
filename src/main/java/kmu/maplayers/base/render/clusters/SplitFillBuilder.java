package kmu.maplayers.base.render.clusters;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.render.clusters.FillSplit.FillState;
import kmu.maplayers.base.theme.HatchStyle;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves one owner's {@link FillSplit} into the {@link TracedFill} its clusters are then cut
 * from, taking the per-state trace only where the split actually needs one.
 *
 * <p>Built per owner around the trace context the whole fill shares - the cells, their
 * grouping, the border trace, and the hatch geometry - so the several tessellation steps read
 * one consistent snapshot instead of threading the lot through each hop.
 *
 * <p>Each drawn state is traced from its own rings rather than from its members' individual
 * cells, so no per-cell inset truncation can leave an unfilled wedge where two members meet at a
 * corner against a rival. Those rings are traced once for the owner, and the cutting of them to
 * one body at a time belongs to the traced fill rather than here - which is what lets a holding
 * in two places pay one trace and one clip apiece.
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

    private final Map<SystemKey, List<CellEdge>> cellEdgesByCellKey;
    private final CellGrouping cellGrouping;
    private final ClusterBorderTrace borderTrace;
    private final HatchStyle hatch;

    /**
     * @param cellEdgesByCellKey the raw cell adjacency the sub-cluster rings are traced from -
     *                           the same map the trace itself takes, rather than whatever cache
     *                           the caller happens to hold it in
     * @param cellGrouping       which system each cell draws as, paired with each system's
     *                           owner - the keys the sub-clusters are derived from
     * @param borderTrace        the trace parameters the whole fill shares with its border
     * @param hatch              the sector-wide hatch geometry the hatched sub-cluster is cut with
     */
    public SplitFillBuilder(
            Map<SystemKey, List<CellEdge>> cellEdgesByCellKey,
            CellGrouping cellGrouping,
            ClusterBorderTrace borderTrace,
            HatchStyle hatch) {

        this.cellEdgesByCellKey = cellEdgesByCellKey;
        this.cellGrouping = cellGrouping;
        this.borderTrace = borderTrace;
        this.hatch = hatch;
    }

    /**
     * Resolves how this owner fills, tracing the per-state rings only where the split needs them.
     *
     * <p>An owner whose members do not all fill solid fills per state inside each body's own
     * boundary - one area per {@link FillState} - so the states read apart without the boundary
     * fracturing. A spotlit owner always splits, since its fill is per-state even when every
     * member is in the same state. Every other owner fills each body solid from the same smoothed
     * loops that body's boundary strokes, so fill and boundary match exactly and the split's cost
     * is paid only by the owners that need it.
     *
     * <p>Answered for the owner rather than for a body, because the split's rings are the
     * owner's: cutting them to a body is the returned value's job, and doing it here would mean
     * either re-tracing the whole holding per body or handing back a list of fills for a caller
     * to line up against its bodies by position.
     *
     * @param isSpotlit  whether this is the filter's spotlighted owner
     * @param split      the owner's members by fill state
     * @param owner      the owner, which the sub-cluster keys are derived from
     * @param fillColour the resolved fill colour, or null for a "No color" fill that draws no
     *                   cluster at all
     * @return the owner's fill, ready to be cut to each body it holds
     */
    public TracedFill traceFill(
            boolean isSpotlit,
            FillSplit split,
            String owner,
            Color fillColour) {

        if (fillColour == null) {
            return TracedFill.UNPAINTED;
        }
        if (!isSpotlit && !split.hasNonSolidMembers()) {
            return TracedFill.WHOLE_CLUSTER;
        }
        // Both drawn states traced as their own clusters across the whole holding. The unfilled
        // state is deliberately not traced - it holds its place for a boundary and a label and
        // paints nothing, so there is no geometry for it to carry.
        var subClusterOwners = mapSubClusterOwnerBySystemKey(split, owner);
        return new TracedFill.PerFillState(
            traceSubClusterRings(FillState.SOLID, split, subClusterOwners),
            traceSubClusterRings(FillState.HATCHED, split, subClusterOwners),
            hatch);
    }

    // Keys the footprint's three fill states apart, so the border tracer - which fuses cells sharing
    // a key - traces the solid, hatched, and unfilled members as separate clusters rather than as the
    // one body their shared footprint key makes them. Every system outside the footprint keeps its
    // real key, so an edge from a member to a rival or to empty space classifies exactly as it does
    // when the whole footprint is traced, and the sub-clusters' outer edge therefore lands where the
    // frontier draws it. Suffixing the footprint's own key leaves the derived keys as
    // collision-free as it already is.
    //
    // Written under each member's own key, the address the owner map already carries, so two
    // members sharing a vanilla id take the sub-cluster key of the state each is actually in.
    private Map<SystemKey, String> mapSubClusterOwnerBySystemKey(FillSplit split, String owner) {

        var keys = new HashMap<>(cellGrouping.ownerBySystemKey());

        putSubClusterOwners(keys, split, FillState.SOLID, owner + SOLID_SUB_CLUSTER_SUFFIX);
        putSubClusterOwners(keys, split, FillState.HATCHED, owner + HATCHED_SUB_CLUSTER_SUFFIX);
        putSubClusterOwners(keys, split, FillState.UNFILLED, owner + UNFILLED_SUB_CLUSTER_SUFFIX);

        return keys;
    }

    private static void putSubClusterOwners(
            Map<SystemKey, String> keys,
            FillSplit split,
            FillState state,
            String subClusterOwner) {

        for (var systemKey : split.resolveMembersOf(state).systemKeys()) {
            keys.put(systemKey, subClusterOwner);
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
            Map<SystemKey, String> subClusterOwnerBySystemKey) {

        var members = split.resolveMembersOf(state);
        if (members.cellKeys().isEmpty()) {
            return List.of();
        }
        return borderTrace.traceRings(
            members.cellKeys(),
            cellEdgesByCellKey,
            new CellGrouping(cellGrouping.systemKeyByCellKey(), subClusterOwnerBySystemKey),
            split.resolveCoincidentSystemKeysOf(state));
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
