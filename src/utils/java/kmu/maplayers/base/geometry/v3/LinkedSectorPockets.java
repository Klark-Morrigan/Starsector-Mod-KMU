package kmu.maplayers.base.geometry.v3;

import kmlib.math.geometry.PolygonRegions;
import kmlib.math.geometry.Segments;

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
            Coastlines.TracedCoasts linked,
            List<List<double[]>> painted,
            List<String> ownerBySite,
            VoidPockets.PocketRules rules) {

        var outlines = new ArrayList<List<double[]>>();

        for (var walled : CoastPockets.findCoastPockets(linked, ownerBySite, rules)) {
            for (var outline : walled.pocket().outlines()) {

                if (!isOverlappingAnything(outline, painted)) {
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
    // last is the one a vertex test cannot see, and measured on the fixtures it was most of what
    // a vertex test let through - a tenth of a cell of water painted twice.
    private static boolean isOverlappingAnything(
            List<double[]> outline,
            List<List<double[]>> painted) {

        var bounds = measureBounds(outline);

        for (var ring : painted) {

            // Nowhere near, so nothing below can be true. Asked first because it is the cheap
            // answer for almost every pair: a pocket meets a handful of the rings on a map and
            // misses the rest by a sector's width.
            if (isApart(bounds, measureBounds(ring))) {
                continue;
            }

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

                if (Segments.intersectSegments(
                        outline.get(here - 1), outline.get(here),
                        ring.get(there - 1), ring.get(there)) != null) {

                    return true;
                }
            }
        }
        return false;
    }

    // One ring's extent as low and high corners, for the rejection above.
    private static double[][] measureBounds(List<double[]> ring) {

        var low = new double[] {Double.MAX_VALUE, Double.MAX_VALUE};
        var high = new double[] {-Double.MAX_VALUE, -Double.MAX_VALUE};

        for (var point : ring) {

            low[0] = Math.min(low[0], point[0]);
            low[1] = Math.min(low[1], point[1]);
            high[0] = Math.max(high[0], point[0]);
            high[1] = Math.max(high[1], point[1]);
        }
        return new double[][] {low, high};
    }

    private static boolean isApart(double[][] one, double[][] other) {

        return one[1][0] < other[0][0]
            || other[1][0] < one[0][0]
            || one[1][1] < other[0][1]
            || other[1][1] < one[0][1];
    }
}
