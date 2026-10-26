package kmu.maplayers.politicalmap.dominance;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.politicalmap.dominance.weighting.KnownMarketFootprints;
import kmu.maplayers.politicalmap.dominance.weighting.MarketFootprint;

import java.util.Map;

/**
 * Resolves which faction owns each star system and in which colours that holder
 * paints.
 *
 * <p>The dominance half of the holder pipeline: it takes the footprints
 * {@link KnownMarketFootprints} weighs out of the pass's colony set, asks
 * {@link SystemDominance} which faction holds the system, and hands that winner to
 * the pass's own palettes ({@code SectorBlocPalettes}), which resolve it into the
 * {@link SystemOwner} the render layer draws. Colouring through the pass rather than
 * looking the faction up here colours every system a bloc holds off the one snapshot
 * its fills were resolved from.
 *
 * <p>Resolves one render holder per system; the picker's whole-sector bloc totals are
 * {@link DominanceStatsAggregator}'s job, walking the same economy through the same
 * {@link DominancePass} so the two never drift on which blocs hold territory.
 *
 * <p>The weighting rule, colony rule, and grouping a pass resolves under travel together as a
 * {@link DominancePass}, which the caller opens once and hands down, so every system a rebuild
 * asks about resolves under one consistent set of knobs.
 */
public final class SectorPolitics {

    private SectorPolitics() {
    }

    /**
     * Resolves the dominant holder of one star system under an explicit dominance pass.
     *
     * <p>The core of the holder pipeline: it reads each bloc's footprint under the
     * pass's grouping (a no-op fold under the identity grouping, a member-summing merge
     * under an alliance grouping), ranks the blocs, then colours the winning bloc through
     * the faction the grouping names for its palette. Under identity the bloc ID is the
     * faction ID and its colour faction is itself, so the result is the plain faction holder.
     *
     * @param system the system to resolve; null yields null
     * @param pass   the weighting rule, colony rule, grouping, and sector walk this pass resolves
     *               under; a pass over no sector (or one whose sector has no economy) yields null
     * @return the dominant holder, or null when the system holds no folded market
     *         (uninhabited)
     */
    public static SystemOwner resolveDominantHolder(
            StarSystemAPI system,
            DominancePass pass) {

        if (system == null || !pass.canReadEconomy()) {
            return null;
        }
        return resolveDominantHolder(system, pass, pass.readBlocFootprints(system));
    }

    // The holder half of the resolve above, over footprints the caller already read off the same
    // pass - so the filter, which needs those footprints for its presence classification too,
    // recedes a system to exactly the holder this resolve paints rather than restating the rule.
    static SystemOwner resolveDominantHolder(
            StarSystemAPI system,
            DominancePass pass,
            Map<String, MarketFootprint> footprintByBlocId) {

        var dominantBlocId = pass.resolveDominantBlocId(system, footprintByBlocId);

        if (dominantBlocId == null) {
            return null;
        }
        return pass.palettes().resolveOwnerOf(dominantBlocId);
    }
}
