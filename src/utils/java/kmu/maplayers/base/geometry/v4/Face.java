package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.PolygonRegions;

import java.util.List;

/**
 * One piece of the plane a walk closed, as the ring that bounds it.
 *
 * <p>The unit the whole construction is built out of. A face is not a shape somebody drew: it
 * is what is left over once every line has been laid, so its boundary is made of the lines
 * themselves and two faces either side of a line share that line exactly. That is the property
 * a division has and a set of separately traced outlines does not.
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

    private final List<double[]> boundary;

    private final double signedArea;

    private Face(List<double[]> boundary, double signedArea) {
        this.boundary = boundary;
        this.signedArea = signedArea;
    }

    /**
     * The face a closed ring bounds.
     *
     * <p>The winding is measured here, once, rather than taken on the caller's word - which is
     * what makes a face that says it is the outer one and winds like a bounded one impossible
     * to build.
     *
     * @param boundary the ring, in walk order and without repeating its first point
     * @return the face
     */
    public static Face encloseFace(List<double[]> boundary) {

        return new Face(List.copyOf(boundary), PolygonRegions.computeSignedArea(boundary));
    }

    /**
     * The ring this face is bounded by.
     *
     * @return the vertices in walk order, without a repeated closing point
     */
    public List<double[]> boundary() {
        return boundary;
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
     * How much plane this face covers.
     *
     * @return the area, unsigned; the outer face reports the area of what it wraps rather than
     *         an endless one, since a walk can only measure the ring it was given
     */
    public double measureArea() {
        return Math.abs(signedArea);
    }
}
