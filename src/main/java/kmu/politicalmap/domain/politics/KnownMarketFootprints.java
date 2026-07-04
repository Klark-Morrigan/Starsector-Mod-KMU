package kmu.politicalmap.domain.politics;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.markets.Markets;

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

    // The full-worth stability fraction the weight uses when the player has turned
    // stability weighting off: every market folds in at its whole lifted rating.
    private static final double UNWEIGHTED_STABILITY_FRACTION = 1.0;

    // The presence test asks only whether a footprint exists, and no weighting
    // factor adds or removes entries - a weightless colony still folds in at 0 -
    // so the presence read folds every market in unweighted, independent of the
    // player's toggles.
    private static final DominanceWeighting PRESENCE_READ_WEIGHTING = DominanceWeighting.UNWEIGHTED;

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
     * @param weighting          the dominance-weighting rules for this pass -
     *                           whether stability scales each rating and whether an
     *                           attached station lifts it. The player's LunaLib
     *                           toggles, read once per pass by the caller so a whole
     *                           pass resolves under one rule
     * @return each faction's footprint in the system, keyed by faction id; empty
     *         when the system holds no known owned market
     */
    public static Map<String, FactionFootprint> readByFaction(
            SectorAPI sector, StarSystemAPI system, DominanceWeighting weighting) {
        var footprintByFactionId = new LinkedHashMap<String, FactionFootprint>();
        for (var market : sector.getEconomy().getMarkets(system)) {
            var faction = market.getFaction();
            if (market.isPlanetConditionMarketOnly() || faction == null) {
                continue;
            }
            if (!Markets.isKnownToPlayer(market)) {
                continue;
            }
            var factionId = faction.getId();
            // getPlanetEntity() is non-null for a market on a planet and null
            // for one on a station; the rule prefers planets at an exact tie.
            var isPlanetMarket = market.getPlanetEntity() != null;
            var footprint = footprintByFactionId.getOrDefault(factionId, FactionFootprint.EMPTY);
            footprintByFactionId.put(factionId, footprint.addMarket(
                    computeDominanceWeight(market, weighting), isPlanetMarket));
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
        return !readByFaction(sector, system, PRESENCE_READ_WEIGHTING).isEmpty();
    }

    // A market's worth to the dominance rule: its size rating - scaled by the
    // colony-size weight, lifted by any station bonus - scaled linearly by stability,
    // rounded onto the fixed-point grid. The base rating is the raw getSize(), or the
    // fixed token for a hidden market; the colony-size weight multiplies that base (a
    // hidden base's token included) so the player can dial how much raw size counts.
    // An attached station adds the station weight in size points (a hidden base gets
    // a configured fraction of it) after the colony-size weight and before stability
    // scales the sum, so the station bonus shares in a colony's stability collapse
    // rather than sitting outside it.
    // Stability then decides how much of the lifted rating the faction actually
    // holds - a colony at 0 stability is worth nothing to dominance (it still marks
    // presence and paints its system when unopposed), at 5 half, at 10 the full
    // amount - so a destabilised colony holds less of its system than a functioning
    // one. With stability weighting off, the fraction is a flat 1, so every market
    // is worth its full lifted rating. The lifted rating rounds once onto the grid
    // so a fractional weight or hidden bonus lands cleanly and the rule stays exact.
    private static int computeDominanceWeight(MarketAPI market, DominanceWeighting weighting) {
        var baseSize = market.isHidden() ? HIDDEN_MARKET_DOMINANCE_SIZE : market.getSize();
        var weightedBaseSize = baseSize * weighting.colonySizeWeight();
        var dominanceSize = weightedBaseSize + computeStationBonus(market, weighting);
        // A rating that has already come out to nothing (both the colony-size weight
        // and the station bonus zeroed) is worth zero at any stability, so skip the
        // stability read entirely - the market still folds into the footprint at zero
        // weight, marking presence like any weightless colony.
        if (dominanceSize <= 0.0) {
            return 0;
        }
        var stabilityFraction = weighting.isStabilityWeighted()
                ? Markets.getStabilityFraction(market)
                : UNWEIGHTED_STABILITY_FRACTION;
        return (int) Math.round(dominanceSize * stabilityFraction * DOMINANCE_WEIGHT_SCALE);
    }

    // The station size bonus a market earns before stability scaling: the player-set
    // station weight in size points for an openly held stationed colony, a configured
    // fraction of that weight for a hidden base (so a concealed fortress reads above a
    // bare outpost without matching an open stationed colony), or nothing when the
    // factor is toggled off, the weight is zero, or the market has no attached station.
    // A zero weight is checked before the connected-entity station scan, so disabling
    // the factor by weight - not just by the toggle - skips that scan too.
    private static double computeStationBonus(MarketAPI market, DominanceWeighting weighting) {
        if (!weighting.isStationWeighted() || weighting.stationWeight() <= 0.0
                || !Markets.hasAttachedStation(market)) {
            return 0.0;
        }
        return market.isHidden()
                ? weighting.stationWeight() * weighting.stationHiddenMarketRate()
                : weighting.stationWeight();
    }
}
