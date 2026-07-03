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
 * exists - and weighs each surviving market for dominance, scaling its size
 * rating by stability when the caller asks for it (the player-facing LunaLib
 * toggle, read upstream so this class stays free of settings access). Confining
 * the {@code MarketAPI} access here lets both the owner-resolution pipeline and
 * the map's inhabitation test share one definition of a known colony. The pure
 * comparison of the footprints it produces is {@link SystemDominance}'s job;
 * turning the winner into draw colors is {@link SectorPolitics}'s.
 */
public final class KnownMarketFootprints {

    /**
     * The fixed-point grid dominance weights live on: one market size point at
     * full stability contributes this many weight units. Rounding each market's
     * stability-scaled worth onto an integer grid keeps the dominance rule's
     * comparisons exact - and its faction-id backstop deterministic - where
     * fractional weights would force epsilon math into the rule.
     */
    public static final int DOMINANCE_WEIGHT_SCALE = 1000;

    // A hidden market (vanilla concealed bases like the Galatia Academy) still
    // marks its system on the political map, but folds into dominance at this
    // fixed token size rating rather than its real size, so a concealed outpost
    // can flag presence without ever outweighing an openly held colony.
    private static final int HIDDEN_MARKET_DOMINANCE_SIZE = 1;

    // Stability's vanilla 0..10 band, the denominator of the per-market weight
    // fraction. Values are clamped into the band on read: a modded market can
    // sit outside it, and an out-of-band value must scale as "none" or "full",
    // never flip a comparison or overshoot the market's own size.
    private static final float MAX_STABILITY_VALUE = 10.0f;

    // The presence test asks only whether a footprint exists, and the stability
    // weighting never adds or removes entries - a weightless colony still folds
    // in at 0 - so the presence read skips the weighting and stays independent
    // of the player's toggle.
    private static final boolean PRESENCE_READ_IS_STABILITY_WEIGHTED = false;

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
     * @param sector             the sector whose economy is read; assumed non-null
     *                           with a non-null economy, which the callers guard
     *                           before delegating
     * @param system             the system whose markets are folded
     * @param isStabilityWeighted whether each market's size rating is scaled by
     *                           its stability; false folds every market in at its
     *                           full rating. The player's LunaLib toggle, read
     *                           once per pass by the caller so a whole pass
     *                           resolves under one rule
     * @return each faction's footprint in the system, keyed by faction id; empty
     *         when the system holds no known owned market
     */
    public static Map<String, FactionFootprint> readByFaction(
            SectorAPI sector, StarSystemAPI system, boolean isStabilityWeighted) {
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
            var footprint = footprintByFactionId.getOrDefault(factionId, FactionFootprint.EMPTY);
            footprintByFactionId.put(factionId, footprint.addMarket(
                    computeDominanceWeight(market, isStabilityWeighted), isPlanetMarket));
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
        return !readByFaction(sector, system, PRESENCE_READ_IS_STABILITY_WEIGHTED).isEmpty();
    }

    // A market's worth to the dominance rule: its size rating scaled linearly by
    // stability, rounded onto the fixed-point grid. The size rating is the raw
    // getSize(), or the fixed token for a hidden market - stability scales the
    // token like any other rating. Stability decides how much of the rating the
    // faction actually holds - a colony at 0 stability is worth nothing to
    // dominance (it still marks presence and paints its system when unopposed),
    // at 5 half its size, at 10 the full size - so a destabilised colony holds
    // less of its system than a functioning colony of equal size. With the
    // weighting toggled off, every market is worth its full rating.
    private static int computeDominanceWeight(MarketAPI market, boolean isStabilityWeighted) {
        var dominanceSize = market.isHidden()
                ? HIDDEN_MARKET_DOMINANCE_SIZE
                : market.getSize();
        if (!isStabilityWeighted) {
            return dominanceSize * DOMINANCE_WEIGHT_SCALE;
        }
        var stabilityFraction = market.getStabilityValue() / MAX_STABILITY_VALUE;
        var clampedFraction = Math.min(Math.max(stabilityFraction, 0.0f), 1.0f);
        return Math.round(dominanceSize * clampedFraction * DOMINANCE_WEIGHT_SCALE);
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
