package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.VertexWelder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lines that meet only at their ends, read as a graph that knows which way round a vertex its
 * edges stand.
 *
 * <p>What a face walk actually walks. Knowing which vertices an edge joins is not enough to
 * close a face: at a vertex where four edges meet, a walk arriving along one of them has to
 * take the next one round rather than any of the other three, and which one that is depends on
 * the angles. So the edges at each vertex are held in the order they stand in, turning
 * counter-clockwise, and that order is the whole of what this adds to a plain list of lines.
 *
 * <p><b>Each edge is held once, in both directions.</b> A face lies on one side of an edge and
 * another face on the other, so the two sides are walked separately - as the two directions the
 * edge can be taken in. An edge taken both ways belongs to two faces, or twice to one face
 * where it divides nothing. Its label is the same whichever way it is taken: which line an
 * edge lies on does not depend on which side it is seen from.
 *
 * <p>The input must already be cut at its crossings: two lines crossing in the middle are two
 * edges that share no vertex here, and a walk would pass straight through the crossing without
 * turning. {@link SegmentCrossings} is what makes that true.
 */
final class PlanarArrangement {

    // A vertex no group has claimed yet, while the groups are being worked out.
    private static final int UNVISITED = -1;

    private final List<double[]> vertices;

    private final List<List<HalfEdge>> outgoing;

    private final Map<HalfEdge, Integer> slots;

    private final Map<HalfEdge, Integer> labels;

    private PlanarArrangement(
            List<double[]> vertices,
            List<List<HalfEdge>> outgoing,
            Map<HalfEdge, Integer> slots,
            Map<HalfEdge, Integer> labels) {

        this.vertices = vertices;
        this.outgoing = outgoing;
        this.slots = slots;
        this.labels = labels;
    }

    /**
     * Builds the graph, welding the ends of the lines into shared vertices as it goes.
     *
     * @param lines         lines that meet only at their ends
     * @param weldTolerance how far two reports of one corner may stand apart and still be one
     * @return the arrangement
     */
    static PlanarArrangement weldArrangement(List<LabelledWall> lines, double weldTolerance) {

        var welder = new VertexWelder(weldTolerance);

        // Undirected, deduplicated, each with the label of the first line that laid it. Two
        // lines laid exactly along each other are one edge of the division, and holding the
        // same neighbour twice would give a vertex two entries at the same angle - which leaves
        // the order round it undecided and the walk following one of them by chance.
        var links = new LinkedHashMap<HalfEdge, Integer>();

        for (var line : lines) {

            var segment = line.segment();
            var from = welder.weld(segment.startX(), segment.startY());
            var to = welder.weld(segment.endX(), segment.endY());

            if (from == to) {
                continue;
            }
            links.putIfAbsent(new HalfEdge(Math.min(from, to), Math.max(from, to)), line.label());
        }

        var vertices = welder.collectPoints();
        var outgoing = sortAroundVertices(vertices, links.keySet());

        return new PlanarArrangement(
            vertices, outgoing, indexSlots(outgoing), labelBothWays(links));
    }

    /**
     * Every direction of every edge, so a caller can be sure it has walked all of them.
     *
     * @return the half-edges, in a stable order
     */
    List<HalfEdge> collectHalfEdges() {

        var edges = new ArrayList<HalfEdge>();

        for (var atVertex : outgoing) {
            edges.addAll(atVertex);
        }
        return edges;
    }

    /**
     * Which group of touching lines each vertex belongs to.
     *
     * <p>What tells a ring enclosed by another group from a ring of the same group. Rings of
     * ONE group share their corners - a piece and the outside of that piece run along the same
     * edges - so asking whether one lies inside another by testing a shared corner has no
     * answer. Across groups there are no shared corners and the question is the ordinary one.
     *
     * @return a group number per vertex, equal exactly for vertices some chain of edges joins
     */
    int[] labelComponents() {

        var components = new int[vertices.size()];

        Arrays.fill(components, UNVISITED);

        var next = 0;

        for (var vertex = 0; vertex < vertices.size(); vertex++) {

            if (components[vertex] != UNVISITED) {
                continue;
            }
            spreadComponentFrom(vertex, next++, components);
        }
        return components;
    }

    /**
     * Where a vertex stands.
     *
     * @param vertex the vertex's index
     * @return its point
     */
    double[] findPointAt(int vertex) {
        return vertices.get(vertex);
    }

