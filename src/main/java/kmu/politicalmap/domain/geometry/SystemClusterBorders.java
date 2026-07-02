package kmu.politicalmap.domain.geometry;

import kmlib.math.geometry.EdgeRings;
import kmlib.math.geometry.Limits;
import kmlib.math.geometry.Polygons;
import kmlib.math.geometry.Segment;

import kmu.politicalmap.domain.politics.DominantOwner;

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
     * @param groupSystemIds  the systems of one faction (or independent space)
     *                        whose fused cluster(s) to outline; systems absent from
     *                        {@code edgesBySystemId} are skipped
     * @param edgesBySystemId each system's raw cell edges, tagged with the
     *                        neighbour across them - the adjacency graph
     * @param ownerBySystemId the dominant owner per system, to tell a border edge
     *                        (different or no owner across it) from a fused seam
     * @param borderInset     inward inset applied to each ring, matching the fills'
     *                        channel so the border lands on the fill edge
     * @param vertexWeldTolerance largest gap between two reports of a shared corner
     *                        still welded into one when chaining the boundary
     * @param miterSpikeLimit  a corner whose inset miter would spike past this
     *                        multiple of {@code borderInset} is bevelled instead of
     *                        pointed, so a sharp cluster corner never shoots an
     *                        inward loop
     * @return one inset (un-rounded) ring per cluster and per enclave, in world
     *         coordinates; empty when the group holds no borderable geometry
     */
    public static List<List<double[]>> traceBorderRings(Collection<String> groupSystemIds,
            Map<String, List<CellEdge>> edgesBySystemId,
            Map<String, DominantOwner> ownerBySystemId, double borderInset,
            double vertexWeldTolerance, double miterSpikeLimit) {
        var boundarySegments = collectBoundarySegments(groupSystemIds, edgesBySystemId,
                ownerBySystemId);
        var rings = new ArrayList<List<double[]>>();
        for (var rawRing : EdgeRings.chainIntoRings(boundarySegments, vertexWeldTolerance)) {
            var inset = Polygons.insetPolygonByMiter(rawRing, borderInset, miterSpikeLimit);
            if (!isCollapsed(rawRing, inset)) {
                rings.add(inset);
            }
        }
        return rings;
    }

    // Gathers, across every system in the group, the edges that face outside the
    // cluster (a different owner, no owner, or the map frontier), as directed
    // segments. Same-faction seams are dropped, so the surviving segments trace
    // only the cluster's outer boundary and its enclaves.
    private static List<Segment> collectBoundarySegments(Collection<String> groupSystemIds,
            Map<String, List<CellEdge>> edgesBySystemId,
            Map<String, DominantOwner> ownerBySystemId) {
        var segments = new ArrayList<Segment>();
        for (var systemId : groupSystemIds) {
            var edges = edgesBySystemId.get(systemId);
            if (edges == null) {
                continue;
            }
            var ownerFactionId = factionIdOf(ownerBySystemId.get(systemId));
            for (var edge : edges) {
                var neighbourFactionId = edge.neighbourSystemId() == null
                        ? null
                        : factionIdOf(ownerBySystemId.get(edge.neighbourSystemId()));
                if (EdgeClassifier.classify(ownerFactionId, neighbourFactionId)
                        == EdgeClass.BOUNDARY) {
                    segments.add(new Segment(edge.x1(), edge.y1(), edge.x2(), edge.y2()));
                }
            }
        }
        return segments;
    }

    // Whether the inset folded the ring over rather than cleanly offsetting it:
    // fewer than three corners left, a vanishing or sign-flipped area, or - for an
    // outer ring - an area that grew. A miter offset past a convex ring's own width
    // does not invert the winding; it re-expands the corners into a larger ring of
    // the same winding, so a grown outer ring is the tell-tale of over-inset. An
    // outer ring (positive, counter-clockwise) must shrink under an inward inset,
    // while a hole (negative, clockwise) legitimately grows as its border backs into
    // the surrounding solid - so the grew-check is applied only to outer rings.
    private static boolean isCollapsed(List<double[]> rawRing, List<double[]> insetRing) {
        if (insetRing.size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
            return true;
        }
        var rawArea = Polygons.computeSignedArea(rawRing);
        var insetArea = Polygons.computeSignedArea(insetRing);
        if (Math.abs(insetArea) < MIN_RING_SIGNED_AREA
                || Math.signum(rawArea) != Math.signum(insetArea)) {
            return true;
        }
        return rawArea > 0 && insetArea > rawArea;
    }

    private static String factionIdOf(DominantOwner owner) {
        return owner == null ? null : owner.factionId();
    }
}
