package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.Limits;
import kmlib.math.geometry.PolygonRegions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lines in, the pieces they divide the plane into out.
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
 * <p><b>Every edge of every piece says which line it lies on.</b> The labels the lines arrive
 * with travel through the cutting and the welding and come out on the faces, so a piece can be
 * read - this shore is that cell's, this side is that wall - and not merely drawn.
 *
 * <p><b>Nothing of the map reaches in here.</b> No cell, no coast, no bridge: lines only, and
 * a label is an int the walk carries without reading. What the pieces mean is read
 * off them afterwards, by whatever laid the lines.
 *
 * <p><b>A group of lines enclosed by another is cut out of it.</b> Groups that never touch are
 * walked separately, so each closes an outside of its own - and where that outside sits within
 * some other group's piece, it is what that piece runs around rather than a piece in its own
 * right. It becomes a hole, and the face it was cut from loses its area. Only the outside of
 * everything survives as an outer face, which is what makes a frame worth laying: framed, every
 * piece is bounded and the pieces cover the frame exactly once.
 *
 * <p><b>The base is welded once, before anything is laid onto it.</b> The welding tolerance is
 * the base's own: the cells' frontier reports each shared corner twice, up to a sagitta apart,
 * and the weld is what closes those rings. It is far too coarse for a line laid across the
 * void. Welded together with the base, such a line has its ends pulled up to a sagitta
 * sideways, after the cutting has already run - and a line that grazes a corner is swung
 * across it, leaving a crossing the walk cannot turn at. So the base is cut and welded on its
 * own and becomes exact; what is laid is then cut against the base as it stands and welded at
 * rounding, and nothing moves after it was cut.
 */
public final class FaceWalk {

    /**
     * How far a corner may sit off a line and still lie on it, in map units, for anything
     * reading a piece's corners back against the lines they were cut on.
     *
     * <p>A corner the walk cut through a line lies on it to rounding, and the laid lines are
     * joined at rounding; this is far above both and far below anything drawn, so a corner
     * merely near a line is never taken for a corner on it.
     */
    public static final double ON_THE_LINE = 1e-3;

    // What a ring enclosed by no piece is filed under - the outside of everything, which is a
    // piece in its own right rather than a hole in something. Named rather than left as a bare
    // -1 because it is compared against real piece numbers.
    private static final int NOTHING_ENCLOSES_IT = -1;

    private FaceWalk() {
    }

    /**
     * Divides the plane along the base lines and everything laid onto them.
     *
     * <p>Two kinds of line in, because they are joined at two different resolutions: the base
     * is welded at the tolerance given, and what is laid is cut against the welded base and
     * joined at rounding. See the class note on why one weld over both is wrong.
     *
     * @param baseWalls     the base's lines, each saying which line it lies on; a closed
     *                      outline is its sides laid one by one
     * @param weldTolerance how far two reports of one base corner may stand apart and still
     *                      meet
     * @param laidWalls     lines laid onto the base after it, each ending exactly where it
     *                      says it does; none, for the base alone
     * @return every piece, bounded ones and outer ones together, each telling which it is by
     *         its own winding
     */
    public static List<Face> walkFaces(
            List<LabelledWall> baseWalls,
            double weldTolerance,
            List<LabelledWall> laidWalls) {

        var arrangement = layOntoWeldedBase(
            PlanarArrangement.weldArrangement(
                SegmentCrossings.splitAtCrossings(baseWalls), weldTolerance),
            laidWalls);

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

    // The welded base with the laid lines cut into it, joined at rounding.
    //
    // The base as it stands after the weld, so the laid lines are cut against corners that will
    // not move again. Cut once more together, because the weld can swing a base line across a
    // corner of its own just as it can a laid one. With nothing laid there is nothing to cut
    // against, and the base is the arrangement as it stands - the frontier alone is thousands
    // of lines, and cutting them all a second time would find nothing new.
    private static PlanarArrangement layOntoWeldedBase(
            PlanarArrangement base, List<LabelledWall> laidWalls) {

        if (laidWalls.isEmpty()) {
            return base;
        }
        var lines = new ArrayList<>(base.collectLines());

        lines.addAll(laidWalls);

        return PlanarArrangement.weldArrangement(
            SegmentCrossings.splitAtCrossings(lines), Limits.MIN_EDGE_LENGTH);
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

    // Below what area a piece is not a piece. A face narrower than the welding tolerance is
    // thinner than the resolution the lines were joined at, so whether it is there at all is a
    // question about the arithmetic rather than about the shape - and a slit, which is a face
    // walked out and back with no width, is exactly that face with an area of nothing.
    //
    // The base's tolerance for every face, laid lines or not, although laid lines are joined
    // at rounding. The map is drawn at the base's resolution, and a sliver a laid line closes
    // against the shore thinner than that is below it in the same way as a sliver the frontier
    // closes on its own - so both go, and the pieces cover the void less exactly those. The
    // floor rounding alone would allow is no answer either: it is below what the area sum over
    // map-sized coordinates can tell from nothing, so a slit would start coming back as a
    // piece.
    private static double measureSmallestFace(double weldTolerance) {

        var resolution = Math.max(weldTolerance, Limits.MIN_EDGE_LENGTH);

        return resolution * resolution;
    }
}
