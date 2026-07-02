package kmu.politicalmap.domain.politics;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads one star system's known owned markets from the live economy into the
 * per-faction footprints the dominance rule compares.
 *
 * <p>The economy / {@code MarketAPI} half of the ownership pipeline: it applies
 * the "counts as a colony" filter - a market is in only when a faction owns it,
 * it is not a bare planet's condition-only placeholder, and the player knows it
 * exists - and sizes each surviving market for dominance. Confining the
 * {@code MarketAPI} access here lets both the owner-resolution pipeline and the
 * map's inhabitation test share one definition of a known colony. The pure
 * comparison of the footprints it produces is {@link SystemDominance}'s job;
 * turning the winner into draw colors is {@link SectorPolitics}'s.
 */
public final class KnownMarketFootprints {
    // A hidden market (vanilla concealed bases like the Galatia Academy) still
    // marks its system on the political map, but folds into dominance at this
    // fixed token size rather than its real size, so a concealed outpost can
    // flag presence without ever outweighing an openly held colony.
    private static final int HIDDEN_MARKET_DOMINANCE_SIZE = 1;

    private KnownMarketFootprints() {
    }

    /**
     * Folds each faction's known markets in one system into the footprint the
     * dominance rule compares.
     *
     * <p>Condition-only markets (the placeholder market every uninhabited planet
     * carries for hazard and atmosphere conditions) are skipped: they are not a
     * colony, so they confer no ownership. Decivilised colonies are already absent
     * - vanilla drops them from the economy - so they need no extra guard here.
     *
     * @param sector the sector whose economy is read; assumed non-null with a
     *               non-null economy, which the callers guard before delegating
     * @param system the system whose markets are folded
     * @return each faction's footprint in the system, keyed by faction id; empty
     *         when the system holds no known owned market
     */
    public static Map<String, FactionFootprint> readByFaction(
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

    /**
     * Whether the player knows of at least one owned, non-condition-only market in
     * the system - its faction-presence test, used to admit the system to the map
     * as inhabited. Shares the known-market and condition-only filters
     * {@link #readByFaction} applies, so "counts as a colony" means one thing: a
     * market is known once its entity is discovered or the market has been un-hidden.
     *
     * @param sector the sector to read; null (or a null economy) yields false
     * @param system the system to test; null yields false
     * @return true when a known faction colony exists in the system
     */
    public static boolean hasKnownOwnedMarket(SectorAPI sector, StarSystemAPI system) {
        if (sector == null || system == null || sector.getEconomy() == null) {
            return false;
        }
        return !readByFaction(sector, system).isEmpty();
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
