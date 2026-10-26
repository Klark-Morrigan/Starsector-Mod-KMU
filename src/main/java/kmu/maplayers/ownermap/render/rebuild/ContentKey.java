package kmu.maplayers.ownermap.render.rebuild;

import kmu.maplayers.ownermap.ContentInputs;

/**
 * Everything the clusters are styled under, compared whole: the view's terms and the sidebar picks
 * this frame sampled.
 *
 * <p>The picks are held as the values sampled rather than as the counters their flips raise,
 * because a counter reports that somebody clicked something while the values answer the only
 * question a rebuild turns on: would this build come out the same. Two readings holding the same
 * picks are one bake whatever has been clicked between them, and a screen switch moves every pick
 * while no counter moves at all.
 *
 * @param viewKey       the terms shared with the owners key
 * @param contentInputs the sidebar picks a rebuild would bake under
 */
record ContentKey(
    ViewKey viewKey,
    ContentInputs contentInputs) {
}
