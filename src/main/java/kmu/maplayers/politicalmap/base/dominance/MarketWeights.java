package kmu.maplayers.politicalmap.base.dominance;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.entities.EntityNameplate;
import kmlib.starsector.entities.EntityNameplates;
import kmlib.starsector.markets.MarketPatrols;
import kmlib.starsector.markets.Markets;
import kmlib.starsector.markets.PatrolCounts;

import kmu.maplayers.politicalmap.base.dominance.weighting.BaseSizeWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.dominance.weighting.PatrolWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.StationWeighting;
import kmu.settings.HiddenMarketScalingChoice;

import java.util.Optional;

/**
 * What one colony is worth to the dominance rule, factor by factor.
 *
 * <p>The arithmetic alone. Which colonies are weighed at all, and how their weights fold into the
 * blocs a map paints, are questions about a system rather than about a market, and they are
 * answered beside this ({@link KnownMarketFootprints}) rather than inside it - so the rule that
 * scores one colony can be read, and changed, without reading a fold, and neither can quietly
 * come to depend on the other's order.
 *
 * <p>A market's weight is the sum of three factors - its weighted base size (a hidden market
 * counting by its real size or a fixed token), any attached-station bonus, and the patrol strength
 * of a colony a functional patrol HQ garrisons (the {@code $patrol} gate) - each cut for low
 * stability by its own penalty. The penalties and rates are the player-facing LunaLib settings,
 * read upstream and handed down as {@link DominanceRules}, so this class stays free of settings
 * access and a whole pass scores under one reading of them.
 *
 * <p>The answer is a {@link MarketWeightBreakdown} rather than a number, always. A caller that
 * wants the number sums the breakdown; one that has to explain the number to a player reads its
 * parts. Neither can show a total the other's arithmetic disagrees with, because there is only the
 * one arithmetic - and the grid that turns a worth in size points into a weight
 * ({@link #roundToWeight}) is here with it, for the same reason.
 */
public final class MarketWeights {

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

    private MarketWeights() {
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
    //
    // Reached by the fold beside it rather than published: a caller outside this package holds a
    // system rather than a market, and one handed a market alone could weigh a colony the
    // selection rules would never have admitted.
    static MarketWeightBreakdown readBreakdown(MarketAPI market, DominanceRules rules) {

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
            market.getId(),
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
            market.marketId(),
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
        String marketId,
        EntityNameplate nameplate,
        boolean isHidden,
        boolean isStation,
        int size,
        double stability,
        Optional<EntityNameplate> station,
        Optional<PatrolCounts> patrols) {
    }
}
