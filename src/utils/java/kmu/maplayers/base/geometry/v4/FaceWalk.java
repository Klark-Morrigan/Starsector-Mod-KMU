package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.Limits;
import kmlib.math.geometry.PolygonRegions;
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
 * <p><b>Every edge of every piece says which line it lies on.</b> The labels the rings and
 * walls arrive with travel through the cutting and the welding and come out on the faces, so a
 * piece can be read - this shore is that cell's, this side is that wall - and not merely drawn.
 *
 * <p><b>Nothing of the map reaches in here.</b> No cell, no coast, no span: rings and lines
 * only, and a label is an int the walk carries without reading. What the pieces mean is read
 * off them afterwards, by whatever laid the lines.
 *
 * <p><b>A group of lines enclosed by another is cut out of it.</b> Groups that never touch are
 * walked separately, so each closes an outside of its own - and where that outside sits within
 * some other group's piece, it is what that piece runs around rather than a piece in its own
 * right. It becomes a hole, and the face it was cut from loses its area. Only the outside of
 * everything survives as an outer face, which is what makes a frame worth laying: framed, every
 * piece is bounded and the pieces cover the frame exactly once.
 */
public final class FaceWalk {

    // What a ring enclosed by no piece is filed under - the outside of everything, which is a
    // piece in its own right rather than a hole in something. Named rather than left as a bare
    // -1 because it is compared against real piece numbers.
    private static final int NOTHING_ENCLOSES_IT = -1;

    private FaceWalk() {
    }

    /**
     * Divides the plane along every line given and hands back the pieces.
     *
     * @param rings         closed outlines, each edge saying which line it lies on; rings too
     *                      short to enclose anything are ignored
     * @param walls         lines with two loose ends, laid across whatever they fall on
     * @param weldTolerance how far two reports of one corner may stand apart and still meet
     * @return every piece, bounded ones and outer ones together, each telling which it is by
     *         its own winding
     */
    public static List<Face> walkFaces(
            List<LabelledRing> rings,
            List<LabelledWall> walls,
            double weldTolerance) {

        var arrangement = PlanarArrangement.weldArrangement(
            SegmentCrossings.splitAtCrossings(collectLines(rings, walls)), weldTolerance);

        var closed = new ArrayList<LabelledRing>();
        var groups = new ArrayList<Integer>();
        var walked = new HashSet<PlanarArrangement.HalfEdge>();
        var smallestFace = measureSmallestFace(weldTolerance);
        var componentOf = arrangement.labelComponents();

        // Every edge in both directions, each direction closing the face on that side of it.
        // Starting from all of them rather than from a chosen few is what makes the result a
        // division: no piece can be missed, because every piece has an edge and every edge is
        // started from.
        var halfEdges = arrangement.collectHalfEdges();

        for (var start : halfEdges) {

            if (walked.contains(start)) {
                continue;
            }

            var ring = walkRingFrom(arrangement, start, halfEdges.size(), walked);

            if (Math.abs(PolygonRegions.computeSignedArea(ring.vertices())) > smallestFace) {
                closed.add(ring);
                groups.add(componentOf[start.from()]);
            }
        }
        return cutEnclosedRingsOut(closed, groups);
    }

