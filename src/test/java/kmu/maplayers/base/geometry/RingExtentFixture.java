package kmu.maplayers.base.geometry;

import java.util.List;

/**
 * Reading how far a traced ring reaches along x, which is how a case tells an inset edge from a
 * raw one: a border that stopped short of a cell edge by the channel reads as an extent.
 *
 * <p>Final class with a private constructor: fixture of static wiring, no instances.
 */
public final class RingExtentFixture {

    private RingExtentFixture() {
        // fixture of static wiring, no instances.
    }

    /** The largest x any vertex of the ring reaches. */
    public static double readMaxXOf(List<double[]> ring) {
        return ring.stream().mapToDouble(vertex -> vertex[0]).max().orElseThrow();
    }

    /** The smallest x any vertex of the ring reaches. */
    public static double readMinXOf(List<double[]> ring) {
        return ring.stream().mapToDouble(vertex -> vertex[0]).min().orElseThrow();
    }
}
