package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.Limits;
import kmlib.math.geometry.Segment;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Rings and lines in, the pieces they divide the plane into out.
 *
 * <p>The one construction v4 is built on. Where a traced outline says what one shape looks
 * like, a division says what every piece is at once - and because the pieces are read off the
 * lines rather than drawn separately, two pieces either side of a line share that line exactly.
 * Nothing has to be reconciled afterwards, because nothing was measured twice.
 *
 * <p><b>A line that divides nothing costs nothing.</b> Whether laying a line creates a new piece
 * is not a property of the line - it depends on what else is down - so there is no judgement to
 * make when laying one. A line with a loose end simply has the same piece on both sides of it,
 * and comes back as a slit in that piece.
 *
 * <p><b>Nothing of the map reaches in here.</b> No cell, no coast, no span: rings and lines
 * only. What the pieces mean is read off them afterwards, by whatever laid the lines.
 *
 * <p><b>One outer face per connected component.</b> The walk reports the endless outside of
 * every group of lines that touch each other, so a fixture of two rings that do not meet hands
 * back two outer faces. Nor is a piece told that another component sits inside it: a ring
 * enclosed by a ring it does not touch comes back as its own pair of faces, and the enclosing
 * piece is not given it as a hole.
 */
public final class FaceWalk {

    private FaceWalk() {
    }

    /**
     * Divides the plane along every line given and hands back the pieces.
     *
     * @param rings         closed outlines, each without a repeated closing point; rings too
     *                      short to enclose anything are ignored
     * @param walls         lines with two loose ends, laid across whatever they fall on
     * @param weldTolerance how far two reports of one corner may stand apart and still meet
     * @return every piece, bounded ones and outer ones together, each telling which it is by
     *         its own winding
     */
    public static List<Face> walkFaces(
            List<List<double[]>> rings,
            List<Segment> walls,
            double weldTolerance) {

        var arrangement = PlanarArrangement.weldArrangement(
            SegmentCrossings.splitAtCrossings(collectEdges(rings, walls)), weldTolerance);

        var faces = new ArrayList<Face>();
        var walked = new HashSet<PlanarArrangement.HalfEdge>();
        var smallestFace = measureSmallestFace(weldTolerance);

        // Every edge in both directions, each direction closing the face on that side of it.
        // Starting from all of them rather than from a chosen few is what makes the result a
        // division: no piece can be missed, because every piece has an edge and every edge is
        // started from.
        for (var start : arrangement.collectHalfEdges()) {

            if (walked.contains(start)) {
                continue;
            }

            var face = Face.encloseFace(walkFaceFrom(arrangement, start, walked));

            if (face.measureArea() > smallestFace) {
                faces.add(face);
            }
        }
        return List.copyOf(faces);
    }

    // One face, followed from an edge round to that edge again, marking off every edge taken so
    // no face is reported twice.
    private static List<double[]> walkFaceFrom(
            PlanarArrangement arrangement,
            PlanarArrangement.HalfEdge start,
            Set<PlanarArrangement.HalfEdge> walked) {

        // A face cannot have more corners than the graph has edge directions. Passing that
        // means the order round some vertex does not lead back where it came from, and the walk
        // would otherwise spin until the viewer was killed rather than say so.
        var mostCorners = arrangement.countHalfEdges();

        var boundary = new ArrayList<double[]>();
        var edge = start;

        do {
            walked.add(edge);
            boundary.add(arrangement.findPointAt(edge.from()));
            edge = arrangement.findNextAroundFace(edge);

            if (boundary.size() > mostCorners) {
                throw new IllegalStateException(
                    "face walk did not close after taking every edge direction once");
            }
        } while (!edge.equals(start));

        return boundary;
    }

    // Every line the division is cut along: each ring's edges, plus the walls as they stand.
    private static List<Segment> collectEdges(
            List<List<double[]>> rings, List<Segment> walls) {

        var edges = new ArrayList<Segment>(walls);

        for (var ring : rings) {

            if (ring.size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
                continue;
            }

            for (var index = 0; index < ring.size(); index++) {

                var from = ring.get(index);
                var to = ring.get((index + 1) % ring.size());

                edges.add(new Segment(from[0], from[1], to[0], to[1]));
            }
        }
        return edges;
    }

    // Below what area a piece is not a piece. A face narrower than the welding tolerance is
    // thinner than the resolution the lines were joined at, so whether it is there at all is a
    // question about the arithmetic rather than about the shape - and a slit, which is a face
    // walked out and back with no width, is exactly that face with an area of nothing.
    private static double measureSmallestFace(double weldTolerance) {

        var resolution = Math.max(weldTolerance, Limits.MIN_EDGE_LENGTH);

        return resolution * resolution;
    }
}
