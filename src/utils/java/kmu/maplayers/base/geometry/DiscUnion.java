package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.Segments;

import java.util.List;

/**
 * The union of one equal-radius reach disc per site - the shape whose complement is the void.
 *
 * <p>One value rather than a site list and a reach handed around side by side, because the
 * two only mean anything together: every question asked of the union is about THESE discs at
 * THIS reach, and passing the pair separately lets a caller marry the sites of one
 * construction to the reach of another and still compile.
 *
 * <p>That every disc shares the one reach is the premise of the whole construction, so it is
 * carried as a single number rather than one per site: no disc can contain another, two
 * circles either miss or cross at exactly two points, and everything downstream is closed
 * form because of it.
 *
 * @param sites where each disc is centred
 * @param reach how far every disc reaches - the one radius all of them share
 */
public record DiscUnion(
    List<double[]> sites,
    double reach) {

    /**
     * How far inside a disc something may reach and still count as touching it rather than
     * entering it.
     *
     * <p>Zero slack is not an option here, because the interesting points are exactly ON a
     * circle. Two discs cross at a point that lies on both of them, and every line the coast
     * draws begins and ends on a border - so without a hair of slack each of those lands a
     * rounding error inside its neighbour and is read as buried. The wall under such a line is
     * then thrown away while the line itself is still drawn, which is a fill missing under a
     * coast that says the void was shut in.
     *
     * <p>One number for every such question rather than one per asker. Whether a run may be
     * drawn and whether a wall under it is on the boundary are the same question asked from
     * two sides, and at different slacks the first approves precisely what the second refuses -
     * a disagreement neither can detect, because each is consistent with itself.
     *
     * <p>A unit against a reach of thousands: far too small to admit anything that genuinely
     * overlaps, far too large for any rounding to cross.
     */
    public static final double TOUCHING_TOLERANCE = 1;

    /**
     * Whether a point lies inside the union rather than in the void.
     *
     * <p>The definition of void, asked directly: a point is void when its nearest site is
     * further off than the reach. Everything that walks the void, samples it or probes it asks
     * this, and the reach it is asked at is the whole of what distinguishes the map's own void
     * from the void a shape is drawn against.
     *
     * @param point the {x, y} point to place
     * @return whether any disc holds it
     */
    public boolean isPointInside(double[] point) {

        for (var site : sites) {

            if (Points.computeDistance(point, site) < reach) {
                return true;
            }
        }
        return false;
    }

    /**
     * How far inside one disc a point lies.
     *
     * <p>Asked of the point rather than of an angle, because being inside a disc is what
     * "buried" means and needs no interval arithmetic to agree with.
     *
     * @param point  the {x, y} point to measure
     * @param site   which disc to measure against
     * @param toEdge how far out that disc's edge is taken to be. The union's own reach for
     *               anything sitting on it, and the point's own distance from its cell for a
     *               landing that sits on a NARROWER circle than the one being traced - which
     *               is what a coast's reaches do, their ends being points on the cells' own
     *               borders wherever the trace is running
     * @return how far past that edge the point sits, negative when it is outside
     */
    public double measureCoverOf(double[] point, int site, double toEdge) {
        return toEdge - Points.computeDistance(point, sites.get(site));
    }

    /**
     * How far a straight segment reaches inside one disc.
     *
     * <p>Here rather than at either place that asks, because both ask the same thing and one
     * of them decides whether a line may be drawn while the other decides whether a line that
     * WAS drawn is a fault. Written out twice they are one sign flip apart, and a flip makes
     * the second certify exactly what the first rejected - a disagreement neither of them can
     * detect, because each is consistent with itself.
     *
     * @param from  where the segment starts
     * @param to    where it ends
     * @param site  which disc to measure against
     * @return how far past the disc's edge the segment reaches, negative when it stays
     *         outside and zero when it grazes
     */
    public double measureIncursionInto(double[] from, double[] to, int site) {

        return reach - Segments.computeDistanceToPoint(from, to, sites.get(site));
    }
}
