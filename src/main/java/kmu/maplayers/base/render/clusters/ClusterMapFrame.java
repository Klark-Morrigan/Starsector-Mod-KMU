package kmu.maplayers.base.render.clusters;

/**
 * One frame of the cluster overlay: what there is to paint, and the two map-wide values every
 * emission in that frame scales and fades by.
 *
 * <p>The three travel together through every pass of the overlay and none of them varies within a
 * frame, so carrying them as one value leaves each pass's own signature to say what that pass
 * actually decides - the primitive it emits, the run it selects, the stroke it strokes with -
 * rather than restating the ambient three in front of it.
 *
 * <p>It is deliberately not shared with the other renderers of the same map pass. A record that
 * one class builds and unpacks stays cheap to change; lifted to the render surface on this
 * evidence alone it would become the bag every renderer adds its own field to. A second consumer
 * reaching for it is what would justify the lift.
 *
 * @param drawLists the already-baked runs this frame paints, opaque to the emission
 * @param factor    the world-to-map scale every coordinate is multiplied by, from vanilla's
 *                  {@code CampaignTerrainPlugin.renderOnMap} and named as vanilla names it so the
 *                  trail back to that call survives
 * @param alphaMult the map's own fade for this frame, multiplied into every element's opacity
 */
record ClusterMapFrame(
    ClusterDrawLists drawLists,
    float factor,
    float alphaMult) {
}
