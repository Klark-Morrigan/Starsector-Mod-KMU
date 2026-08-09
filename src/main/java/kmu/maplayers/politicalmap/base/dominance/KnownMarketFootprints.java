package kmu.maplayers.politicalmap.base.dominance;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.markets.MarketPatrols;
import kmlib.starsector.markets.Markets;
import kmlib.starsector.markets.PatrolCounts;
import kmlib.starsector.systems.StarSystems;

import kmu.maplayers.politicalmap.base.dominance.weighting.BaseSizeWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.dominance.weighting.PatrolWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.StationWeighting;
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
 *
 * <p>Beside those two sits a third read that weighs nothing:
 * {@link #readUnweighedColoniesByFaction}, the colonies present in the system that the
 * economy does not list. They reach a caller describing the system and no caller
 * computing it, which is why they are a separate walk answering a separate type - the
 * pass sees exactly the markets it sees today.
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

    // The share of the station weight a colony held in the open earns: all of it, the
    // hidden-market rate being the reduced share a concealed base earns instead.
    private static final double FULL_STATION_RATE = 1.0;

    // What a market's factors add up to when none of them holds anything - the reading
    // below which stability has nothing left to cut.
    private static final double NO_WORTH = 0.0;

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

    /**
     * Reads each faction's colonies in one system that the economy does not list - the ones no
     * weight was ever worked out for.
     *
     * <p>Vanilla builds a colony that way on purpose, so a player can be looking at a station in a
     * faction's colours that {@link #readBreakdownByFaction} cannot see. A box accounting for the
     * system has to name it; the pass must not, every term of a dominance weight being economy-fed
     * - an unlisted market has no industries, no conditions and no computed stability, so admitting
     * one would hand its owner weight nobody worked out and could change which faction the map
     * paints the system for.
     *
     * <p>A second read over a second walk rather than a widening of the weighed one, so a caller
     * that must not see these colonies cannot be handed them by accident. What the two walks share
     * is the one thing that must not drift between them: which markets count as colonies here.
     *
     * @param sector                           the sector whose economy is read; assumed non-null
     *                                         with a non-null economy, which the callers guard
     *                                         before delegating
     * @param system                           the system whose markets are read
     * @param shouldIncludeUndiscoveredMarkets whether a market the player has not yet discovered
     *                                         still counts (the "show all factions" dev reveal);
     *                                         false applies the normal known-to-player filter
     * @return each faction's unlisted colonies in the system, keyed by faction id and in the
     *         system's own entity order; empty when every colony present is one the economy lists
     */
    public static Map<String, List<UnweighedColony>> readUnweighedColoniesByFaction(
            SectorAPI sector,
            StarSystemAPI system,
            boolean shouldIncludeUndiscoveredMarkets) {

        var coloniesByFactionId = new LinkedHashMap<String, List<UnweighedColony>>();
        for (var market : readUnweighedColonies(sector, system, shouldIncludeUndiscoveredMarkets)) {
            coloniesByFactionId
                .computeIfAbsent(market.getFaction().getId(), factionId -> new ArrayList<>())
                .add(new UnweighedColony(market.getName()));
        }
        return coloniesByFactionId;
    }

    /**
     * Rounds a worth in size points onto the dominance grid, which is the only place a
     * weight is ever expressed as an integer.
     *
     * <p>Held here rather than repeated wherever a contribution has to read as a weight,
     * because a second rounding written out by hand is free to round the other way at a
     * half unit - and a breakdown whose factor weights did not add up to the market weight
     * beside them would say the arithmetic it exists to explain is wrong.
     *
     * @param contribution the worth in size points
     * @return that worth in weight units
     */
    public static int roundToWeight(double contribution) {
        return (int) Math.round(contribution * DOMINANCE_WEIGHT_SCALE);
    }

    // The colonies the economy lists in the system, in the order it lists them. What every weighed
    // read walks, so the totals and the parts can never be folded from different sets of markets.
    private static List<MarketAPI> readCountedColonies(
            SectorAPI sector,
            StarSystemAPI system,
            boolean shouldIncludeUndiscoveredMarkets) {

        return filterCountedColonies(
            sector.getEconomy().getMarkets(system),
            shouldIncludeUndiscoveredMarkets);
    }

    // The colonies present that the economy does not list - what the walk above cannot reach, and
    // nothing it can, the widened read answering only what the economy leaves out.
    private static List<MarketAPI> readUnweighedColonies(
            SectorAPI sector,
            StarSystemAPI system,
            boolean shouldIncludeUndiscoveredMarkets) {

        return filterCountedColonies(
            StarSystems.readMarketsUnlistedByEconomy(sector, system),
            shouldIncludeUndiscoveredMarkets);
    }

    // Which of the markets handed over count as colonies here, one per place and owner. The one
    // definition, shared by both walks rather than written out beside each: what a colony is cannot
    // be allowed to differ between the set that is weighed and the set that is merely named, or a
    // colony admitted by one and refused by the other would reach the box twice or not at all.
    //
    // The per-place resolution is what stops a colony being banked twice. A mod that supersedes a
    // market by adding its own beside vanilla's rather than replacing it leaves two markets on one
    // station entity, and a weight summed over both reads their owner as holding twice what it
    // holds. Vanilla's own inhabited-systems filter never trips on this because it ORs presence
    // rather than summing weight, so the duplicate has to be resolved here instead of being
    // inherited from the walk.
    private static List<MarketAPI> filterCountedColonies(
            List<MarketAPI> markets,
            boolean shouldIncludeUndiscoveredMarkets) {

        var colonies = new ArrayList<MarketAPI>();
        for (var market : markets) {
            if (Markets.isCountedAsColony(market, shouldIncludeUndiscoveredMarkets)) {
                colonies.add(market);
            }
        }
        return Markets.readLargestMarketsPerFaction(colonies);
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
    private static MarketWeightBreakdown readBreakdown(MarketAPI market, DominanceRules rules) {

        var weighed = readWeighedMarket(market, rules);

        // What the factors are worth before stability is what decides whether stability is
        // worth reading at all: a market whose factors are worth nothing weighs nothing at
        // any stability, so this reading is already its answer. It still marks presence and
        // paints its system when unopposed, like any weightless colony.
        var atFullWorth = buildBreakdown(
            weighed,
            rules,
            new StabilityScaling(rules, UNWEIGHTED_STABILITY_FRACTION));

        if (atFullWorth.computeTotalContribution() <= NO_WORTH) {
            return atFullWorth;
        }
        return buildBreakdown(
            weighed,
            rules,
            new StabilityScaling(rules, Markets.getStabilityFraction(market)));
    }

    // Everything the weight rules read off one market, gathered before any of them applies:
    // its identity and size, plus the station and patrol tiers their factors admitted.
    // Gathered once because the market's worth is worked out twice - at full worth, then
    // under its own stability - and the connected-entity scan and the dynamic-stat lookup
    // behind the two optional factors must not be paid for twice.
    private static WeighedMarket readWeighedMarket(MarketAPI market, DominanceRules rules) {
        return new WeighedMarket(
            market.getName(),
            market.isHidden(),
            market.getSize(),
            market.getStabilityValue(),
            findWeighedStation(market, rules.station()),
            readWeighedPatrolCounts(market, rules.patrols()));
    }

    // One market's weight stated factor by factor under a given stability scaling. The same
    // three factors are built whether the market is being read at full worth or under its
    // own stability, so the two readings can differ only by what stability costs it.
    private static MarketWeightBreakdown buildBreakdown(
            WeighedMarket market,
            DominanceRules rules,
            StabilityScaling stabilityScaling) {

        return new MarketWeightBreakdown(
            market.name(),
            market.isHidden(),
            market.stability(),
            buildBaseSizeFactor(market, rules.baseSize(), stabilityScaling),
            market.station().map(station ->
                buildStationFactor(market, station, rules.station(), stabilityScaling)),
            market.patrols().map(patrols ->
                buildPatrolFactor(patrols, rules.patrols(), stabilityScaling)));
    }

    // The base-size factor as the breakdown states it: the colony's own size beside the
    // rating that actually entered the weight, and what that rating was worth once the
    // colony-size multiplier and the stability cut had their say.
    private static BaseSizeFactor buildBaseSizeFactor(
            WeighedMarket market,
            BaseSizeWeighting rules,
            StabilityScaling stabilityScaling) {

        var sizeRating = computeSizeRating(market, rules);
        return new BaseSizeFactor(
            market.size(),
            sizeRating,
            stabilityScaling.scaleFactor(
                sizeRating * rules.colonySizeWeight(),
                rules.lowStabilityPenalty()),
            stabilityScaling.computePenaltyFraction(rules.lowStabilityPenalty()));
    }

    // The station factor as the breakdown states it, naming the station that earned it: the
    // player-set station weight in size points for an openly held stationed colony, or a
    // configured fraction of that weight for a hidden market, so a hidden fortress reads
    // above a bare outpost without matching an open stationed colony. The rate that survives
    // is what the bonus is built from, and the breakdown states its complement - the share
    // the rate removes - so both of the factor's cuts read the same way round.
    private static StationFactor buildStationFactor(
            WeighedMarket market,
            SectorEntityToken station,
            StationWeighting rules,
            StabilityScaling stabilityScaling) {

        var hiddenMarketRate = market.isHidden() ? rules.hiddenMarketRate() : FULL_STATION_RATE;
        return new StationFactor(
            station.getName(),
            rules.weight(),
            1.0 - hiddenMarketRate,
            stabilityScaling.computePenaltyFraction(rules.lowStabilityPenalty()),
            stabilityScaling.scaleFactor(
                rules.weight() * hiddenMarketRate,
                rules.lowStabilityPenalty()));
    }

    // The patrol factor as the breakdown states it: each tier scaled on its own, so the
    // tiers add up to the patrol factor's worth rather than being derived back out of it.
    private static PatrolFactor buildPatrolFactor(
            PatrolCounts patrols,
            PatrolWeighting rules,
            StabilityScaling stabilityScaling) {

        var penalty = rules.lowStabilityPenalty();
        return new PatrolFactor(
            buildPatrolTierFactor(patrols.small(), rules.smallWeight(), penalty, stabilityScaling),
            buildPatrolTierFactor(patrols.medium(), rules.mediumWeight(), penalty, stabilityScaling),
            buildPatrolTierFactor(patrols.large(), rules.largeWeight(), penalty, stabilityScaling),
            stabilityScaling.computePenaltyFraction(penalty));
    }

    // One patrol tier of that factor: its headcount, what one patrol of the tier is worth,
    // and what the tier folded in at. The only place a headcount meets a tier weight, so the
    // patrol worth and the tier lines that explain it cannot state the rule differently.
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
    private static double computeSizeRating(WeighedMarket market, BaseSizeWeighting rules) {
        if (market.isHidden() && rules.hiddenMarketScaling() == HiddenMarketScalingChoice.FIXED) {
            return rules.hiddenMarketFixedWeight();
        }
        return market.size();
    }

    // The market's own orbital station, when the station factor can earn anything for it:
    // nothing while the factor is toggled off or its weight is zero, so disabling the
    // factor either way skips the connected-entity station scan rather than paying for a
    // station whose bonus would be discarded.
    private static Optional<SectorEntityToken> findWeighedStation(
            MarketAPI market,
            StationWeighting rules) {

        if (!rules.isWeighted() || rules.weight() <= 0.0) {
            return Optional.empty();
        }
        return Markets.findAttachedStation(market);
    }

    // The market's patrol-tier counts, when the patrol factor can earn anything for them:
    // nothing while the factor is toggled off or no functional patrol HQ fields patrols
    // here. The $patrol gate (MarketPatrols.fieldsPatrols) confines the bonus to a colony a
    // patrol HQ actually garrisons: the raw patrol-count stats are also written by hidden
    // pirate and Luddic Path bases that set no flag, so reading the counts alone would
    // credit patrol strength vanilla would never spawn. The economy read is skipped while
    // the factor is off or the flag is absent, so neither costs a dynamic-stat lookup.
    private static Optional<PatrolCounts> readWeighedPatrolCounts(
            MarketAPI market,
            PatrolWeighting rules) {

        if (!rules.isWeighted() || !MarketPatrols.fieldsPatrols(market)) {
            return Optional.empty();
        }
        return Optional.of(MarketPatrols.readPatrolCounts(market));
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

    /**
     * One market as the weight rules see it: its identity and size, plus the subjects of the
     * two optional factors - the station and the patrol tiers - each absent when its factor
     * did not run for this market.
     *
     * <p>Read from the economy once and weighed as often as needed, so the connected-entity
     * scan and the dynamic-stat lookup behind the two optional factors are paid for once
     * however many times the market's worth is worked out.
     *
     * @param name      the colony's display name
     * @param isHidden  whether the colony is concealed rather than held in the open
     * @param size      the colony's own size, as the economy reports it
     * @param stability the colony's stability on its own 0..10 band - the reading behind
     *                  every one of the penalties below, carried as the economy states it
     *                  rather than as the fraction the scaling divides it down to
     * @param station   the market's orbital station, when the station factor admitted one
     * @param patrols   the market's patrol-tier counts, when the patrol factor admitted them
     */
    private record WeighedMarket(
        String name,
        boolean isHidden,
        int size,
        double stability,
        Optional<SectorEntityToken> station,
        Optional<PatrolCounts> patrols) {
    }
}
