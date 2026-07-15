package kmu.maplayers.politicalmap.base.politics;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.markets.Markets;

import kmu.maplayers.politicalmap.base.politics.weighting.DominanceRules;
import kmu.settings.HiddenMarketScalingChoice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads one star system's known owned markets from the live economy into the
 * per-faction footprints the dominance rule compares.
 *
 * <p>The economy / {@code MarketAPI} half of the ownership pipeline: it applies
 * the "counts as a colony" filter - a market is in only when a faction owns it,
 * it is not a bare planet's condition-only placeholder, and the player knows it
 * exists - and weighs each surviving market for dominance. A market's weight is the
 * sum of three factors - its weighted base size (a hidden market counting by its
 * real size or a fixed token), any attached-station bonus, and any patrol strength -
 * each cut for low stability by its own penalty (the player-facing LunaLib settings,
 * read upstream so this class stays free of settings access). The
 * "counts as a colony" filter itself is {@link kmlib.starsector.markets.Markets},
 * so the map's inhabitation read (a plain presence test) and this weighted
 * dominance read share one definition of a known colony. The pure comparison of
 * the footprints it produces is {@link SystemDominance}'s job; turning the winner
 * into draw colors is {@link SectorPolitics}'s.
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

    // The full-worth stability fraction a factor uses when the player has turned
    // stability weighting off (the master toggle): every factor folds in at its
    // whole worth, untouched by stability.
    private static final double UNWEIGHTED_STABILITY_FRACTION = 1.0;

    private KnownMarketFootprints() {
    }

    /**
     * Folds each faction's known markets in one system into the footprint the
     * dominance rule compares, under the normal known-to-player filter.
     *
     * @param sector the sector whose economy is read; assumed non-null with a
     *               non-null economy, which the callers guard before delegating
     * @param system the system whose markets are folded
     * @param rules  the dominance-weighting rules for this pass
     * @return each faction's footprint in the system, keyed by faction id; empty
     *         when the system holds no known owned market
     */
    public static Map<String, MarketFootprint> readByFaction(
            SectorAPI sector, StarSystemAPI system, DominanceRules rules) {
        return readByFaction(sector, system, rules, false);
    }

    /**
     * Folds each faction's markets in one system into the footprint the dominance
     * rule compares.
     *
     * <p>Condition-only markets (the placeholder market every uninhabited planet
     * carries for hazard and atmosphere conditions) are skipped: they are not a
     * colony, so they confer no ownership. Decivilised colonies are already absent
     * - vanilla drops them from the economy - so they need no extra guard here.
     *
     * @param sector                       the sector whose economy is read; assumed
     *                                     non-null with a non-null economy, which the
     *                                     callers guard before delegating
     * @param system                       the system whose markets are folded
     * @param rules                        the dominance-weighting rules for this pass -
     *                                     whether stability scales each rating and
     *                                     whether an attached station lifts it. The
     *                                     player's LunaLib toggles, read once per pass
     *                                     by the caller so a whole pass resolves under
     *                                     one rule
     * @param shouldIncludeUndiscoveredMarkets whether a market the player has not yet
     *                                     discovered still folds in (the "show all
     *                                     factions" dev reveal); false applies the normal
     *                                     known-to-player filter, true drops it so an
     *                                     undiscovered colony counts too
     * @return each faction's footprint in the system, keyed by faction id; empty
     *         when the system holds no folded market
     */
    public static Map<String, MarketFootprint> readByFaction(
            SectorAPI sector,
            StarSystemAPI system,
            DominanceRules rules,
            boolean shouldIncludeUndiscoveredMarkets) {
        // The dominance-only projection of the fuller contribution read: a footprint-only caller
        // (the dominance resolve, the watcher's diff) drops the raw market size the picker's stats
        // need, so both share the one market walk and colony filter rather than defining a second.
        var footprintByFactionId = new LinkedHashMap<String, MarketFootprint>();
        for (var entry : readContributionsByFaction(
                sector, system, rules, shouldIncludeUndiscoveredMarkets).entrySet()) {
            footprintByFactionId.put(entry.getKey(), entry.getValue().footprint());
        }
        return footprintByFactionId;
    }

    /**
     * Folds each faction's markets in one system into its {@link FactionMarketContribution} - the
     * dominance footprint plus the raw summed colony size the picker's market-size metric reads -
     * from a single walk of the system's markets under the one "counts as a colony" filter.
     *
     * <p>The read {@link #readByFaction} projects down to when only the footprint is wanted, and the
     * picker's stats aggregation reads whole to also see raw market size. Keeping both concerns on
     * one walk means the colony filter and the weight read are defined once, not duplicated per
     * caller.
     *
     * @param sector                       the sector whose economy is read; assumed non-null with a
     *                                     non-null economy, which the callers guard before delegating
     * @param system                       the system whose markets are folded
     * @param rules                        the dominance-weighting rules for this pass, read once per
     *                                     pass by the caller so a whole pass resolves under one rule
     * @param shouldIncludeUndiscoveredMarkets whether a market the player has not yet discovered still
     *                                     folds in (the "show all factions" dev reveal); false applies
     *                                     the normal known-to-player filter
     * @return each faction's contribution in the system, keyed by faction id; empty when the system
     *         holds no folded market
     */
    static Map<String, FactionMarketContribution> readContributionsByFaction(
            SectorAPI sector,
            StarSystemAPI system,
            DominanceRules rules,
            boolean shouldIncludeUndiscoveredMarkets) {
        var contributionByFactionId = new LinkedHashMap<String, FactionMarketContribution>();
        for (var market : sector.getEconomy().getMarkets(system)) {
            if (!Markets.isOwnedColony(market)) {
                continue;
            }
            if (!shouldIncludeUndiscoveredMarkets && !Markets.isKnownToPlayer(market)) {
                continue;
            }
            var factionId = market.getFaction().getId();
            // getPlanetEntity() is non-null for a market on a planet and null
            // for one on a station; the rule prefers planets at an exact tie.
            var isPlanetMarket = market.getPlanetEntity() != null;
            var contribution = contributionByFactionId.getOrDefault(
                    factionId, FactionMarketContribution.EMPTY);
            contributionByFactionId.put(factionId, contribution.addMarket(
                    computeDominanceWeight(market, rules), isPlanetMarket, market.getSize()));
        }
        return contributionByFactionId;
    }

    // A market's worth to the dominance rule: the sum of its three weight factors -
    // its weighted base size, any station bonus, and any patrol strength - each cut
    // for low stability by its own penalty, then rounded once onto the fixed-point
    // grid. The base rating is the raw getSize(), or - for a hidden market - its real
    // size or the fixed weight per the hidden-market scaling; the colony-size weight
    // multiplies that base so the player can dial how much raw size counts. The station
    // and patrol bonuses add their own size points, read below.
    // Stability then decides how much of each factor the faction actually holds: with
    // the master weighting off every factor keeps its full worth, and with it on each
    // factor is scaled by 1 - penalty * (1 - stabilityFraction), so a factor with a
    // penalty of 1 is worth nothing at 0 stability, half at 5, and its full amount at
    // 10, while a factor with a smaller penalty keeps more of its worth as the colony
    // destabilises. A colony that comes out to nothing on every factor still marks
    // presence and paints its system when unopposed. The lifted sum rounds once onto
    // the grid so a fractional weight lands cleanly and the rule stays exact.
    private static int computeDominanceWeight(MarketAPI market, DominanceRules rules) {
        var weightedBaseSize = computeBaseSize(market, rules) * rules.baseSize().colonySizeWeight();
        var stationBonus = computeStationBonus(market, rules);
        var patrolStrength = computePatrolStrength(market, rules);
        // A market whose three factors are all zero is worth zero at any stability, so
        // skip the stability read entirely - it still folds into the footprint at zero
        // weight, marking presence like any weightless colony.
        if (weightedBaseSize <= 0.0 && stationBonus <= 0.0 && patrolStrength <= 0.0) {
            return 0;
        }
        var stabilityFraction = Markets.getStabilityFraction(market);
        var total = weightedBaseSize
                        * effectiveFactor(rules, rules.baseSize().lowStabilityPenalty(),
                                stabilityFraction)
                + stationBonus
                        * effectiveFactor(rules, rules.station().lowStabilityPenalty(),
                                stabilityFraction)
                + patrolStrength
                        * effectiveFactor(rules, rules.patrols().lowStabilityPenalty(),
                                stabilityFraction);
        return (int) Math.round(total * DOMINANCE_WEIGHT_SCALE);
    }

    // A market's base size rating before the colony-size weight: a visible colony's own
    // size, or - for a hidden market - its real size under Normal scaling or the fixed
    // weight under Fixed, so a hidden market can mark presence without its real size
    // swaying dominance when the player pins it to a token.
    private static double computeBaseSize(MarketAPI market, DominanceRules rules) {
        if (market.isHidden()
                && rules.baseSize().hiddenMarketScaling() == HiddenMarketScalingChoice.FIXED) {
            return rules.baseSize().hiddenMarketFixedWeight();
        }
        return market.getSize();
    }

    // The fraction of a factor's worth that survives stability: a flat 1 while the
    // master stability weighting is off, else 1 - penalty * (1 - stabilityFraction), so
    // a penalty of 1 collapses the factor to nothing at 0 stability and a penalty of 0
    // leaves it untouched at any stability.
    private static double effectiveFactor(
            DominanceRules rules,
            double lowStabilityPenalty,
            double stabilityFraction) {
        if (!rules.isStabilityWeighted()) {
            return UNWEIGHTED_STABILITY_FRACTION;
        }
        return 1.0 - lowStabilityPenalty * (1.0 - stabilityFraction);
    }

    // The station size bonus a market earns before stability scaling: the player-set
    // station weight in size points for an openly held stationed colony, a configured
    // fraction of that weight for a hidden market (so a hidden fortress reads above
    // a bare outpost without matching an open stationed colony), or nothing when the
    // factor is toggled off, the weight is zero, or the market has no attached station.
    // A zero weight is checked before the connected-entity station scan, so disabling
    // the factor by weight - not just by the toggle - skips that scan too.
    private static double computeStationBonus(MarketAPI market, DominanceRules rules) {
        if (!rules.station().isWeighted() || rules.station().weight() <= 0.0
                || !Markets.hasAttachedStation(market)) {
            return 0.0;
        }
        return market.isHidden()
                ? rules.station().weight() * rules.station().hiddenMarketRate()
                : rules.station().weight();
    }

    // The patrol size bonus a market earns before stability scaling: its small, medium,
    // and large patrol counts each times the player-set weight for that tier, or nothing
    // when the patrol factor is toggled off. The economy read is skipped while the factor
    // is off, so a disabled factor costs no dynamic-stat lookups.
    private static double computePatrolStrength(MarketAPI market, DominanceRules rules) {
        if (!rules.patrols().isWeighted()) {
            return 0.0;
        }
        var patrols = Markets.readPatrolCounts(market);
        return patrols.small() * rules.patrols().smallWeight()
                + patrols.medium() * rules.patrols().mediumWeight()
                + patrols.large() * rules.patrols().largeWeight();
    }
}
