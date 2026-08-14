package kmu.maplayers.base.geometry;

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
}
