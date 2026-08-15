package kmu.maplayers.base.geometry;

/**
 * Turning angles into a form that can be compared, and the constants that define one.
 *
 * <p>Its own class because more than one construction here works in angles rather than points
 * - the boundary trace, which cuts each circle into intervals, and the coast smoothing, which
 * slides a point along one - and both need the same three things: put an angle in a known
 * range, measure how far apart two are, and place one in the same turn as another so a
 * comparison means something.
 *
 * <p>Nothing shape-like belongs here. kmlib owns the real geometry; what is here is the
 * bookkeeping that makes two angles from different calculations safe to compare.
 */
final class Angles {

    static final double FULL_TURN = 2 * Math.PI;
    static final double HALF_TURN = Math.PI;

    private Angles() {
    }

    /**
     * The same direction expressed in the first turn.
     *
     * @param angle any angle
     * @return the same direction, from zero up to but not including a full turn
     */
    static double normalise(double angle) {

        var turned = angle % FULL_TURN;
        return turned < 0 ? turned + FULL_TURN : turned;
    }

    /**
     * How far apart two directions are, whichever way round is shorter.
     *
     * @param from one direction
     * @param to   the other
     * @return the angle between them, never more than a half turn
     */
    static double measureGap(double from, double to) {

        var turned = normalise(to - from);
        return turned > HALF_TURN ? FULL_TURN - turned : turned;
    }

    /**
     * The same direction, moved into the turn that begins at {@code origin}.
     *
     * <p>What makes an angle comparable to an interval. An interval is carried as a pair of
     * angles that may run past a full turn, so an angle taken fresh from {@code atan2} is in
     * the wrong turn as often as not, and comparing the two directly puts a direction outside
     * an interval that in fact contains it.
     *
     * @param angle  the direction to move
     * @param origin where the turn begins
     * @return the same direction, at or after {@code origin} and less than a turn past it
     */
    static double placeAfter(double angle, double origin) {
        return origin + normalise(angle - origin);
    }

    /**
     * The value in a range nearest to the one asked for.
     *
     * <p>An empty range - one whose floor has been pushed past its ceiling by two constraints
     * that do not overlap - collapses to whichever end is nearer, so a caller that cannot have
     * what it wants still gets the closest thing to it rather than a reversed interval.
     *
     * @param wanted  the value asked for
     * @param lowest  the floor
     * @param highest the ceiling
     * @return the nearest allowed value
     */
    static double clampInto(double wanted, double lowest, double highest) {

        if (lowest > highest) {
            return Math.abs(wanted - lowest) <= Math.abs(wanted - highest) ? lowest : highest;
        }
        return Math.min(highest, Math.max(lowest, wanted));
    }
}
