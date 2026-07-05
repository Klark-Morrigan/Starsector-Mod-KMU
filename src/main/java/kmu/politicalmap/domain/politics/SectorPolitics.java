package kmu.politicalmap.domain.politics;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves which faction owns each star system and in which colors that owner
 * paints.
 *
 * <p>The dominance-and-palette half of the ownership pipeline: it takes the known
 * market footprints {@link KnownMarketFootprints} reads from the economy, asks
 * {@link SystemDominance} which faction holds the system, and resolves that
 * winner's authored UI shades into a {@link DominantOwner} the render layer draws.
 * Confining the winning owner's {@code FactionAPI} palette lookup here keeps the
 * render layer clear of Starsector economy and faction types.
 */
public final class SectorPolitics {

    private SectorPolitics() {
    }

    /**
     * Builds the dominant owner - faction id and draw color - for every inhabited
     * star system.
     *
     * <p>The render-ready output of the ownership pipeline: resolving the faction
     * and its palette here confines {@code FactionAPI} access to this adapter, so
     * the render layer consumes a plain {@link DominantOwner} and never reaches
     * into the economy. Both facts come from one dominance pass, and the id is
     * kept beside the color so per-owner styling - and later per-owner behaviour -
     * reads the same winner the fill was decided by.
     *
     * <p>Reads the player's dominance-weighting rules once up front, so every
     * system in the pass resolves under the same rule even if the player applies
     * a settings change mid-walk.
     *
     * @param sector the sector whose economy is read; null yields an empty map
     * @return the dominant owner keyed by system id; a system with no owned
     *         markets is absent from the map (uninhabited)
     */
    public static Map<String, DominantOwner> resolveDominantOwnerBySystemId(SectorAPI sector) {
        return resolveDominantOwnerBySystemId(sector, DominanceWeighting.readFromSettings(),
                PoliticalMapDevOverrides.readFromSettings().isShowingAllFactions());
    }

    /**
     * Builds the dominant owner for every inhabited star system under an explicit
     * weighting rule and the normal known-to-player filter.
     *
     * @param sector    the sector whose economy is read; null yields an empty map
     * @param weighting the dominance-weighting rules for this pass
     * @return the dominant owner keyed by system id; a system with no owned
     *         markets is absent from the map (uninhabited)
     */
    public static Map<String, DominantOwner> resolveDominantOwnerBySystemId(
            SectorAPI sector, DominanceWeighting weighting) {
        return resolveDominantOwnerBySystemId(sector, weighting, false);
    }

    /**
     * Builds the dominant owner for every inhabited star system under an explicit
     * weighting rule, for a caller that has already read the player's toggles for
     * the surrounding pass.
     *
     * @param sector                       the sector whose economy is read; null yields
     *                                     an empty map
     * @param weighting                    the dominance-weighting rules for this pass -
     *                                     whether stability scales each rating and
     *                                     whether an attached station lifts it - before
     *                                     dominance is compared
     * @param shouldIncludeUndiscoveredMarkets whether undiscovered colonies count toward
     *                                     dominance (the "show all factions" dev reveal);
     *                                     false applies the normal known-to-player filter
     * @return the dominant owner keyed by system id; a system with no folded
     *         markets is absent from the map (uninhabited)
     */
    public static Map<String, DominantOwner> resolveDominantOwnerBySystemId(
            SectorAPI sector, DominanceWeighting weighting,
            boolean shouldIncludeUndiscoveredMarkets) {
        var ownerBySystemId = new LinkedHashMap<String, DominantOwner>();
        if (sector == null) {
            return ownerBySystemId;
        }

        for (var system : sector.getStarSystems()) {
            var owner = resolveDominantOwner(sector, system, weighting,
                    shouldIncludeUndiscoveredMarkets);
            if (owner != null) {
                ownerBySystemId.put(system.getId(), owner);
            }
        }
        return ownerBySystemId;
    }

    /**
     * Resolves the dominant owner of one star system - the same result the bulk
     * pass would put under this system's id, computed for it alone.
     *
     * <p>The single-system entry point the incremental refresh path leans on:
     * when one colony's size changes, only that system's ownership can shift, so
     * only it is re-derived rather than re-walking the whole economy. Shares the
     * footprint, dominance rule, and palette lookup with
     * {@link #resolveDominantOwnerBySystemId}, so a system resolves the same
     * winner and colors whether it is refreshed alone or in the full pass.
     *
     * <p>Reads the player's dominance-weighting rules live, so a single-system
     * refresh resolves under the player's current rule.
     *
     * @param sector the sector whose economy is read; null (or a null economy)
     *               yields null
     * @param system the system to resolve; null yields null
     * @return the dominant owner, or null when the system holds no owned market
     *         (uninhabited)
     */
    public static DominantOwner resolveDominantOwner(SectorAPI sector, StarSystemAPI system) {
        return resolveDominantOwner(sector, system, DominanceWeighting.readFromSettings(),
                PoliticalMapDevOverrides.readFromSettings().isShowingAllFactions());
    }

    /**
     * Resolves the dominant owner of one star system under an explicit weighting
     * rule and the normal known-to-player filter.
     *
     * @param sector    the sector whose economy is read; null (or a null economy)
     *                  yields null
     * @param system    the system to resolve; null yields null
     * @param weighting the dominance-weighting rules for this pass
     * @return the dominant owner, or null when the system holds no owned market
     *         (uninhabited)
     */
    public static DominantOwner resolveDominantOwner(SectorAPI sector, StarSystemAPI system,
            DominanceWeighting weighting) {
        return resolveDominantOwner(sector, system, weighting, false);
    }

    /**
     * Resolves the dominant owner of one star system under an explicit weighting
     * rule, for a caller that has already read the player's toggles for the
     * surrounding pass.
     *
     * @param sector                       the sector whose economy is read; null (or a
     *                                     null economy) yields null
     * @param system                       the system to resolve; null yields null
     * @param weighting                    the dominance-weighting rules for this pass -
     *                                     whether stability scales each rating and
     *                                     whether an attached station lifts it - before
     *                                     dominance is compared
     * @param shouldIncludeUndiscoveredMarkets whether undiscovered colonies count toward
     *                                     dominance (the "show all factions" dev reveal);
     *                                     false applies the normal known-to-player filter
     * @return the dominant owner, or null when the system holds no folded market
     *         (uninhabited)
     */
    public static DominantOwner resolveDominantOwner(SectorAPI sector, StarSystemAPI system,
            DominanceWeighting weighting, boolean shouldIncludeUndiscoveredMarkets) {
        if (sector == null || system == null || sector.getEconomy() == null) {
            return null;
        }
        var footprintByFactionId = KnownMarketFootprints.readByFaction(sector, system,
                weighting, shouldIncludeUndiscoveredMarkets);
        var dominantFactionId = SystemDominance.resolveDominantFactionId(footprintByFactionId);
        if (dominantFactionId == null) {
            return null;
        }
        var faction = sector.getFaction(dominantFactionId);
        if (faction == null) {
            return null;
        }
        // The two palette slots are the faction's own authored UI shades: the
        // bright color as primary and the dark color as secondary. Each .faction
        // file specifies both directly, so a map element pointed at either stays
        // true to the faction palette. Which element uses which is the player's
        // choice, made downstream in the render layer.
        return new DominantOwner(dominantFactionId,
                faction.getBrightUIColor(), faction.getDarkUIColor());
    }
}
