package kmu.maplayers.base.geometry.v3;

import kmlib.math.geometry.Bounds;
import kmlib.math.geometry.PolygonRegions;

import kmu.maplayers.base.geometry.LineCrossings;

import java.util.ArrayList;
import java.util.List;

/**
 * The water the sector encloses once the links are laid, that no other layer paints.
 *
 * <p>A link changes what the sector IS. Two continents joined by a run of them close a basin
 * between themselves that neither closed alone, and the second trace - the sector walked again
 * with the links as walls - is the only reading in which that basin exists. Its coast reaches
 * shut the basin in; nothing else on the map does.
 *
 * <p><b>The fault it ends is a line with open void inside it.</b> A reader seeing a closed
 * boundary reads the inside as water. Left to the backdrop it says the opposite, about the same
 * place, at the same time - and the boundary is already on screen whether or not the links
 * themselves are switched on, since most of it is continent coastline that was always drawn.
 *
 * <p><b>Everything it closes is somewhere a reader can see.</b> The reaches of that trace run
 * along the cells, so a pocket of it is bounded by continent coastline, by links, or by the
 * coastline the links added - never by open space. Measured on both fixtures, no pocket sits
 * further than about 1500 units from a line the map strokes, which is under half a cell. So there
 * is no test to make here: a distance rule admitted all but a handful, and the handful it turned
 * down were pockets standing exactly one channel off a line, where the threshold and the geometry
 * meet and floating point decides. That is a fill that flickers rather than a rule.
 *
 * <p><b>The one rule is that nothing else may already paint it.</b> Most of what the second trace closes is water
 * the continent coasts close too, and painting it again would make this layer look like it draws
 * the whole sea while its switch merely doubled another layer's work. A pocket any other layer
 * touches at all is dropped rather than trimmed - whole, so that no seam appears where a trim
 * would have run, and so that the layers stay disjoint rather than nearly so.
 *
 * <p>Which makes this the one layer defined by what the others leave: what it adds is exactly the
 * water the drawn map encloses and nobody had painted.
 */
public final class LinkedSectorPockets {

    private LinkedSectorPockets() {
    }

    /**
     * Finds the enclosed water no other layer paints.
     *
     * @param linked      the sector traced again with the links laid, whose reaches close the
     *                    pockets this chooses among
     * @param painted     what the other layers already cover, which this keeps clear of
     * @param ownerBySite each site's owner, index-aligned with the sites, to decide which
     *                    pockets sit inside one owner's area rather than between owners
     * @param rules       the knobs one pocket is built under
     * @return one outline per pocket the drawn map encloses and nothing else fills
     */
    public static List<List<double[]>> findUnpaintedPockets(
            TracedCoasts linked,
            List<List<double[]>> painted,
            List<String> ownerBySite,
            VoidPockets.PocketRules rules) {

        var boxedPainted = boxRings(painted);
        var outlines = new ArrayList<List<double[]>>();

        for (var walled : CoastPockets.findCoastPockets(linked, ownerBySite, rules)) {
            for (var outline : walled.pocket().outlines()) {

                if (!isOverlappingAnything(outline, boxedPainted)) {
                    outlines.add(outline);
                }
            }
        }
        return List.copyOf(outlines);
    }

    // Whether a pocket shares any of its water with something already painted.
    //
    // Three tests, because two shapes can meet in three ways and each alone misses the others.
    // Either can hold a vertex of the other, which containment catches; one can swallow the
    // other whole, which is why containment is asked both ways round; and two can cross without
    // either holding a vertex at all, overlapping in a lens between four edge crossings. The
    // last is the one a vertex test cannot see, and measured on the fixtures it is most of what
    // a vertex test lets through - a tenth of a cell of water painted twice.
    private static boolean isOverlappingAnything(
            List<double[]> outline,
            List<BoxedRing> painted) {

        // A pocket with no outline holds no water to share.
        if (outline.isEmpty()) {
            return false;
        }

        var bounds = Bounds.computeEnclosingBounds(outline);

        for (var boxed : painted) {

            // Nowhere near, so nothing below can be true. Asked first because it is the cheap
            // answer for almost every pair: a pocket meets a handful of the rings on a map and
            // misses the rest by a sector's width.
            if (!bounds.overlaps(boxed.bounds())) {
                continue;
            }

            var ring = boxed.ring();

            if (isHoldingAnyPoint(ring, outline)
                    || isHoldingAnyPoint(outline, ring)
                    || isCrossingAnyEdge(outline, ring)) {

                return true;
            }
        }
        return false;
    }

    private static boolean isHoldingAnyPoint(List<double[]> ring, List<double[]> points) {

        for (var point : points) {

            if (PolygonRegions.isPointInsideRing(ring, point[0], point[1])) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCrossingAnyEdge(List<double[]> outline, List<double[]> ring) {

        for (var here = 1; here < outline.size(); here++) {
            for (var there = 1; there < ring.size(); there++) {

                if (LineCrossings.isCrossing(
                        outline.get(here - 1), outline.get(here),
                        ring.get(there - 1), ring.get(there),
                        LineCrossings.SharedAnchor.IS_CROSSING)) {

                    return true;
                }
            }
        }
        return false;
    }

    // Each painted ring with its box, measured once rather than once per pocket asked about it.
    // An empty ring paints nothing, so it is left out rather than boxed.
    private static List<BoxedRing> boxRings(List<List<double[]>> rings) {

        var boxed = new ArrayList<BoxedRing>(rings.size());

        for (var ring : rings) {

            if (!ring.isEmpty()) {
                boxed.add(new BoxedRing(ring, Bounds.computeEnclosingBounds(ring)));
            }
        }
        return boxed;
    }

    private record BoxedRing(
        List<double[]> ring,
        Bounds bounds) {
    }
}
