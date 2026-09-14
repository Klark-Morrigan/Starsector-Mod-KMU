package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Bounds;

import java.util.List;

/**
 * Reading how far a traced ring reaches along x, which is how a case tells an inset edge from a
 * raw one: a border that stopped short of a cell edge by the channel reads as an extent.
 *
 * <p>Named readers over {@link Bounds} rather than a walk of their own. A case asserting on one
 * extent reads better for saying which extent it means, and measuring it here a second time
 * would be a second answer to a question the geometry library already answers.
 *
 * <p>Final class with a private constructor: fixture of static wiring, no instances.
 */
public final class RingExtentFixture {

    private RingExtentFixture() {
        // fixture of static wiring, no instances.
    }

    /** The largest x any vertex of the ring reaches. */
    public static double readMaxXOf(List<double[]> ring) {
        return Bounds.computeEnclosingBounds(ring).maxX();
    }

    /** The smallest x any vertex of the ring reaches. */
    public static double readMinXOf(List<double[]> ring) {
        return Bounds.computeEnclosingBounds(ring).minX();
    }
}
