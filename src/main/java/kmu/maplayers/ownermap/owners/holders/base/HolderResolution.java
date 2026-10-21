package kmu.maplayers.ownermap.owners.holders.base;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.render.clusters.SystemFillStates;
import kmu.maplayers.ownermap.owners.SystemOwner;

import java.util.Map;

/**
 * What a layer's {@link HolderProvider} answers for one walk: who paints each star system, and
 * which of those owned systems draw hatched, or with no fill at all, rather than solid inside their
 * bloc's one border.
 *
 * <p>The holding half of a {@link kmu.maplayers.ownermap.owners.ResolvedOwners}.
 * {@link HolderOwnerSource} adds the habitation reads off the same pass and hands the whole to the
 * tier, so no build stage names the provider that produced it. That is what lets one view resolve
 * holding from the live economy while another resolves it some other way, with no fork in the pass
 * that shapes, borders, and labels the result.
 *
 * <p>The fill states ride inside this one value rather than through a second seam, because they are
 * co-produced with the holder map in the one call that resolves it: a system's holder and how its
 * fill is drawn are decided together.
 *
 * @param ownerBySystemKey the bloc painting each owned system, keyed by {@link SystemKey}; a
 *                         system with no holder is absent, so it draws as an uninhabited cell
 * @param fillStates       the owned systems drawn hatched - under a filter, the systems the
 *                         spotlit bloc is present in while another bloc holds them - and the ones
 *                         drawn with no fill, held by their bloc for border and label alone;
 *                         {@link SystemFillStates#ALL_SOLID} when the whole resolution fills solid
 */
public record HolderResolution(
    Map<SystemKey, SystemOwner> ownerBySystemKey,
    SystemFillStates fillStates) {
}
