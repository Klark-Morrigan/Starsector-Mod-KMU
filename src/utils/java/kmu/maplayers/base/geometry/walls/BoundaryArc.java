package kmu.maplayers.base.geometry.walls;

/**
 * One stretch of a circle that nothing covers - a single edge of the union's boundary.
 *
 * <p>What the cover sweep hands the cycle walk: the sweep cuts every circle into these, and the
 * walk joins them by their terminals.
 *
 * @param circle    whose circle it runs on
 * @param fromAngle the angle it begins at
 * @param toAngle   the angle it ends at, always greater than {@code fromAngle}
 * @param startsAt  the terminal it begins at, naming either the disc it comes out of or
 *                  the chord mouth it leaves
 * @param endsAt    the terminal it runs on to, which is the {@code startsAt} of whichever
 *                  arc the boundary continues along
 */
record BoundaryArc(
    int circle,
    double fromAngle,
    double toAngle,
    long startsAt,
    long endsAt) {
}
