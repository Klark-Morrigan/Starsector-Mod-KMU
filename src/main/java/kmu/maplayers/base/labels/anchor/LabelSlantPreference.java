package kmu.maplayers.base.labels.anchor;

import kmlib.math.geometry.PrincipalAxis;

/**
 * The angle a cluster's label prefers to lean at, and the score penalty a candidate
 * line pays for departing from it. The anchor search leans a name along the cluster's
 * own principal axis rather than screen-horizontal, so most labels pick up the gentle
 * slant of the cluster they sit in instead of reading as artificially level.
 *
 * <p>Two guards keep that lean sane. A cap ({@code maxSlantDegrees}) stops a tall
 * cluster from standing its name vertical - the anti-verticality preference, expressed
 * as a ceiling on the preferred angle rather than a pull toward horizontal. And the
 * cluster's own elongation scales the lean down toward level as the cluster gets
 * rounder: a near-round or tightly-clumped cloud has no trustworthy long axis, so its
 * axis direction is arbitrary noise and the label stays level rather than chasing it.
 *
 * <p>The preference is applied only as a scoring reference - the candidate directions
 * and the box fit are unchanged - so the search still decides on measured lengths, just
 * measured against a per-cluster slant instead of the screen horizontal.
 */
public record LabelSlantPreference(double preferredAngle) {

    // The undirected right angle a label line can be at most from the preference: a
    // line and its preferred angle are both folded into [-90, 90] degrees, so their
    // separation never exceeds a quarter turn, and this normalises the penalty against it.
    private static final double QUARTER_TURN = Math.PI / 2.0;

    /**
     * Resolves the slant a cluster's label prefers from the cluster's principal axis:
     * the axis direction folded into {@code [-90, 90]} degrees, capped to
     * {@code maxSlantDegrees}, then scaled by the axis's elongation so a round cluster
     * (whose direction is meaningless) collapses back to level.
     *
     * @param axis            the cluster's fitted principal axis
     * @param maxSlantDegrees the ceiling on the lean, in degrees; 0 forces level labels
     * @return the resolved slant preference
     */
    public static LabelSlantPreference resolveFrom(PrincipalAxis axis, double maxSlantDegrees) {
        var axisAngle = foldToRightAngle(Math.atan2(axis.axisY(), axis.axisX()));
        var maxSlant = Math.toRadians(maxSlantDegrees);
        var capped = Math.max(-maxSlant, Math.min(maxSlant, axisAngle));
        return new LabelSlantPreference(capped * axis.computeElongation());
    }

    /**
     * The score multiplier for a candidate line: {@code 1} when the line sits exactly at
     * the preferred angle, falling toward {@code 1 - strength} as it turns toward the
     * perpendicular. The falloff is {@code (deviation / 90deg)^exponent}, so a higher
     * exponent leaves near-preferred lines almost unpenalised and bites only as a line
     * swings far off the lean.
     *
     * @param direction the candidate line's unit direction {@code {x, y}} (undirected)
     * @param strength   how much score a far-off line may lose, 0 (no penalty) to 1
     * @param exponent   how sharply the penalty concentrates toward the perpendicular
     * @return the multiplier in {@code [1 - strength, 1]}
     */
    public double computePenaltyMultiplier(double[] direction, double strength, double exponent) {
        var lineAngle = foldToRightAngle(Math.atan2(direction[1], direction[0]));
        var deviation = undirectedAngularDistance(lineAngle, preferredAngle);
        return 1.0 - strength * Math.pow(deviation / QUARTER_TURN, exponent);
    }

    /**
     * The preferred slant as a unit direction, so the search can add it to the candidate
     * fan and land a line exactly on the lean rather than at the nearest fan spoke.
     *
     * @return the unit direction {@code {cos, sin}} of the preferred angle
     */
    public double[] toDirection() {
        return new double[] {Math.cos(preferredAngle), Math.sin(preferredAngle)};
    }

    // Folds any angle into [-90, 90] degrees, the range of an undirected line: a line and
    // its 180-degree opposite are the same line, so both map to one representative angle.
    private static double foldToRightAngle(double angle) {
        var folded = angle;
        while (folded > QUARTER_TURN) {
            folded -= Math.PI;
        }
        while (folded < -QUARTER_TURN) {
            folded += Math.PI;
        }
        return folded;
    }

    // The acute separation between two folded line angles: their raw difference can reach
    // a half turn, but the smaller of it and its supplement is the quarter-turn-bounded
    // angle between the two undirected lines.
    private static double undirectedAngularDistance(double first, double second) {
        var difference = Math.abs(first - second);
        return Math.min(difference, Math.PI - difference);
    }
}
