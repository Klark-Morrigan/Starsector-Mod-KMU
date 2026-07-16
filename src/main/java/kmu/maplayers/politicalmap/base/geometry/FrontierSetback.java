package kmu.maplayers.politicalmap.base.geometry;

import kmlib.math.geometry.Points;

/**
 * Derives the single distance both frontier consumers offset an open-frontier edge by,
 * so an owned cell's push-out and the facing empty cell's pull-in land on the same line
 * by construction rather than by two offsets that merely happen to match.
 *
 * <p>Across an owned system O and an unowned neighbour E, the Voronoi bisector sits at
 * the midpoint, {@code dist(O, E) / 2} from each. The keep-out line sits {@code r} from
 * E, where {@code r} is the keep-out radius. The setback is how far that line lies from
 * the bisector: the owned edge shifts out toward E by it, the empty edge shifts in toward
 * E by it, and both come to rest {@code r} short of E - the pocket left around the dead
 * star. Kept free of the geometry cache and of GL so the rule can be exercised on bare
 * site coordinates.
 */
public final class FrontierSetback {

    private FrontierSetback() {
    }

    /**
     * The setback {@code max(0, dist(O, E) / 2 - r)} - the distance from the raw bisector
     * to the keep-out line. Clamped at zero so two systems closer together than {@code 2r}
     * never push past their halfway line: the owned colour stops at the bisector rather
     * than reaching over a dead star it is nearly on top of.
     *
     * @param ownedSite     the owned system's {@code {x, y}} site
     * @param emptySite     the unowned neighbour's {@code {x, y}} site
     * @param keepOutRadius how close the owned colour may come to the empty star, in world
     *                      units; larger leaves a wider pocket
     * @return the non-negative offset both consumers apply to reach the shared keep-out line
     */
    public static double computeSetback(
            double[] ownedSite,
            double[] emptySite,
            double keepOutRadius) {
        var halfSpacing = Points.computeDistance(ownedSite, emptySite) / 2;
        return Math.max(0, halfSpacing - keepOutRadius);
    }
}
