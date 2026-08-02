package kmu.maplayers.base.theme;

/**
 * The corner-rounding pass's half of the border smoothing: whether it runs, plus the shape it
 * works to - the corner radius, the arc segments each corner is drawn with, and the angle below
 * which a corner is chamfered instead. Half of a {@link BorderSmoothingStyle}, which says why the
 * two are held apart.
 *
 * @param shouldRoundCorners  whether the rounding pass runs at all; off leaves the corners sharp
 * @param cornerRadius        the radius each corner is stepped back by before arcing, in world
 *                            units
 * @param cornerSegments      the straight segments each corner's arc is drawn with
 * @param chamferAngleRadians the interior angle below which a corner is cut flat rather than
 *                            arced; non-positive arcs every corner
 */
public record CornerRoundingStyle(
    boolean shouldRoundCorners,
    double cornerRadius,
    int cornerSegments,
    double chamferAngleRadians) {
}
