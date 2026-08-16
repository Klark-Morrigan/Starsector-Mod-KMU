package kmu.maplayers.base.geometry;

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
record DiscUnion(
    List<double[]> sites,
    double reach) {

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
    double measureIncursionInto(double[] from, double[] to, int site) {

        return reach - Segments.computeDistanceToPoint(from, to, sites.get(site));
    }
}
