package kmu.maplayers.ownermap.owners.holders.base;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.render.clusters.FillSplit.FillState;
import kmu.maplayers.base.render.clusters.SystemFillStates;
import kmu.maplayers.ownermap.owners.SystemOwner;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A layer's holding over one walk: who paints each star system, and which of those owned systems
 * draw hatched, or with no fill at all, rather than solid inside their bloc's one border.
 *
 * <p>The holding half of a {@link kmu.maplayers.ownermap.owners.ResolvedOwners}.
 * {@link HolderOwnerSource} adds the habitation reads off the same pass and hands the whole to the
 * tier, so no build stage names the rule that produced it. That is what lets one view resolve
 * holding from the live economy while another resolves it some other way, with no fork in the pass
 * that shapes, borders, and labels the result.
 *
 * <p>The fill states ride inside this one value rather than through a second seam, because a
 * system's holder and how its fill is drawn are decided together, in one per-system answer.
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

    /**
     * The holding of every system a pass walks, as a layer's per-system rule answers each one.
     *
     * <p>The one way a whole-sector holding is built. A rebuild and a batch re-deriving a marked
     * system then ask the same rule, so a refreshed cell cannot part from the rebuild around it.
     *
     * @param pass     the reading of the sector whose systems are walked, in the sector's own order; a
     *                 pass over no sector yields empty holding
     * @param resolver the layer's per-system rule, opened over that same pass
     * @return every system the rule gives somebody, with the fill state of each that does not fill
     *         solid
     */
    public static HolderResolution collectOver(HolderPass pass, SystemHolderResolver resolver) {

        var ownerBySystemKey = new LinkedHashMap<SystemKey, SystemOwner>();
        var fillStateBySystemKey = new LinkedHashMap<SystemKey, FillState>();

        for (var system : pass.readSystems()) {

            var resolved = resolver.resolveHolderIn(system);
            if (resolved.owner() != null) {

                var systemKey = SystemKey.readKeyOf(system);

                ownerBySystemKey.put(systemKey, resolved.owner());
                fillStateBySystemKey.put(systemKey, resolved.fillState());
            }
        }

        return new HolderResolution(ownerBySystemKey, SystemFillStates.createOf(fillStateBySystemKey));
    }
}
