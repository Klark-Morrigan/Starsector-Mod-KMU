package kmu.maplayers.base.geometry.v3;

import kmlib.math.geometry.CornerRounding;

/**
 * The knobs a coast is traced under, in the units they are set in.
 *
 * <p>Multiples of a cell radius rather than distances, because that is what they mean:
 * "closer than a cell across" is a claim about the map, where a number of units stops
 * being one the moment the reach slider moves. Converting to distances is
 * {@link Coastlines}' job and happens once, so no caller can do it differently.
 *
 * @param bridgeReachMultiple how far apart two cells may sit and still be walled together,
 *                            centre to centre, in cell radii
 * @param minFrontageShare    how much of its own border a cell has to face the void with
 *                            to be worth passing through, as a share of the whole turn.
 *                            Zero asks nothing and drops nobody
 * @param minLakeShare        how much water a hole has to hold to be drawn as a lake, as
 *                            a share of one cell's area. The second floor, beside the
 *                            frontage's: that one judges a cell's stretch of shore, this
 *                            one a whole lake - and it is judged on the true hole, so no
 *                            smoothing knob can move it. Zero keeps every puddle
 * @param rounding            how the drawn line is rounded where it turns sharply. Read by
 *                            {@link CoastRounding} and by no stage of the trace, which is
 *                            what makes it the one setting here a reader could take to
 *                            nothing and still have a border: the floors above are
 *                            selection's and decide which stretches and lakes the line
 *                            passes through, where this only takes the tips off the joins
 *                            that leaves. Carried with them all the same, so that one
 *                            setting drives every drawing of a coast traced under them
 * @param reachAnchor         which turn placement reads the reachable window on, which
 *                            decides where every straight run lands. A knob rather than a
 *                            constant because the two answers draw different maps and the
 *                            better-drawn one is not the arithmetically correct one - what
 *                            each costs is on {@link StraightRuns.ReachAnchor}
 */
public record CoastRules(
    double bridgeReachMultiple,
    double minFrontageShare,
    double minLakeShare,
    CornerRounding rounding,
    StraightRuns.ReachAnchor reachAnchor) {
}
