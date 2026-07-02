package kmu.politicalmap.domain.politics;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import java.awt.Color;
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
 * Confining the {@code FactionAPI} palette lookups here - both the owner colors and
 * the neutral color for unowned space - keeps the render layer clear of Starsector
 * economy and faction types.
 */
public final class SectorPolitics {
    // Fallback when the sector has no "neutral" faction (it always does in
    // vanilla); a mid grey so an uninhabited outline still reads as unowned.
    private static final Color NEUTRAL_FALLBACK_COLOR = Color.GRAY;

    // Vanilla's unaffiliated faction. Uninhabited systems (and decivilised
    // planets) borrow its color so unowned space reads consistently.
    private static final String NEUTRAL_FACTION_ID = "neutral";

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
     * @param sector the sector whose economy is read; null yields an empty map
     * @return the dominant owner keyed by system id; a system with no owned
     *         markets is absent from the map (uninhabited)
     */
    public static Map<String, DominantOwner> resolveDominantOwnerBySystemId(SectorAPI sector) {
        var ownerBySystemId = new LinkedHashMap<String, DominantOwner>();
        if (sector == null) {
            return ownerBySystemId;
        }

        for (var system : sector.getStarSystems()) {
            var owner = resolveDominantOwner(sector, system);
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
     * @param sector the sector whose economy is read; null (or a null economy)
     *               yields null
     * @param system the system to resolve; null yields null
     * @return the dominant owner, or null when the system holds no owned market
     *         (uninhabited)
     */
    public static DominantOwner resolveDominantOwner(SectorAPI sector, StarSystemAPI system) {
        if (sector == null || system == null || sector.getEconomy() == null) {
            return null;
        }
        var footprintByFactionId = KnownMarketFootprints.readByFaction(sector, system);
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

    /**
     * Resolves the neutral color uninhabited cells are outlined in (the same
     * color decivilised markers use), so unowned space reads consistently.
     *
     * @param sector the sector to read; null falls back to a mid grey
     * @return the neutral faction's base UI color, or a grey fallback
     */
    public static Color resolveNeutralColor(SectorAPI sector) {
        if (sector == null) {
            return NEUTRAL_FALLBACK_COLOR;
        }
        var neutral = sector.getFaction(NEUTRAL_FACTION_ID);
        if (neutral == null) {
            return NEUTRAL_FALLBACK_COLOR;
        }
        return neutral.getBaseUIColor();
    }
}
