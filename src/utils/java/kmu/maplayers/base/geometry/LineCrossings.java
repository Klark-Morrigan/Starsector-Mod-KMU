package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.Segments;

/**
 * Whether two straight runs on the map cross, with the one choice the constructions differ on
 * made by the caller: what two runs meeting at a shared end amount to.
 *
 * <p>The library counts a shared end as a crossing, because two segments meeting there do
 * touch. That is the right answer for a walk stepping across a wall, where grazing a wall's end
 * is still meeting it. It is the wrong one for two spans leaving one anchor: each runs away from
 * the other, nothing overshoots, and counting it would knock out every span but one of any set
 * leaving one place. Both readings are wanted, so the choice is a parameter of the one test
 * rather than a second copy of it.
 */
public final class LineCrossings {

    private LineCrossings() {
    }

    /**
     * Whether two runs cross within both of their own lengths.
     *
     * @param oneFrom      where the first run starts
     * @param oneTo        where it ends
     * @param otherFrom    where the second starts
     * @param otherTo      where it ends
     * @param sharedAnchor what two runs whose ends meet, within the touching tolerance, count as
     * @return whether they cross
     */
    public static boolean isCrossing(
            double[] oneFrom,
            double[] oneTo,
            double[] otherFrom,
            double[] otherTo,
            SharedAnchor sharedAnchor) {

        if (sharedAnchor == SharedAnchor.IS_TOUCH
                && isSharingAnAnchor(oneFrom, oneTo, otherFrom, otherTo)) {

            return false;
        }
        return Segments.intersectSegments(oneFrom, oneTo, otherFrom, otherTo) != null;
    }

    private static boolean isSharingAnAnchor(
            double[] oneFrom,
            double[] oneTo,
            double[] otherFrom,
            double[] otherTo) {

        return isSamePlace(oneFrom, otherFrom)
            || isSamePlace(oneFrom, otherTo)
            || isSamePlace(oneTo, otherFrom)
            || isSamePlace(oneTo, otherTo);
    }

    // Within the touching tolerance rather than exactly, because two runs anchored on one
    // frontage point arrive there by different arithmetic.
    private static boolean isSamePlace(double[] one, double[] other) {
        return Points.computeDistance(one, other) <= DiscUnion.TOUCHING_TOLERANCE;
    }

    /** What two runs meeting at a shared end amount to. */
    public enum SharedAnchor {

        /** A crossing: the runs touch there, which is all a walk stepping across needs. */
        IS_CROSSING,

        /** A touch and no more: runs leaving one anchor head apart, and nothing overshoots. */
        IS_TOUCH
    }
}
