package kmu.maplayers.politicalmap.base.politics;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.dominance.KnownMarketFootprints;
import kmu.maplayers.politicalmap.base.dominance.SystemDominance;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves which faction owns each star system and in which colours that holder
 * paints.
 *
 * <p>The dominance-and-palette half of the holder pipeline: it takes the footprints
 * {@link KnownMarketFootprints} weighs out of the pass's colony set, asks
 * {@link SystemDominance} which faction holds the system, and resolves that
 * winner's authored UI shades into a {@link DominantHolder} the render layer draws.
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
     * @return the dominant holder keyed by system id; a system with no owned
     *         markets is absent from the map (uninhabited)
     */
    public static Map<String, DominantHolder> resolveDominantHolderBySystemId(HolderPass pass) {
        return resolveDominantHolderBySystemId(
            DominancePass.readRulesFromLunaSettings(pass));
    }

    /**
     * Builds the dominant holder for every inhabited star system under an explicit
     * dominance pass, for a caller that has already sampled the player's settings.
     *
     * @param pass the weighting rule, colony rule, grouping, and sector walk this pass resolves under
     * @return the dominant holder keyed by system id; a system with no folded
     *         markets is absent from the map (uninhabited)
     */
    public static Map<String, DominantHolder> resolveDominantHolderBySystemId(DominancePass pass) {

        var ownerBySystemId = new LinkedHashMap<String, DominantHolder>();

        for (var system : pass.readSystems()) {
            var holder = resolveDominantHolder(system, pass);
            if (holder != null) {
                ownerBySystemId.put(system.getId(), holder);
            }
        }
        return ownerBySystemId;
    }

    /**
     * Resolves the dominant holder of one star system under an explicit grouping and
     * the player's live settings.
     *
     * <p>The view-aware single-system entry point: the incremental refresh path
     * hands in the active view's grouping so a re-derived system resolves the same
     * winning bloc the bulk pass would under that view, reading the dominance rule
     * and colony rule live like the parameterless entry.
     *
     * @param sector   the sector whose economy is read; null (or a null economy)
     *                 yields null
     * @param system   the system to resolve; null yields null
     * @param grouping the holder grouping that collapses factions into blocs for
     *                 this pass
     * @return the dominant holder, or null when the system holds no owned market
     *         (uninhabited)
     */
    public static DominantHolder resolveDominantHolder(
            SectorAPI sector,
            StarSystemAPI system,
            HolderGrouping grouping) {
        return resolveDominantHolder(
            system,
            DominancePass.readFromLunaSettings(sector, grouping));
    }

    /**
     * Resolves the dominant holder of one star system under an explicit dominance pass.
     *
     * <p>The core of the holder pipeline: it reads each bloc's footprint under the
     * pass's grouping (a no-op fold under the identity grouping, a member-summing merge
     * under an alliance grouping), ranks the blocs, then colours the winning bloc through
     * the faction the grouping names for its palette. Under identity the bloc id is the
     * faction id and its colour faction is itself, so the result is the plain faction holder.
     *
     * @param system the system to resolve; null yields null
     * @param pass   the weighting rule, colony rule, grouping, and sector walk this pass resolves
     *               under; a pass over no sector (or one whose sector has no economy) yields null
     * @return the dominant holder, or null when the system holds no folded market
     *         (uninhabited)
     */
    public static DominantHolder resolveDominantHolder(
            StarSystemAPI system,
            DominancePass pass) {

        if (system == null || !pass.canReadEconomy()) {
            return null;
        }
        var footprintByBlocId = pass.readBlocFootprints(system);
        var dominantBlocId = SystemDominance.resolveDominantFactionId(
            footprintByBlocId,
            pass.tieBreakFor(system));

        if (dominantBlocId == null) {
            return null;
        }
        return resolveBlocHolder(
            pass.sector(),
            pass.grouping(),
            dominantBlocId);
    }

    /**
     * Colours a bloc into a render-ready {@link DominantHolder}: the bloc's id paired with
     * the two shades it paints in.
     *
     * <p>The bloc paints in a real faction's palette - itself for a lone faction bloc, the
     * alliance's dominant member for an alliance bloc - so resolving the colour faction here
     * keeps the bloc id, which for an alliance is not a faction id, out of the
     * {@code FactionAPI} lookup. The two palette slots are that faction's own authored UI
     * shades: the bright colour as primary and the dark colour as secondary, each specified
     * directly in the {@code .faction} file, so a map element pointed at either stays true to
     * the palette; which element uses which is the player's choice, made downstream in the
     * render layer.
     *
     * <p>Shared with the filter's presence resolver ({@link FilteredPolitics}), which reuses
     * the selected bloc's palette under its own synthetic key. Returns null when the colour
     * faction does not resolve, which drops the system as unowned.
     *
     * @param sector   the sector whose {@code FactionAPI} palette is read
     * @param grouping the grouping that names the bloc's colour faction
     * @param blocId   the bloc to colour, carried on the returned holder as its id
     * @return the render-ready holder, or null when the colour faction does not resolve
     */
    static DominantHolder resolveBlocHolder(
            SectorAPI sector,
            HolderGrouping grouping,
            String blocId) {
                
        var faction = sector.getFaction(grouping.resolveColourFactionId(blocId));
        if (faction == null) {
            return null;
        }
        return new DominantHolder(blocId, faction.getBrightUIColor(), faction.getDarkUIColor());
    }
}
