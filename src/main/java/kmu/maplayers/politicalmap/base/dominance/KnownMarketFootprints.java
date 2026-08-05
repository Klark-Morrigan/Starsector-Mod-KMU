package kmu.maplayers.politicalmap.base.dominance;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.markets.Markets;
import kmlib.starsector.markets.PatrolCounts;

import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.settings.HiddenMarketScalingChoice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads one star system's known owned markets from the live economy into the
 * per-faction footprints the dominance rule compares.
 *
 * <p>The economy / {@code MarketAPI} half of the holder pipeline: it applies
 * the "counts as a colony" filter - a market is in only when a faction owns it,
 * it is not a bare planet's condition-only placeholder, and the player knows it
 * exists - and weighs each surviving market for dominance. A market's weight is the
 * sum of three factors - its weighted base size (a hidden market counting by its
 * real size or a fixed token), any attached-station bonus, and the patrol strength
 * of a colony a functional patrol HQ garrisons (the {@code $patrol} gate) -
 * each cut for low stability by its own penalty (the player-facing LunaLib settings,
 * read upstream so this class stays free of settings access). The
 * "counts as a colony" filter itself is {@link kmlib.starsector.markets.Markets},
 * so the map's inhabitation read (a plain presence test) and this weighted
 * dominance read share one definition of a known colony. The pure comparison of
 * the footprints it produces is {@link SystemDominance}'s job; turning the winner
 * into draw colours is a later, separate step.
 *
 * <p>Every weight is worked out once, as a {@link MarketWeightBreakdown} the scalar
 * weight is then summed over. A caller that wants the number reads the footprint;
 * one that has to explain the number reads {@link #readBreakdownByFaction} for the
 * same markets' parts. Neither can show a total the other's arithmetic disagrees
 * with, because there is only the one arithmetic.
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

    // The full-worth stability fraction a factor stands at when stability cannot cost it
    // anything: the player has turned stability weighting off (the master toggle), or the
    // market has no worth for a penalty to cut in the first place.
    private static final double UNWEIGHTED_STABILITY_FRACTION = 1.0;

    // What low stability takes off a factor it cannot touch.
    private static final double NO_STABILITY_PENALTY = 0.0;

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
            SectorAPI sector,
            StarSystemAPI system,
            DominanceRules rules) {
        return readByFaction(
            sector,
            system,
            rules,
            false); // Undiscovered markets are not included.
    }

    /**
     * Folds each faction's markets in one system into the footprint the dominance
     * rule compares.
     *
     * <p>Condition-only markets (the placeholder market every uninhabited planet
     * carries for hazard and atmosphere conditions) are skipped: they are not a
     * colony, so they hold nothing. Decivilised colonies are already absent
     * - vanilla drops them from the economy - so they need no extra guard here.
     *
     * @param sector                           the sector whose economy is read; assumed
     *                                         non-null with a non-null economy, which the
     *                                         callers guard before delegating
     * @param system                           the system whose markets are folded
     * @param rules                            the dominance-weighting rules for this pass -
     *                                         whether stability scales each rating and
     *                                         whether an attached station lifts it. The
     *                                         player's LunaLib toggles, read once per pass
     *                                         by the caller so a whole pass resolves under
     *                                         one rule
     * @param shouldIncludeUndiscoveredMarkets whether a market the player has not yet
     *                                         discovered still folds in (the "show all
     *                                         factions" dev reveal); false applies the normal
     *                                         known-to-player filter, true drops it so an
     *                                         undiscovered colony counts too
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
                    sector,
                    system,
                    rules,
                    shouldIncludeUndiscoveredMarkets)
                .entrySet()) {

            footprintByFactionId.put(
                entry.getKey(),
                entry.getValue().footprint());
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
     * @param sector                           the sector whose economy is read; assumed non-null with a
     *                                         non-null economy, which the callers guard before delegating
     * @param system                           the system whose markets are folded
     * @param rules                            the dominance-weighting rules for this pass, read once per
     *                                         pass by the caller so a whole pass resolves under one rule
     * @param shouldIncludeUndiscoveredMarkets whether a market the player has not yet discovered still
     *                                         folds in (the "show all factions" dev reveal); false applies
     *                                         the normal known-to-player filter
     * @return each faction's contribution in the system, keyed by faction id; empty when the system
     *         holds no folded market
     */
    public static Map<String, FactionMarketContribution> readContributionsByFaction(
            SectorAPI sector,
            StarSystemAPI system,
            DominanceRules rules,
            boolean shouldIncludeUndiscoveredMarkets) {

        var contributionByFactionId = new LinkedHashMap<String, FactionMarketContribution>();
        for (var market : readCountedColonies(sector, system, shouldIncludeUndiscoveredMarkets)) {
            var factionId = market.getFaction().getId();

            // getPlanetEntity() is non-null for a market on a planet and null
            // for one on a station; the rule prefers planets at an exact tie.
            var isPlanetMarket = market.getPlanetEntity() != null;
            var contribution = contributionByFactionId.getOrDefault(
                factionId,
                FactionMarketContribution.EMPTY);

            contributionByFactionId.put(
                factionId,
                contribution.addMarket(
                    readBreakdown(market, rules).computeTotalWeight(),
                    isPlanetMarket,
                    market.getSize()));
        }
        return contributionByFactionId;
    }

    /**
     * Reads each faction's markets in one system as the arithmetic behind their dominance
     * weights, rather than as the weights alone.
     *
     * <p>The explaining half of {@link #readContributionsByFaction}: the same market walk under
     * the same "counts as a colony" filter and the same per-market weight read, kept whole
     * instead of summed away. What paints the map reads the totals; what has to justify a
     * painted system to the player reads the parts those totals are made of.
     *
     * @param sector                           the sector whose economy is read; assumed non-null
     *                                         with a non-null economy, which the callers guard
     *                                         before delegating
     * @param system                           the system whose markets are read
     * @param rules                            the dominance-weighting rules for this pass, read
     *                                         once per pass by the caller so a whole pass resolves
     *                                         under one rule
     * @param shouldIncludeUndiscoveredMarkets whether a market the player has not yet discovered
     *                                         still counts (the "show all factions" dev reveal);
     *                                         false applies the normal known-to-player filter
     * @return each faction's counted markets in the system with the breakdown of each market's
     *         weight, keyed by faction id and in the economy's own market order; empty when the
     *         system holds no counted market
     */
    public static Map<String, List<MarketWeightBreakdown>> readBreakdownByFaction(
            SectorAPI sector,
            StarSystemAPI system,
            DominanceRules rules,
            boolean shouldIncludeUndiscoveredMarkets) {

        var breakdownsByFactionId = new LinkedHashMap<String, List<MarketWeightBreakdown>>();
        for (var market : readCountedColonies(sector, system, shouldIncludeUndiscoveredMarkets)) {
            breakdownsByFactionId
                .computeIfAbsent(market.getFaction().getId(), factionId -> new ArrayList<>())
                .add(readBreakdown(market, rules));
        }
        return breakdownsByFactionId;
    }

    // The system's markets that count, in the economy's own order. One definition of
    // "which markets are in play here" for both reads above, so the totals and the parts
    // can never be folded from different sets of markets.
    private static List<MarketAPI> readCountedColonies(
            SectorAPI sector,
            StarSystemAPI system,
            boolean shouldIncludeUndiscoveredMarkets) {

        var colonies = new ArrayList<MarketAPI>();
        for (var market : sector.getEconomy().getMarkets(system)) {
            if (Markets.isCountedAsColony(market, shouldIncludeUndiscoveredMarkets)) {
                colonies.add(market);
            }
        }
        return colonies;
    }

    // A market's worth to the dominance rule, factor by factor: its weighted base size,
    // any station bonus, and any patrol strength, each cut for low stability by its own
    // penalty. The base rating is the raw getSize(), or - for a hidden market - its real
    // size or the fixed weight per the hidden-market scaling; the colony-size weight
    // multiplies that base so the player can dial how much raw size counts. The station
    // and patrol bonuses add their own size points, read below.
    // Stability then decides how much of each factor the faction actually holds: with
    // the master weighting off every factor keeps its full worth, and with it on each
    // factor loses penalty * (1 - stabilityFraction) of it, so a factor with a penalty
    // of 1 is worth nothing at 0 stability, half at 5, and its full amount at 10, while
    // a factor with a smaller penalty keeps more of its worth as the colony destabilises.
    // A colony that comes out to nothing on every factor still marks presence and paints
    // its system when unopposed.
    private static MarketWeightBreakdown readBreakdown(MarketAPI market, DominanceRules rules) {

        var sizeRating = computeBaseSize(market, rules);
        var weightedBaseSize = sizeRating * rules.baseSize().colonySizeWeight();
        var station = findWeighedStation(market, rules);
        var stationBonus = station.isPresent() ? computeStationBonus(market, rules) : 0.0;
        var patrolCounts = readWeighedPatrolCounts(market, rules);
        var patrolStrength = patrolCounts
            .map(counts -> computePatrolStrength(counts, rules))
            .orElse(0.0);

        // A market whose three factors are all zero is worth zero at any stability, so the
        // stability read is skipped: there is nothing left for a penalty to cut, and the
        // market still folds into the footprint at zero weight like any weightless colony.
        var hasWorthToCut = weightedBaseSize > 0.0 || stationBonus > 0.0 || patrolStrength > 0.0;
        // Binding the pass rules and this market's stability once leaves each factor
        // stating only what differs between them: its raw worth and its own penalty.
        var stabilityScaling = new StabilityScaling(
            rules,
            hasWorthToCut
                ? Markets.getStabilityFraction(market)
                : UNWEIGHTED_STABILITY_FRACTION);

        return new MarketWeightBreakdown(
            market.getName(),
            market.isHidden(),
            buildBaseSizeFactor(market, sizeRating, weightedBaseSize, rules, stabilityScaling),
            station.map(entity ->
                buildStationFactor(market, entity, stationBonus, rules, stabilityScaling)),
            patrolCounts.map(counts -> buildPatrolFactor(counts, rules, stabilityScaling)));
    }

    // The base-size factor as the breakdown states it: the colony's own size beside the
    // rating that actually entered the weight, and what that rating was worth once the
    // stability cut had been taken.
    private static BaseSizeFactor buildBaseSizeFactor(
            MarketAPI market,
            double sizeRating,
            double weightedBaseSize,
            DominanceRules rules,
            StabilityScaling stabilityScaling) {

        var penalty = rules.baseSize().lowStabilityPenalty();
        return new BaseSizeFactor(
            market.getSize(),
            sizeRating,
            stabilityScaling.scaleFactor(weightedBaseSize, penalty),
            stabilityScaling.computePenaltyFraction(penalty));
    }

    // The station factor as the breakdown states it, naming the station that earned it.
    // The hidden-market rate is restated as the share it removes rather than the share it
    // keeps, so both of the factor's cuts read the same way round.
    private static StationFactor buildStationFactor(
            MarketAPI market,
            SectorEntityToken station,
            double stationBonus,
            DominanceRules rules,
            StabilityScaling stabilityScaling) {

        var penalty = rules.station().lowStabilityPenalty();
        return new StationFactor(
            station.getName(),
            rules.station().weight(),
            market.isHidden() ? 1.0 - rules.station().hiddenMarketRate() : 0.0,
            stabilityScaling.computePenaltyFraction(penalty),
            stabilityScaling.scaleFactor(stationBonus, penalty));
    }

    // The patrol factor as the breakdown states it: each tier scaled on its own, so the
    // tiers add up to the garrison's worth rather than being derived back out of it.
    private static PatrolFactor buildPatrolFactor(
            PatrolCounts patrols,
            DominanceRules rules,
            StabilityScaling stabilityScaling) {

        var penalty = rules.patrols().lowStabilityPenalty();
        return new PatrolFactor(
            buildPatrolTierFactor(
                patrols.small(), rules.patrols().smallWeight(), penalty, stabilityScaling),
            buildPatrolTierFactor(
                patrols.medium(), rules.patrols().mediumWeight(), penalty, stabilityScaling),
            buildPatrolTierFactor(
                patrols.large(), rules.patrols().largeWeight(), penalty, stabilityScaling),
            stabilityScaling.computePenaltyFraction(penalty));
    }

    // One patrol tier of that factor: its headcount, what one patrol of the tier is worth,
    // and what the tier folded in at.
    private static PatrolTierFactor buildPatrolTierFactor(
            int count,
            double tierWeight,
            double lowStabilityPenalty,
            StabilityScaling stabilityScaling) {

        return new PatrolTierFactor(
            count,
            tierWeight,
            stabilityScaling.scaleFactor(count * tierWeight, lowStabilityPenalty));
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

    // The market's own orbital station, when the station factor can earn anything for it:
    // nothing while the factor is toggled off or its weight is zero, so disabling the
    // factor either way skips the connected-entity station scan rather than paying for a
    // station whose bonus would be discarded.
    private static Optional<SectorEntityToken> findWeighedStation(
            MarketAPI market,
            DominanceRules rules) {

        if (!rules.station().isWeighted() || rules.station().weight() <= 0.0) {
            return Optional.empty();
        }
        return Markets.findAttachedStation(market);
    }

    // The market's patrol-tier counts, when the patrol factor can earn anything for them:
    // nothing while the factor is toggled off or no functional patrol HQ fields patrols
    // here. The $patrol gate (Markets.fieldsPatrols) confines the bonus to a colony a
    // patrol HQ actually garrisons: the raw patrol-count stats are also written by hidden
    // pirate and Luddic Path bases that set no flag, so reading the counts alone would
    // credit patrol strength vanilla would never spawn. The economy read is skipped while
    // the factor is off or the flag is absent, so neither costs a dynamic-stat lookup.
    private static Optional<PatrolCounts> readWeighedPatrolCounts(
            MarketAPI market,
            DominanceRules rules) {

        if (!rules.patrols().isWeighted() || !Markets.fieldsPatrols(market)) {
            return Optional.empty();
        }
        return Optional.of(Markets.readPatrolCounts(market));
    }

    // The station size bonus a market earns before stability scaling: the player-set
    // station weight in size points for an openly held stationed colony, or a configured
    // fraction of that weight for a hidden market, so a hidden fortress reads above a bare
    // outpost without matching an open stationed colony. Asked only of a market whose
    // station the factor has already admitted.
    private static double computeStationBonus(MarketAPI market, DominanceRules rules) {
        return market.isHidden()
            ? rules.station().weight() * rules.station().hiddenMarketRate()
            : rules.station().weight();
    }

    // The patrol size bonus a garrison earns before stability scaling: its small, medium,
    // and large counts each times the player-set weight for that tier.
    private static double computePatrolStrength(PatrolCounts patrols, DominanceRules rules) {
        return patrols.small() * rules.patrols().smallWeight()
            + patrols.medium() * rules.patrols().mediumWeight()
            + patrols.large() * rules.patrols().largeWeight();
    }

    /**
     * How far low stability cuts one market's weight factors, with the pass rules and that
     * market's stability fraction bound once for all of them.
     *
     * <p>Reading stability is per-market while the penalty is per-factor, so binding the
     * shared half here leaves each factor to supply only its own two values. The cut and the
     * size of the cut come off the one value because the breakdown states both, and a
     * penalty worked out apart from the scaling it explains would be free to disagree with
     * it.
     *
     * @param rules             the weighting rules in force for the pass
     * @param stabilityFraction how far up its 0..1 band the market's stability sits
     */
    private record StabilityScaling(DominanceRules rules, double stabilityFraction) {

        // The share of a factor's worth low stability removes: nothing while the master
        // stability weighting is off, else the factor's own penalty scaled by how far below
        // full stability the market sits - so a penalty of 1 removes everything at 0
        // stability and a penalty of 0 removes nothing at any stability.
        private double computePenaltyFraction(double lowStabilityPenalty) {
            if (!rules.isStabilityWeighted()) {
                return NO_STABILITY_PENALTY;
            }
            return lowStabilityPenalty * (1.0 - stabilityFraction);
        }

        // The share of a factor's worth in size points the market actually holds, once its
        // own penalty has taken what low stability costs it.
        private double scaleFactor(double rawFactor, double lowStabilityPenalty) {
            return rawFactor * (1.0 - computePenaltyFraction(lowStabilityPenalty));
        }
    }
}
