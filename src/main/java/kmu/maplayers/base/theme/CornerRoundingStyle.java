package kmu.maplayers.base.theme;

/**
 * The corner-rounding pass's half of the border smoothing: whether it runs, plus the shape it
 * works to - the corner radius, the arc segments each corner is drawn with, the angle below
 * which a corner is chamfered instead, and the angle above which a corner is left alone. Half
 * of a {@link BorderSmoothingStyle}, which says why the two are held apart.
 *
 * @param shouldRoundCorners     whether the rounding pass runs at all; off leaves the corners
 *                               sharp
 * @param cornerRadius           the radius each corner is stepped back by before arcing, in
 *                               world units
 * @param cornerSegments         the straight segments each corner's arc is drawn with
 * @param chamferAngleRadians    the interior angle below which a corner is cut flat rather
 *                               than arced; non-positive arcs every corner
 * @param roundBelowAngleRadians the interior angle above which a corner keeps its vertex
 *                               untouched. A cluster border is arcs sampled at fixed angles
 *                               joined by straight runs, and the samples along one run meet
 *                               at all but a straight line: rounding those spends vertices on
 *                               bends that are already smooth, and what is worth rounding is
 *                               the joins BETWEEN runs
 */
public record CornerRoundingStyle(
    boolean shouldRoundCorners,
    double cornerRadius,
    int cornerSegments,
    double chamferAngleRadians,
    double roundBelowAngleRadians) {
}
