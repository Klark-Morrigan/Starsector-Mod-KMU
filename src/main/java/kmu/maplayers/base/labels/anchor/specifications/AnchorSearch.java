package kmu.maplayers.base.labels.anchor.specifications;

import kmu.maplayers.base.render.clusters.ClusterBorderTrace;

/**
 * Where the cluster-anchor search generates and clips its candidate lines: the border
 * rings a label is confined within, and the fan of directions and parallel offsets swept
 * across the cluster.
 *
 * @param borderTrace      the cluster-border trace the anchor clips against - shared with
 *                         the territory build, so the anchor sees the same rings the player
 *                         does by construction
 * @param endInsetDistance how far each end of the clear interval pulls inward, in world
 *                         units - the border-inset multiple already resolved to a distance
 * @param iconClearance    the keep-out radius around each system icon, world units
 * @param directionCount   the number of directions the candidate fan spans over the
 *                         half-circle
 * @param offsetCount      the number of parallel lines swept per direction
 */
public record AnchorSearch(
        ClusterBorderTrace borderTrace,
        double endInsetDistance,
        double iconClearance,
        int directionCount,
        int offsetCount) {
}
