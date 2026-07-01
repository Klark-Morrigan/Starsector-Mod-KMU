package kmu.politicalmap.domain;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads the live economy to decide which faction owns each star system, and in
 * which colors that owner paints.
 *
 * <p>This is the Starsector-facing half of the ownership pipeline: it walks the
 * sector's markets (the only authority on who holds what), sums each faction's
 * footprint per system, and hands that to {@link SystemDominance}, which owns
 * the rule. Splitting the economy read from the rule keeps the rule pure and
 * confines the concrete {@code MarketAPI} / {@code FactionAPI} access to one
 * place, including the faction-palette lookups that resolve the draw colors.
 */
public final class SectorPolitics {
    // Fallback when the sector has no "neutral" faction (it always does in
    // vanilla); a mid grey so an uninhabited outline still reads as unowned.
    private static final Color NEUTRAL_FALLBACK_COLOR = Color.GRAY;

    // A hidden market (vanilla concealed bases like the Galatia Academy) still
    // marks its system on the political map, but folds into dominance at this
    // fixed token size rather than its real size, so a concealed outpost can
    // flag presence without ever outweighing an openly held colony.
    private static final int HIDDEN_MARKET_DOMINANCE_SIZE = 1;

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
            var footprintByFactionId = buildKnownFootprintByFaction(sector, system);
            var dominantFactionId = SystemDominance.resolveDominantFactionId(footprintByFactionId);
            if (dominantFactionId == null) {
                continue;
            }
            var faction = sector.getFaction(dominantFactionId);
            if (faction == null) {
                continue;
            }
            // The two palette slots are the faction's own authored UI shades: the
            // bright color as primary and the dark color as secondary. Each
            // .faction file specifies both directly, so a map element pointed at
            // either stays true to the faction palette rather than a mechanical
            // darkening. Which element uses which is the player's choice, made
            // downstream in the render layer.
            ownerBySystemId.put(system.getId(),
                    new DominantOwner(dominantFactionId,
                            faction.getBrightUIColor(), faction.getDarkUIColor()));
        }
        return ownerBySystemId;
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

    /**
     * Whether the player knows of at least one owned, non-condition-only market
     * in the system - its faction-presence test, used to admit the system to the
     * map as inhabited. Shares the known-market and condition-only filters the
     * color pipeline uses, so "counts as a colony" means one thing: a market is
     * known once its entity is discovered or the market has been un-hidden.
     *
     * @param sector the sector to read; null yields false
     * @param system the system to test; null yields false
     * @return true when a known faction colony exists in the system
     */
    public static boolean hasDiscoveredOwnedMarket(SectorAPI sector, StarSystemAPI system) {
        if (sector == null || system == null || sector.getEconomy() == null) {
            return false;
        }
        return !buildKnownFootprintByFaction(sector, system).isEmpty();
    }

    // Folds each faction's known markets in one system into the footprint the
    // dominance rule compares. Condition-only markets (the placeholder
    // market every uninhabited planet carries for hazard and atmosphere
    // conditions) are skipped: they are not a colony, so they confer no
    // ownership. Decivilised colonies are already absent - vanilla drops them
    // from the economy - so they need no extra guard here.
    private static Map<String, FactionFootprint> buildKnownFootprintByFaction(
            SectorAPI sector, StarSystemAPI system) {
        var footprintByFactionId = new LinkedHashMap<String, FactionFootprint>();
        for (var market : sector.getEconomy().getMarkets(system)) {
            var faction = market.getFaction();
            if (market.isPlanetConditionMarketOnly() || faction == null) {
                continue;
            }
            if (!isMarketKnownToPlayer(market)) {
                continue;
            }
            var factionId = faction.getId();
            // getPlanetEntity() is non-null for a market on a planet and null
            // for one on a station; the rule prefers planets at an exact tie.
            var isPlanetMarket = market.getPlanetEntity() != null;
            // A hidden market counts as a token presence rather than its real
            // size, so it can mark the system without skewing the dominance.
            var dominanceSize = market.isHidden()
                    ? HIDDEN_MARKET_DOMINANCE_SIZE
                    : market.getSize();
            var footprint = footprintByFactionId.getOrDefault(factionId, FactionFootprint.EMPTY);
            footprintByFactionId.put(factionId,
                    footprint.addMarket(dominanceSize, isPlanetMarket));
        }
        return footprintByFactionId;
    }

    // Whether the player knows this market exists - the gate on what counts as
    // political presence. Known means the entity has been discovered (no longer
    // flagged discoverable) OR the market has been un-hidden, surfaced into the
    // open by a story reveal. Neither arm cares about the owner or whether the
    // faction is in the intel directory: a faction hidden from the directory is
    // not barred, and a Galatia-Academy-style base (hidden market on an
    // always-visible entity) still paints via the discovery arm, folding into
    // dominance at a token size.
    //
    // The un-hidden arm catches a colony surfaced ahead of its entity being
    // physically found: FSF Military Corporation's DWR43 colonies un-hide on the
    // player's first entry yet stay setDiscoverable(true) until the fleet closes
    // to sensor range. They are public knowledge in that window - listed on the
    // star's map tooltip - so they paint their system at once rather than waiting
    // on the approach. A still-concealed station (a hidden market on a
    // discoverable entity, e.g. Knights of Ludd's Battlestar Libra, or DWR43
    // before its reveal) fails both arms and never paints until found.
    private static boolean isMarketKnownToPlayer(MarketAPI market) {
        var entity = market.getPrimaryEntity();
        var isEntityDiscovered = entity == null || !entity.isDiscoverable();
        return isEntityDiscovered || !market.isHidden();
    }
}
