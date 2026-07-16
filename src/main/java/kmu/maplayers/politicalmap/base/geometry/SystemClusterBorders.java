package kmu.maplayers.politicalmap.base.geometry;

import kmlib.math.geometry.EdgeRings;
import kmlib.math.geometry.Limits;
import kmlib.math.geometry.PolygonOffsets;
import kmlib.math.geometry.PolygonRegions;
import kmlib.math.geometry.Segment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Traces the inset border rings that outline one faction's system cluster(s).
 *
 * <p>Where {@link CellShaper} shapes each cell on its
 * own, this looks at a whole cluster: it gathers the boundary edges of every
 * system a faction holds - the edges against a different owner, unowned space, or
 * the map frontier, with same-faction seams dropped - and chains them into the
 * closed rings that outline the cluster. Disjoint pockets of the faction and
 * enclaves carved out of it each come back as their own ring. Every ring is inset
 * inward by the same channel the fills use, so the border sits on the fill's outer
 * edge. The rings are handed back un-rounded: the inset of a fused cluster can
 * cross itself where the cluster pinches to a neck, so the render layer resolves
 * each ring to its clean envelope before rounding it - rounding here would only be
 * clipped away by that resolve. Interior seams are never touched - they stay
 * sharp, drawn per cell.
 *
 * <p>An open-frontier edge - the cluster facing an unheld dead or decivilised star -
 * is the one exception to the uniform inward channel: while the frontier toggle is on
 * it pushes <em>outward</em> past the raw edge toward that star by the channel plus the
 * {@link FrontierSetback} for its spacing, so the colour reaches around the star and
 * stops at the star's keep-out line rather than cutting off at the midline. That push
 * mirrors the facing empty cell's own pull-in ({@link CellShaper}) - the same setback,
 * only the sign flipped - so fill and empty pocket land on one shared line. It is inert
 * on organised boundaries and while the toggle is off, so those insets stay unchanged.
 *
 * <p>Pure geometry over the adjacency graph and the resolved owners, using the same
 * {@link EdgeClassifier} rule the fills merge on, so a system's edge is a border in
 * exactly the cases its fill leaves a channel. Kept free of GL and of styling: it
 * hands back plain rings the render layer colors and strokes.
 */
public final class SystemClusterBorders {
    // A degenerate inset (a cluster narrower than twice the channel) folds the ring
    // over, flipping its signed area's sign against the raw ring's. Such a ring is
    // dropped rather than stroked as a self-crossing tangle.
    private static final double MIN_RING_SIGNED_AREA = 1e-6;

    private SystemClusterBorders() {
    }

    /**
     * Traces the inset border rings for one group of same-faction systems - the
     * raw material the render layer then cleans, rounds, and strokes.
     *
     * <p>These rings are inset but not yet rounded: a miter/bevel inset of a fused
     * cluster can cross itself where the cluster pinches to a neck, so the caller
     * resolves each ring to its clean outer envelope (dropping the crossing) and
     * only then rounds it - rounding before that resolve would see the arc clipped
     * off at the crossing and left a sharp corner.
     *
     * @param groupSystemIds  the systems sharing one grouping key whose fused
     *                        cluster(s) to outline; systems absent from
     *                        {@code edgesBySystemId} are skipped
     * @param edgesBySystemId each system's raw cell edges, tagged with the
     *                        neighbour across them - the adjacency graph
     * @param groupKeyBySystemId the grouping key per system, to tell a border edge
     *                        (different or no key across it) from a fused seam
     * @param borderInset     inward inset applied to each ring, matching the fills'
     *                        channel so the border lands on the fill edge
     * @param vertexWeldTolerance largest gap between two reports of a shared corner
     *                        still welded into one when chaining the boundary
     * @param miterSpikeLimit  a corner whose inset miter would spike past this
     *                        multiple of {@code borderInset} is bevelled instead of
     *                        pointed, so a sharp cluster corner never shoots an
     *                        inward loop
     * @param frontier        the pass's frontier snapshot: while enabled, an open-frontier
     *                        edge pushes outward toward its unheld star by the channel plus
     *                        its setback instead of insetting inward by the channel
     * @return one inset (un-rounded) ring per cluster and per enclave, in world
     *         coordinates; empty when the group holds no borderable geometry
     */
    public static List<List<double[]>> traceBorderRings(
            Collection<String> groupSystemIds,
            Map<String, List<CellEdge>> edgesBySystemId,
            Map<String, String> groupKeyBySystemId,
            double borderInset,
            double vertexWeldTolerance,
            double miterSpikeLimit,
            FrontierSettings frontier) {
        var boundary = collectBoundarySegments(groupSystemIds, edgesBySystemId,
                groupKeyBySystemId, borderInset, frontier);
        var rings = new ArrayList<List<double[]>>();
        for (var ring : EdgeRings.chainIntoRingsWithEdgeValues(
                boundary.segments(),
                boundary.edgeDistances(),
                vertexWeldTolerance)) {
            // Offset each ring edge by its own signed distance: organised boundaries inward
            // by the channel, open frontiers outward toward their star. The per-edge miter
            // path is used rather than the half-plane clip because only it can carry an edge
            // outward past the outline, the reach the frontier push needs.
            var inset = PolygonOffsets.insetPolygonByMiter(
                    ring.corners(), ring.edgeValues(), miterSpikeLimit);
            if (!isCollapsed(ring.corners(), inset, hasOutwardPush(ring.edgeValues()))) {
                rings.add(inset);
            }
        }
        return rings;
    }

