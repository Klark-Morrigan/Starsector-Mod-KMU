package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Segments;

import java.util.ArrayList;
import java.util.List;

/**
 * The water behind the coastline the links added.
 *
 * <p>A link changes the shape of the sector, and the second trace draws what it changed it to.
 * Part of that line is new - the isthmus a link becomes, the rim of an island it reached, the
 * outer border sweeping round a pair of continents it joined - and {@link IntercontinentalCoasts}
 * strokes exactly those stretches. This is what lies behind them.
 *
 * <p><b>Without it the map draws a line with open void inside it.</b> A reader who sees a
 * coastline reads the water side as water; painted as backdrop it says the opposite, and the two
 * statements are about the same place. That is the whole reason this layer exists - not that the
 * void is unaccounted for, but that a line already on screen is making a claim nothing honours.
 *
 * <p><b>Only what a NEW stretch of shore closed.</b> The second trace redraws the whole sector,
 * so most of what its reaches shut in is water the continent coasts already hold and the coast
 * fill already paints. Kept wholesale it came to eleven cells of water on one fixture against the
 * one and a half that is actually behind a new line - the rest being void that is open by every
 * line a reader can see, and painting it would say the sector is enclosed where it is not.
 *
 * <p><b>So the test is the drawn line itself, not the trace behind it.</b> A pocket is kept where
 * one of the runs the map strokes as linked shore runs along its edge. Asking instead whether a
 * closing reach is new to the second trace sounds equivalent and is not: the second trace re-lays
 * the whole sector, so reaches are new to it all over the map, and that rule kept fifty-five
 * pockets on one fixture of which twelve sat more than ten thousand units from any stroked run.
 * Water behind a line nobody draws is the very thing this layer exists to stop.
 *
 * <p><b>The links themselves are not the test.</b> A pocket walled by a link is the link fill's
 * ({@link IntercontinentalPockets}), which is a different claim - how much of the sector a run of
 * links took in, rather than what the coastline they made now encloses. The two overlap and are
 * painted in one colour, so the overlap costs nothing.
 */
public final class LinkedShorePockets {

    private LinkedShorePockets() {
    }

    /**
     * Finds the water the linked shore shut in behind it.
     *
     * @param linked      the sector traced again with the links laid, whose reaches close the
     *                    pockets this chooses among
     * @param drawnShores the runs the map strokes as the linked shore, which are what decides
     *                    whether a pocket is one of these
     * @param ownerBySite each site's owner, index-aligned with the sites, to decide which
     *                    pockets sit inside one owner's area rather than between owners
     * @param rules       the knobs one pocket is built under
     * @return one outline per pocket a stroked run of linked shore holds
     */
    public static List<List<double[]>> findLinkedShorePockets(
            Coastlines.TracedCoasts linked,
            List<List<double[]>> drawnShores,
            List<String> ownerBySite,
            VoidPockets.PocketRules rules) {

        // No stretch is stroked, so there is no line for water to be behind - which is the case
        // wherever no link was laid at all.
        if (drawnShores.isEmpty()) {
            return List.of();
        }

        var channel = rules.parameters().borderInset();
        var outlines = new ArrayList<List<double[]>>();

        for (var walled : CoastPockets.findCoastPockets(linked, ownerBySite, rules)) {
            for (var outline : walled.pocket().outlines()) {

                if (isRunAlongside(outline, drawnShores, channel)) {
                    outlines.add(outline);
                }
            }
        }
        return List.copyOf(outlines);
    }

    // Whether a stroked run passes along a pocket's edge.
    //
    // Against the run's SEGMENTS rather than its sampled points, and held to one channel. A
    // pocket gives up half a channel against the wall that closed it, so its outline stands that
    // far off the line and no further; measured to the nearest sampled point instead, a run
    // sampled coarsely would read as further away than it is, and the threshold would have to be
    // loosened until it began admitting pockets a different line holds.
    private static boolean isRunAlongside(
            List<double[]> outline,
            List<List<double[]>> runs,
            double channel) {

        for (var point : outline) {
            for (var run : runs) {
                for (var step = 1; step < run.size(); step++) {

                    if (Segments.computeDistanceToPoint(
                            run.get(step - 1), run.get(step), point) <= channel) {

                        return true;
                    }
                }
            }
        }
        return false;
    }
}
