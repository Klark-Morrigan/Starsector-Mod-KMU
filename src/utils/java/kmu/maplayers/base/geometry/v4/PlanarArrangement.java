package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.Segment;
import kmlib.math.geometry.VertexWelder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
 * where it divides nothing.
 *
 * <p>The input must already be cut at its crossings: two lines crossing in the middle are two
 * edges that share no vertex here, and a walk would pass straight through the crossing without
 * turning. {@link SegmentCrossings} is what makes that true.
 */
final class PlanarArrangement {

    private final List<double[]> vertices;

    private final List<List<HalfEdge>> outgoing;

    private final Map<HalfEdge, Integer> slots;

    private PlanarArrangement(
            List<double[]> vertices,
            List<List<HalfEdge>> outgoing,
            Map<HalfEdge, Integer> slots) {

        this.vertices = vertices;
        this.outgoing = outgoing;
        this.slots = slots;
    }

    /**
     * Builds the graph, welding the ends of the lines into shared vertices as it goes.
     *
     * @param segments      lines that meet only at their ends
     * @param weldTolerance how far two reports of one corner may stand apart and still be one
     * @return the arrangement
     */
    static PlanarArrangement weldArrangement(List<Segment> segments, double weldTolerance) {

        var welder = new VertexWelder(weldTolerance);

        // Undirected, deduplicated. Two lines laid exactly along each other are one edge of the
        // division, and holding the same neighbour twice would give a vertex two entries at the
        // same angle - which leaves the order round it undecided and the walk following one of
        // them by chance.
        var links = new LinkedHashSet<HalfEdge>();

        for (var segment : segments) {

            var from = welder.weld(segment.startX(), segment.startY());
            var to = welder.weld(segment.endX(), segment.endY());

            if (from == to) {
                continue;
            }
            links.add(new HalfEdge(Math.min(from, to), Math.max(from, to)));
        }

        var vertices = welder.collectPoints();
        var outgoing = sortAroundVertices(vertices, links);

        return new PlanarArrangement(vertices, outgoing, indexSlots(outgoing));
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
     * Where a vertex stands.
     *
     * @param vertex the vertex's index
     * @return its point
     */
    double[] findPointAt(int vertex) {
        return vertices.get(vertex);
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
