package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.PolygonRegions;

import java.util.ArrayList;
import java.util.List;

/**
 * One piece of the plane a walk closed: the ring around it, what each edge of that ring lies
 * on, and the rings of anything cut out of it.
 *
 * <p>The unit the whole construction is built out of. A face is not a shape somebody drew: it
 * is what is left over once every line has been laid, so its boundary is made of the lines
 * themselves and two faces either side of a line share that line exactly. That is the property
 * a division has and a set of separately traced outlines does not.
 *
 * <p><b>Every edge says which line it lies on.</b> The corners alone say where a face is; the
 * labels say what it is - which cell each stretch of shore belongs to, which wall closed it -
 * and everything that reads a face for meaning rather than for drawing reads those. Entry
 * {@code i} names the edge leaving corner {@code i}, the last wrapping to the first.
 *
 * <p><b>A face may have holes, and the largest ones always do.</b> The sea is everything beyond
 * the cells, which is one piece with a hole in it per group of cells; a group with a lake in it
 * is one piece with that lake cut out. A face without holes could not state either, and a
 * reader handed the outline alone would fill over the very things the piece runs around. Each
 * hole carries its own labels, because a hole's boundary is as much a part of what bounds the
 * piece as the outline is - the sea's shore IS its holes.
 *
 * <p><b>Bounded or outer, told by the winding.</b> A walk hands back both, and they are the
 * same kind of thing walked in opposite directions: go round a bounded piece with the piece on
 * your left and you come back counter-clockwise, go round the endless outside the same way and
 * you come back clockwise. So which one a face is, is read off its own ring rather than carried
 * beside it, and nothing can label it one way and wind it the other.
 *
 * <p>A boundary may walk a line twice - out along a wall that divides nothing and back down its
 * other side. That is the face genuinely having a slit in it rather than a fault: the two passes
 * cancel in the area, so a slit costs nothing and is still there to be seen.
 */
public final class Face {

    private final LabelledRing outline;

    private final List<LabelledRing> holes;

    private final double signedArea;

    private Face(LabelledRing outline, List<LabelledRing> holes, double signedArea) {
        this.outline = outline;
        this.holes = holes;
        this.signedArea = signedArea;
    }

    /**
     * The face a closed ring bounds, with nothing cut out of it.
     *
     * <p>The winding is measured here, once, rather than taken on the caller's word - which is
     * what makes a face that says it is the outer one and winds like a bounded one impossible
     * to build.
     *
     * @param outline the ring, in walk order and without repeating its first point
     * @return the face
     */
    public static Face encloseFace(LabelledRing outline) {

        return new Face(
            outline, List.of(), PolygonRegions.computeSignedArea(outline.vertices()));
    }

    /**
     * The same face with something cut out of it.
     *
     * @param hole the ring of what is cut out, labelled as its own boundary
     * @return a face the size of this one less that hole
     */
    public Face cutOut(LabelledRing hole) {

        var cut = new ArrayList<>(holes);

        cut.add(hole);

        // Taken off rather than recomputed: a hole is wound against its piece, so its own
        // signed area already carries the sign that removes it.
        return new Face(
            outline,
            List.copyOf(cut),
            signedArea + PolygonRegions.computeSignedArea(hole.vertices()));
    }

    /**
     * The ring this face is bounded by.
     *
     * @return the corners in walk order, without a repeated closing point
     */
    public List<double[]> boundary() {
        return outline.vertices();
    }

    /**
     * That same ring with its labels, in the form the holes already come in.
     *
     * <p>So a pass over every ring of a face can walk one kind of thing. Asked for the corners
     * and the labels separately, each such pass had to pair them back up itself, which is a
     * line written the same way in three places and only ever wrong in one.
     *
     * @return the boundary as a labelled ring
     */
    public LabelledRing outline() {
        return outline;
    }

    /**
     * Which line each edge of that ring lies on.
     *
     * @return one label per edge, entry {@code i} naming the edge leaving corner {@code i}
     */
    public int[] edgeLabels() {
        return outline.edgeLabels();
    }

    /**
     * The rings of everything cut out of this face.
     *
     * @return one ring per hole, each labelled as its own boundary; empty for a solid piece
     */
    public List<LabelledRing> holes() {
        return holes;
    }

    /**
     * Whether this is the endless piece outside everything rather than a piece of it.
     *
     * @return true for the outer face
     */
    public boolean isOuterFace() {
        return signedArea < 0;
    }

    /**
     * How much plane this face covers, holes taken off.
     *
     * @return the area, unsigned; the outer face reports the area of what it wraps rather than
     *         an endless one, since a walk can only measure the ring it was given
     */
    public double measureArea() {
        return Math.abs(signedArea);
    }
}