    // Each ring as a face, with any ring it encloses cut out of it rather than standing as a
    // piece of its own.
    //
    // A ring winding clockwise is the outside of some group of lines. Where that group sits
    // inside another group's piece, its outside is not a piece - it is the shape that piece runs
    // around, so it belongs to that piece as a hole. Only an outside enclosed by nothing is a
    // piece in its own right, and that is the one true outer face.
    //
    // The smallest enclosing piece takes it, so a lake inside a group inside the sea is cut from
    // the group rather than from the sea.
    private static List<Face> cutEnclosedRingsOut(
            List<LabelledRing> rings, List<Integer> groups) {

        var pieces = new ArrayList<Face>();
        var enclosing = new ArrayList<Integer>();

        for (var ring : rings) {
            pieces.add(Face.encloseFace(ring));
        }

        for (var index = 0; index < rings.size(); index++) {
            enclosing.add(pieces.get(index).isOuterFace()
                ? findSmallestEnclosing(pieces, groups, index)
                : NOTHING_ENCLOSES_IT);
        }

        var cut = new ArrayList<Face>();

        for (var index = 0; index < rings.size(); index++) {

            var parent = enclosing.get(index);

            if (parent != NOTHING_ENCLOSES_IT) {
                pieces.set(parent, pieces.get(parent).cutOut(rings.get(index)));
            }
        }

        for (var index = 0; index < rings.size(); index++) {
            if (enclosing.get(index) == NOTHING_ENCLOSES_IT) {
                cut.add(pieces.get(index));
            }
        }
        return List.copyOf(cut);
    }

    // Which bounded piece a ring sits inside, or NOTHING_ENCLOSES_IT where none does.
    //
    // Only pieces of OTHER groups are candidates, and that is not an optimisation. A group's
    // own pieces run along the very edges this ring does, so the corner asked about lies on
    // their boundary - where inside and outside is not a question with an answer, and a piece
    // would end up cut out of the piece beside it. Across groups no corner is shared and the
    // test means what it says.
    private static int findSmallestEnclosing(
            List<Face> pieces, List<Integer> groups, int ring) {

        var smallest = NOTHING_ENCLOSES_IT;
        var corner = pieces.get(ring).boundary().get(0);

        for (var index = 0; index < pieces.size(); index++) {

            var piece = pieces.get(index);

            if (groups.get(index).equals(groups.get(ring))
                    || piece.isOuterFace()
                    || !PolygonRegions.isPointInsideRing(
                        piece.boundary(), corner[0], corner[1])) {
                continue;
            }
            if (smallest == NOTHING_ENCLOSES_IT
                    || piece.measureArea() < pieces.get(smallest).measureArea()) {
                smallest = index;
            }
        }
        return smallest;
    }

    // One face, followed from an edge round to that edge again, marking off every edge taken so
    // no face is reported twice.
    //
    // mostCorners is the count of edge directions in the whole graph: a face cannot have more
    // corners than that, so passing it means the order round some vertex does not lead back
    // where it came from, and the walk would otherwise spin until the viewer was killed rather
    // than say so.
    private static LabelledRing walkRingFrom(
            PlanarArrangement arrangement,
            PlanarArrangement.HalfEdge start,
            int mostCorners,
            Set<PlanarArrangement.HalfEdge> walked) {

        var boundary = new ArrayList<double[]>();
        var labels = new ArrayList<Integer>();
        var edge = start;

        do {
            walked.add(edge);
            boundary.add(arrangement.findPointAt(edge.from()));
            labels.add(arrangement.readLabelOf(edge));
            edge = arrangement.findNextAroundFace(edge);

            if (boundary.size() > mostCorners) {
                throw new IllegalStateException(
                    "face walk did not close after taking every edge direction once");
            }
        } while (!edge.equals(start));

        return LabelledRing.ofGatheredLabels(boundary, labels);
    }

    // Every line the division is cut along: each ring's edges under their own labels, plus the
    // walls as they stand.
    private static List<LabelledWall> collectLines(
            List<LabelledRing> rings, List<LabelledWall> walls) {

        var lines = new ArrayList<LabelledWall>(walls);

        for (var ring : rings) {

            var vertices = ring.vertices();

            if (vertices.size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
                continue;
            }

            for (var index = 0; index < vertices.size(); index++) {

                var from = vertices.get(index);
                var to = vertices.get((index + 1) % vertices.size());

                lines.add(new LabelledWall(
                    new Segment(from[0], from[1], to[0], to[1]), ring.edgeLabels()[index]));
            }
        }
        return lines;
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
