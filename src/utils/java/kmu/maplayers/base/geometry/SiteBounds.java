package kmu.maplayers.base.geometry;

import java.util.List;

/**
 * The extent a sector's sites occupy.
 *
 * <p>A record rather than a four-slot array because the slots mean different things: reading
 * a corner back out by index invites a transposed x for y that draws a plausible, wrong map.
 *
 * @param minX the leftmost site's x
 * @param minY the lowest site's y
 * @param maxX the rightmost site's x
 * @param maxY the highest site's y
 */
record SiteBounds(
    double minX,
    double minY,
    double maxX,
    double maxY) {

    /**
     * Measures the box the sites fall inside.
     *
     * @param sites the sites
     * @return their extent
     */
    static SiteBounds measureAround(List<double[]> sites) {

        var minX = Double.MAX_VALUE;
        var minY = Double.MAX_VALUE;
        var maxX = -Double.MAX_VALUE;
        var maxY = -Double.MAX_VALUE;

        for (var site : sites) {

            minX = Math.min(minX, site[0]);
            minY = Math.min(minY, site[1]);
            maxX = Math.max(maxX, site[0]);
            maxY = Math.max(maxY, site[1]);
        }
        return new SiteBounds(minX, minY, maxX, maxY);
    }

    /**
     * The longer of the two sides.
     *
     * @return how far the sector reaches across at its widest
     */
    double measureWidestSpan() {
        return Math.max(maxX - minX, maxY - minY);
    }

    double findCentreX() {
        return (minX + maxX) / 2;
    }

    double findCentreY() {
        return (minY + maxY) / 2;
    }

    /**
     * The same box with room left around it.
     *
     * @param margin how much to leave on every side
     * @return the padded extent
     */
    SiteBounds expandBy(double margin) {
        return new SiteBounds(minX - margin, minY - margin, maxX + margin, maxY + margin);
    }
}
