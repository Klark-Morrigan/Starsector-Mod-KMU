package kmu.maplayers.base.labels.anchor.specifications;

import kmu.maplayers.base.render.clusters.ClusterBorderTrace;

/**
 * Where the cluster-anchor search generates its candidate lines: the border rings a label
 * is confined within, and the fan of directions and parallel offsets swept across the
 * cluster. What each candidate is then measured against belongs to the band-fit tuning
 * beside this record, so this one answers only how many lines the search considers.
 *
 * @param borderTrace    the cluster-border trace the anchor clips against - shared with
 *                       the cluster build, so the anchor sees the same rings the player
 *                       does by construction
 * @param directionCount the number of directions the candidate fan spans over the
 *                       half-circle
 * @param offsetCount    the number of parallel lines swept per direction
 */
public record AnchorSearch(
    ClusterBorderTrace borderTrace,
    int directionCount,
    int offsetCount) {
}
