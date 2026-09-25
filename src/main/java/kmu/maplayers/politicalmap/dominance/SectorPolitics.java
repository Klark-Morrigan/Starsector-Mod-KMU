package kmu.maplayers.politicalmap.dominance;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.ownermap.holding.HolderPass;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.politicalmap.dominance.weighting.KnownMarketFootprints;
import kmu.maplayers.politicalmap.dominance.weighting.MarketFootprint;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves which faction owns each star system and in which colours that holder
 * paints.
 *
 * <p>The dominance-and-palette half of the holder pipeline: it takes the footprints
 * {@link KnownMarketFootprints} weighs out of the pass's colony set, asks
 * {@link SystemDominance} which faction holds the system, and resolves that
 * winner's authored UI shades into a {@link SystemOwner} the render layer draws.
 * Confining the winning holder's {@code FactionAPI} palette lookup here keeps the
 * render layer clear of Starsector economy and faction types.
 *
 * <p>Resolves one render holder per system; the picker's whole-sector bloc totals are
 * {@link DominanceStatsAggregator}'s job, walking the same economy through the same
 * {@link DominancePass} so the two never drift on which blocs hold territory.
 *
 * <p>The weighting rule, colony rule, and grouping a pass resolves under travel together as a
 * {@link DominancePass}: the live entry points read the player's settings into one,
 * and every explicit caller hands its own down, so a whole pass resolves under one
 * consistent set of knobs.
 */
public final class SectorPolitics {

    private SectorPolitics() {
    }

    /**
     * Builds the dominant holder for every inhabited star system over a rebuild's own reading of
     * the sector, reading the weighting rule live - the entry a holding provider calls, the rule
     * being the one knob the pass it was handed does not carry.
     *
     * @param pass the rebuild's reading of the sector, whose walk of each system this resolve
     *             shares; a pass over no sector yields an empty map
     * @return the dominant holder keyed by {@link SystemKey}; a system with no owned
     *         markets is absent from the map (uninhabited)
     */
    public static Map<SystemKey, SystemOwner> resolveDominantHolderBySystemKey(HolderPass pass) {
        return resolveDominantHolderBySystemKey(
            DominancePass.readRulesFromLunaSettings(pass));
    }

    /**
     * Builds the dominant holder for every inhabited star system under an explicit
     * dominance pass, for a caller that has already sampled the player's settings.
     *
     * @param pass the weighting rule, colony rule, grouping, and sector walk this pass resolves under
     * @return the dominant holder keyed by {@link SystemKey}; a system with no folded
     *         markets is absent from the map (uninhabited)
     */
    public static Map<SystemKey, SystemOwner> resolveDominantHolderBySystemKey(
            DominancePass pass) {

        var ownerBySystemKey = new LinkedHashMap<SystemKey, SystemOwner>();

        for (var system : pass.readSystems()) {
            var holder = resolveDominantHolder(system, pass);
            if (holder != null) {
                ownerBySystemKey.put(SystemKey.readKeyOf(system), holder);
            }
        }
        return ownerBySystemKey;
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
        return SystemOwner.resolveForBloc(
            pass.sector(),
            pass.grouping(),
            dominantBlocId);
    }
}