    // Gathers, across every system in the group, the edges that face outside the
    // cluster (a different key, no key, or the map frontier) as directed segments,
    // each paired with the signed distance its ring edge later offsets by. Same-key
    // seams are dropped, so the surviving segments trace only the cluster's outer
    // boundary and its enclaves.
    private static BoundarySegments collectBoundarySegments(
            Collection<String> groupSystemIds,
            Map<String, List<CellEdge>> edgesBySystemId,
            Map<String, String> groupKeyBySystemId,
            double borderInset,
            FrontierSettings frontier) {
        var segments = new ArrayList<Segment>();
        var distances = new ArrayList<Double>();
        for (var systemId : groupSystemIds) {
            var edges = edgesBySystemId.get(systemId);
            if (edges == null) {
                continue;
            }
            var ownGroupKey = groupKeyBySystemId.get(systemId);
            var ownSite = frontier.siteBySystemId().get(systemId);
            for (var edge : edges) {
                var edgeClass = EdgeClassifier.classifyAcross(
                        edge,
                        ownGroupKey,
                        groupKeyBySystemId);
                if (!edgeClass.isBoundary()) {
                    continue;
                }
                segments.add(new Segment(edge.x1(), edge.y1(), edge.x2(), edge.y2()));
                distances.add(
                        computeSignedOffset(edge, ownSite, edgeClass, borderInset, frontier));
            }
        }
        return new BoundarySegments(segments, toDoubleArray(distances));
    }

    // The signed miter offset one boundary segment receives: the border channel inward
    // (positive) for an organised boundary or the map bound, and the channel plus the
    // frontier setback outward (negative, toward the dead star) for the owned side of an
    // open frontier. The outward push carries the owned colour around an unheld star and
    // stops it at the star's keep-out line; the setback is the exact one the facing empty
    // cell pulls in by, only the sign flipped, so owned fill and empty pocket coincide.
    private static double computeSignedOffset(
            CellEdge edge,
            double[] ownSite,
            EdgeClass edgeClass,
            double borderInset,
            FrontierSettings frontier) {
        if (!isOwnedFrontierPushOut(edge, ownSite, edgeClass, frontier)) {
            return borderInset;
        }
        var neighbourSite = frontier.siteBySystemId().get(edge.neighbourSystemId());
        var setback = FrontierSetback.computeSetback(
                ownSite,
                neighbourSite,
                frontier.keepOutRadius());
        return -(borderInset + setback);
    }

    // Whether this segment is the owned side of an open frontier to push outward: the
    // toggle on, the edge an open frontier (an owned cell facing an unheld star), and both
    // sites known so the setback can be measured. Every group system here is owned, so an
    // open-frontier edge is always the owned side; a missing site (a stale adjacency edge)
    // falls back to the plain inward channel rather than offsetting with garbage.
    private static boolean isOwnedFrontierPushOut(
            CellEdge edge,
            double[] ownSite,
            EdgeClass edgeClass,
            FrontierSettings frontier) {
        return frontier.isEnabled()
                && edgeClass == EdgeClass.OPEN_FRONTIER
                && ownSite != null
                && frontier.siteBySystemId().get(edge.neighbourSystemId()) != null;
    }

    // Whether any edge pushes outward (a negative signed offset) - an open frontier
    // reaching around a dead star. When it does, the ring may legitimately grow, so the
    // collapse guard's grew-check (which assumes a pure inward inset always shrinks an
    // outer ring) is waived for it.
    private static boolean hasOutwardPush(double[] edgeDistances) {
        for (var distance : edgeDistances) {
            if (distance < 0) {
                return true;
            }
        }
        return false;
    }

    // Copies a distance list into a primitive array, so the per-edge distances hand to the
    // chainer and miter inset as a plain double[] parallel to the segments.
    private static double[] toDoubleArray(List<Double> values) {
        var array = new double[values.size()];
        for (var i = 0; i < array.length; i++) {
            array[i] = values.get(i);
        }
        return array;
    }

    // Whether the inset folded the ring over rather than cleanly offsetting it:
    // fewer than three corners left, a vanishing or sign-flipped area, or - for an
    // outer ring with no outward push - an area that grew. A miter offset past a convex
    // ring's own width does not invert the winding; it re-expands the corners into a
    // larger ring of the same winding, so a grown outer ring is the tell-tale of
    // over-inset. An outer ring (positive, counter-clockwise) must shrink under an inward
    // inset, while a hole (negative, clockwise) legitimately grows as its border backs
    // into the surrounding solid - so the grew-check is applied only to outer rings. When
    // {@code mayGrow}, a frontier edge deliberately pushed the outer ring outward, so a
    // grown ring is expected rather than a fold and the grew-check is waived; the
    // winding-flip and vanishing-area guards, which still catch a true fold, remain.
    private static boolean isCollapsed(
            List<double[]> rawRing,
            List<double[]> insetRing,
            boolean mayGrow) {
        if (insetRing.size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
            return true;
        }
        var rawArea = PolygonRegions.computeSignedArea(rawRing);
        var insetArea = PolygonRegions.computeSignedArea(insetRing);
        if (Math.abs(insetArea) < MIN_RING_SIGNED_AREA
                || Math.signum(rawArea) != Math.signum(insetArea)) {
            return true;
        }
        return !mayGrow && rawArea > 0 && insetArea > rawArea;
    }

    // One faction group's boundary segments paired with the signed miter offset each
    // receives, kept parallel so the offset survives chaining onto the ring edge it
    // forms (an organised boundary insets inward, an open frontier pushes outward).
    private record BoundarySegments(List<Segment> segments, double[] edgeDistances) {
    }
}
