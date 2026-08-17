package kmu.maplayers.politicalmap.base.dominance;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.entities.EntityNameplate;
import kmlib.starsector.entities.EntityNameplates;
import kmlib.starsector.markets.MarketPatrols;
import kmlib.starsector.markets.Markets;
import kmlib.starsector.markets.PatrolCounts;
import kmlib.starsector.systems.SystemColonies;

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
 * Weighs one star system's colonies into the per-faction footprints the dominance rule
 * compares.
 *
 * <p>The weighing half of the holder pipeline. Which colonies are in the system is not its
 * question: it is handed the system's {@link SystemColonies} and takes the known projection
 * over it - the same projection the ribbon counts and the cell classifies on - so a colony
 * this weighs and a colony another surface names are the one set read once rather than two
 * walks that can part company. A market's weight is the sum of three factors - its weighted
 * base size (a hidden market counting by its real size or a fixed token), any attached-station
 * bonus, and the patrol strength of a colony a functional patrol HQ garrisons (the
 * {@code $patrol} gate) - each cut for low stability by its own penalty (the player-facing
 * LunaLib settings, read upstream so this class stays free of settings access). The pure
 * comparison of the footprints it produces is {@link SystemDominance}'s job; turning the winner
 * into draw colours is a later, separate step.
 *
 * <p>Every weight is worked out once, as a {@link MarketWeightBreakdown} the scalar
 * weight is then summed over. A caller that wants the number reads the footprint;
 * one that has to explain the number reads {@link #readBreakdownByFaction} for the
 * same markets' parts. Neither can show a total the other's arithmetic disagrees
 * with, because there is only the one arithmetic.
 *
 * <p>Beside those two sits a third read that weighs nothing:
 * {@link #readUnweighedColoniesByFaction}, the colonies present in the system that the economy
 * does not list. They reach a caller describing the system and no caller computing it, so they
 * come back as nameplates alone. The two sets partition the projection - every colony is either
 * economy-listed and weighed or unlisted and merely named - which is what a colony being read
 * once and sorted, rather than sought by two walks, buys: neither set can gain a colony the
 * other keeps, nor lose one both pass over.
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
     * @param colonies the system's colony set, as one walk of it reported; empty yields an
     *                 empty map
     * @param rules    the dominance-weighting rules for this pass
     * @return each faction's footprint in the system, keyed by faction id; empty
     *         when the system holds no known owned market
     */
    public static Map<String, MarketFootprint> readByFaction(
            SystemColonies colonies,
            DominanceRules rules) {
        return readByFaction(
            colonies,
            rules,
            false); // Undiscovered markets are not included.
    }

    /**
     * Folds each faction's markets in one system into the footprint the dominance
     * rule compares.
     *
     * <p>Only the colonies the sector's economy lists are weighed. Every term of a dominance
     * weight is economy-fed - industries, conditions, computed stability - so an unlisted colony
     * has nothing for the arithmetic to read, and admitting one would hand its owner weight
     * nobody worked out. It is named instead, by {@link #readUnweighedColoniesByFaction}.
     *
     * <p>Condition-only markets (the placeholder market every uninhabited planet
     * carries for hazard and atmosphere conditions) never reach here: the colony set is
     * already selected on ownership, which is the rule that rejects them.
     *
     * @param colonies                         the system's colony set, as one walk of it
     *                                         reported; empty yields an empty map
     * @param rules                            the dominance-weighting rules for this pass -
     *                                         whether stability scales each rating and
     *                                         whether an attached station lifts it. The
     *                                         player's LunaLib toggles, read once per pass
     *                                         by the caller so a whole pass resolves under
     *                                         one rule
     * @param shouldIncludeUndiscoveredMarkets whether a market the player has not yet discovered
     *                                         still folds in (the "show undiscovered markets" dev
     *                                         override); false applies the normal known-to-player
     *                                         filter, true drops it so an undiscovered colony
     *                                         counts too
     * @return each faction's footprint in the system, keyed by faction id; empty
     *         when the system holds no folded market
     */
    public static Map<String, MarketFootprint> readByFaction(
            SystemColonies colonies,
            DominanceRules rules,
            boolean shouldIncludeUndiscoveredMarkets) {

        // The dominance-only projection of the fuller contribution read: a footprint-only caller
        // (the dominance resolve, the watcher's diff) drops the raw market size the picker's stats
        // need, so both share the one market walk and colony filter rather than defining a second.
        var footprintByFactionId = new LinkedHashMap<String, MarketFootprint>();

        for (var entry : readContributionsByFaction(
                    colonies,
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
     * @param colonies                         the system's colony set, as one walk of it reported;
     *                                         empty yields an empty map
     * @param rules                            the dominance-weighting rules for this pass, read once per
     *                                         pass by the caller so a whole pass resolves under one rule
     * @param shouldIncludeUndiscoveredMarkets whether a market the player has not yet discovered
     *                                         still folds in (the "show undiscovered markets" dev
     *                                         reveal); false applies the normal known-to-player
     *                                         filter
     * @return each faction's contribution in the system, keyed by faction id; empty when the system
     *         holds no folded market
     */
    public static Map<String, FactionMarketContribution> readContributionsByFaction(
            SystemColonies colonies,
            DominanceRules rules,
            boolean shouldIncludeUndiscoveredMarkets) {

        var contributionByFactionId = new LinkedHashMap<String, FactionMarketContribution>();
        for (var market : readWeighedColonies(colonies, shouldIncludeUndiscoveredMarkets)) {
            var factionId = market.getFaction().getId();
            var breakdown = readBreakdown(market, rules);
            var contribution = contributionByFactionId.getOrDefault(
                factionId,
                FactionMarketContribution.EMPTY);

            // Whether the colony is on a planet is read off the breakdown rather than from the
            // market again: the tie-break here and the box that names a station colony's station
            // must not be able to disagree about what kind of place a colony is.
            contributionByFactionId.put(
                factionId,
                contribution.addMarket(
                    breakdown.computeTotalWeight(),
                    !breakdown.isStationMarket(),
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
     * @param colonies                         the system's colony set, as one walk of it reported;
     *                                         empty yields an empty map
     * @param rules                            the dominance-weighting rules for this pass, read
     *                                         once per pass by the caller so a whole pass resolves
     *                                         under one rule
     * @param shouldIncludeUndiscoveredMarkets whether a market the player has not yet discovered
     *                                         still counts (the "show undiscovered markets" dev
     *                                         reveal); false applies the normal known-to-player
     *                                         filter
     * @return each faction's counted markets in the system with the breakdown of each market's
     *         weight, keyed by faction id and in the economy's own market order; empty when the
     *         system holds no counted market
     */
    public static Map<String, List<MarketWeightBreakdown>> readBreakdownByFaction(
            SystemColonies colonies,
            DominanceRules rules,
            boolean shouldIncludeUndiscoveredMarkets) {

        var breakdownsByFactionId = new LinkedHashMap<String, List<MarketWeightBreakdown>>();
        for (var market : readWeighedColonies(colonies, shouldIncludeUndiscoveredMarkets)) {
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
     * <p>A separate read rather than a widening of the weighed one, so a caller that must not see
     * these colonies cannot be handed them by accident. Both are selected out of the one colony
     * set on the one fact that tells them apart - whether the economy lists the colony - so the
     * two are exact complements and no colony can be admitted by one and refused by the other.
     *
     * <p>Each colony comes back as its nameplate alone rather than as a zeroed
     * {@link MarketWeightBreakdown}, and that is the guarantee the separation turns on: zero weight
     * is not absence on this side - a weightless colony still marks presence and paints its system
     * unopposed - so a value that could be summed into a footprint would leave the pass one
     * forgotten branch away from painting a system for a faction the mechanic never counted. A name
     * and a glyph cannot be summed into anything.
     *
     * @param colonies                         the system's colony set, as one walk of it reported;
     *                                         empty yields an empty map
     * @param shouldIncludeUndiscoveredMarkets whether a market the player has not yet discovered
     *                                         still counts (the "show undiscovered markets" dev
     *                                         reveal); false applies the normal known-to-player
     *                                         filter
     * @return each faction's unlisted colonies in the system, identified and nothing more, keyed by
     *         faction id and in the system's own entity order; empty when every colony present is
     *         one the economy lists
     */
    public static Map<String, List<EntityNameplate>> readUnweighedColoniesByFaction(
            SystemColonies colonies,
            boolean shouldIncludeUndiscoveredMarkets) {

        var coloniesByFactionId = new LinkedHashMap<String, List<EntityNameplate>>();
        for (var market : readUnweighedColonies(colonies, shouldIncludeUndiscoveredMarkets)) {
            coloniesByFactionId
                .computeIfAbsent(market.getFaction().getId(), factionId -> new ArrayList<>())
                .add(Markets.readNameplate(market));
        }
        return coloniesByFactionId;
    }

    /**
     * The colonies in one system this class weighs: the ones the economy lists, in the order it
     * lists them, under the known projection.
     *
     * <p>Named here rather than left implicit in each read because it is the whole of what
     * "dominance is economy-fed" amounts to, and it is asked from outside the weighing too - a
     * reader ordering the blocs this class ranked has to be ranking them over the same colonies,
     * or it can settle a contest among markets the weights were never folded from.
     *
     * @param colonies                         the system's colony set, as one walk of it reported;
     *                                         empty yields an empty list
     * @param shouldIncludeUndiscoveredMarkets whether a market the player has not yet discovered
     *                                         still counts (the "show undiscovered markets" dev
     *                                         reveal); false applies the normal known-to-player
     *                                         filter
     * @return the weighed colonies' markets, in the economy's own order
     */
    public static List<MarketAPI> readWeighedColonies(
            SystemColonies colonies,
            boolean shouldIncludeUndiscoveredMarkets) {

        return selectMarkets(colonies, shouldIncludeUndiscoveredMarkets, true);
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

    // The colonies present that the economy does not list - what the weighed read passes over, and
    // nothing it takes, the two dividing the projection between them.
    private static List<MarketAPI> readUnweighedColonies(
            SystemColonies colonies,
            boolean shouldIncludeUndiscoveredMarkets) {

        return selectMarkets(colonies, shouldIncludeUndiscoveredMarkets, false);
    }

    // One side of the known projection's one division, the flag naming which. Written once with
    // the side as a parameter rather than twice with the test negated, so the two sides cannot
    // drift into overlapping or into leaving a colony out between them.
    //
    // The projection is taken here rather than by the caller because the fog is per-read: the same
    // system is read under the player's filter for the map and under the dev reveal for a box, and
    // a projection cached across the pair would answer one of them wrongly.
    private static List<MarketAPI> selectMarkets(
            SystemColonies colonies,
            boolean shouldIncludeUndiscoveredMarkets,
            boolean shouldSelectListedByEconomy) {

        var markets = new ArrayList<MarketAPI>();

        for (var colony : colonies.readKnownColonies(shouldIncludeUndiscoveredMarkets)) {
            if (colony.isListedByEconomy() == shouldSelectListedByEconomy) {
                markets.add(colony.market());
            }
        }
        return markets;
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

    // Everything one market contributes to its own breakdown, gathered before any rule applies:
    // how it and its station are identified, its size, and the patrol tiers the patrol factor
    // admitted.
    // Gathered once because the market's worth is worked out twice - at full worth, then
    // under its own stability - and the connected-entity scan, the dynamic-stat lookup and the
    // two icon-spec reads behind it must not be paid for twice. The station's nameplate in
    // particular is read here, where the scan has just answered the token, rather than where the
    // factor is built: that is the half that runs twice.
    private static WeighedMarket readWeighedMarket(MarketAPI market, DominanceRules rules) {
        return new WeighedMarket(
            Markets.readNameplate(market),
            market.isHidden(),

            // getPlanetEntity() is non-null for a market on a planet and null for one on a
            // station, which is the only reading either consumer of the flag has.
            market.getPlanetEntity() == null,
            market.getSize(),
            market.getStabilityValue(),
            findWeighedStation(market, rules.station())
                .map(EntityNameplates::readNameplate),
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
            market.nameplate(),
            market.isHidden(),
            market.isStation(),
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

    // The station factor as the breakdown states it, identifying the station that earned it: the
    // player-set station weight in size points for an openly held stationed colony, or a
    // configured fraction of that weight for a hidden market, so a hidden fortress reads
    // above a bare outpost without matching an open stationed colony. The rate that survives
    // is what the bonus is built from, and the breakdown states its complement - the share
    // the rate removes - so both of the factor's cuts read the same way round.
    private static StationFactor buildStationFactor(
            WeighedMarket market,
            EntityNameplate station,
            StationWeighting rules,
            StabilityScaling stabilityScaling) {

        var hiddenMarketRate = market.isHidden() ? rules.hiddenMarketRate() : FULL_STATION_RATE;
        return new StationFactor(
            station,
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
     * One market as the breakdown of its weight sees it: how it is identified, its size, plus the
     * subjects of the two optional factors - the station and the patrol tiers - each absent when
     * its factor did not run for this market.
     *
     * <p>Read from the economy once and weighed as often as needed, so the connected-entity
     * scan, the dynamic-stat lookup and the two icon-spec reads are paid for once however many
     * times the market's worth is worked out.
     *
     * @param nameplate how the colony is identified - its name and the glyph the sector map marks
     *                  it with. Read here with the rest of the market, so what the breakdown
     *                  carries belongs to the market that was weighed
     * @param isHidden  whether the colony is concealed rather than held in the open
     * @param isStation whether the colony sits on an orbital station rather than on a planet
     * @param size      the colony's own size, as the economy reports it
     * @param stability the colony's stability on its own 0..10 band - the reading behind
     *                  every one of the penalties below, carried as the economy states it
     *                  rather than as the fraction the scaling divides it down to
     * @param station   how the market's orbital station is identified, when the station factor
     *                  admitted one. Carried as the nameplate rather than as the token it was read
     *                  from, because the token is a live entity whose specs cost a lookup apiece
     *                  and the factor is built once per weighing while the station is found once
     * @param patrols   the market's patrol-tier counts, when the patrol factor admitted them
     */
    private record WeighedMarket(
        EntityNameplate nameplate,
        boolean isHidden,
        boolean isStation,
        int size,
        double stability,
        Optional<EntityNameplate> station,
        Optional<PatrolCounts> patrols) {
    }
}