    /**
     * Which line an edge lies on.
     *
     * @param edge the edge, taken either way
     * @return its label
     */
    int readLabelOf(HalfEdge edge) {
        return labels.get(edge);
    }

    /**
     * The next edge round the face this one has on its left.
     *
     * <p>Arrive at a vertex along an edge and turn back down it; the next edge of the face is
     * the one immediately clockwise from there, because that is the first one met sweeping back
     * towards the way you came. Taking any other would cut across the face rather than round
     * it. Taken from every edge in turn, the rule closes each face exactly once.
     *
     * <p>At a vertex with one edge - the far end of a line that divides nothing - the edge back
     * IS the only one there, so the walk turns round on the spot and comes back the way it
     * came. That is the right answer rather than a special case: a line with a loose end has
     * the same face on both sides of it.
     *
     * @param edge the edge just taken
     * @return the next one
     */
    HalfEdge findNextAroundFace(HalfEdge edge) {

        var back = edge.reverse();
        var atVertex = outgoing.get(edge.to());
        var slot = slots.get(back);

        return atVertex.get((slot - 1 + atVertex.size()) % atVertex.size());
    }

    // Claims every vertex some chain of edges reaches from this one for the given group.
    // Walked with a stack rather than by recursion, a group being as long as the sector is wide.
    private void spreadComponentFrom(int vertex, int component, int[] components) {

        var pending = new ArrayDeque<Integer>();

        pending.push(vertex);
        components[vertex] = component;

        while (!pending.isEmpty()) {
            for (var edge : outgoing.get(pending.pop())) {

                if (components[edge.to()] == UNVISITED) {

                    components[edge.to()] = component;
                    pending.push(edge.to());
                }
            }
        }
    }

    // Both directions of every edge, gathered under the vertex each leaves from and put into
    // the order they stand in round it.
    private static List<List<HalfEdge>> sortAroundVertices(
            List<double[]> vertices, Iterable<HalfEdge> links) {

        var outgoing = new ArrayList<List<HalfEdge>>(vertices.size());

        for (var index = 0; index < vertices.size(); index++) {
            outgoing.add(new ArrayList<>());
        }

        for (var link : links) {
            outgoing.get(link.from()).add(link);
            outgoing.get(link.to()).add(link.reverse());
        }

        for (var atVertex : outgoing) {
            atVertex.sort(Comparator.comparingDouble(edge -> measureBearing(vertices, edge)));
        }
        return outgoing;
    }

    // Where each half-edge stands in the order round the vertex it leaves. Held rather than
    // searched for: the walk asks it once per edge taken, and finding it by scanning would make
    // every step cost as much as the vertex has edges.
    private static Map<HalfEdge, Integer> indexSlots(List<List<HalfEdge>> outgoing) {

        var slots = new HashMap<HalfEdge, Integer>();

        for (var atVertex : outgoing) {
            for (var slot = 0; slot < atVertex.size(); slot++) {
                slots.put(atVertex.get(slot), slot);
            }
        }
        return slots;
    }

    // Each link's label under both of its directions, so the walk reads a label off whichever
    // way it took the edge.
    private static Map<HalfEdge, Integer> labelBothWays(Map<HalfEdge, Integer> links) {

        var labels = new HashMap<HalfEdge, Integer>();

        for (var link : links.entrySet()) {
            labels.put(link.getKey(), link.getValue());
            labels.put(link.getKey().reverse(), link.getValue());
        }
        return labels;
    }

    // Which way an edge leaves its vertex, counter-clockwise from due east. Only ever compared
    // against other bearings at the same vertex, so where the turn is measured from does not
    // matter as long as every edge is measured from the same place.
    private static double measureBearing(List<double[]> vertices, HalfEdge edge) {

        return Points.computeAngleDegrees(
            vertices.get(edge.from()), vertices.get(edge.to()));
    }

    /**
     * One direction of one edge: the pair of vertices it joins, taken this way round.
     *
     * @param from the vertex it leaves
     * @param to   the vertex it reaches
     */
    record HalfEdge(int from, int to) {

        /**
         * The same edge, taken the other way.
         *
         * @return the half-edge back
         */
        HalfEdge reverse() {
            return new HalfEdge(to, from);
        }
    }
}
