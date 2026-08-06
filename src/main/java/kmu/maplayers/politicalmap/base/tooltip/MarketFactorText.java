package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.text.KmlibNumbers;

import kmu.maplayers.politicalmap.base.dominance.BaseSizeFactor;
import kmu.maplayers.politicalmap.base.dominance.KnownMarketFootprints;
import kmu.maplayers.politicalmap.base.dominance.PatrolFactor;
import kmu.maplayers.politicalmap.base.dominance.PatrolTierFactor;
import kmu.maplayers.politicalmap.base.dominance.StationFactor;
import kmu.util.KmuStrings;

/**
 * How one dominance factor's arithmetic reads as words: the rating or headcount that went in, the
 * weight that came out, and each cut taken along the way.
 *
 * <p>Every one of these values answers the same question - "where did this number come from" - so
 * they are worded to one grammar rather than each factor inventing its own: what went in, then the
 * weight it became, then the cuts, in the order the weight read applied them. A reader who has
 * worked out one factor's line has worked out them all.
 *
 * <p>Two units meet in that grammar and are kept apart by which separator joins them. A rating is
 * what the player set or the colony is - a size, a station's worth, what one patrol counts for -
 * and reads at the resolution such values are authored at. A weight is that rating on the
 * dominance grid, the same units as every score in the box, so a market's factor lines add up to
 * the market's own number and the markets to the bloc's.
 *
 * <p>A cut of nothing is not stated. The box exists to say what moved a number, and a penalty
 * reading zero moved nothing - printed anyway, it would put a deduction on every line of every
 * market that is stable, hidden, or neither.
 *
 * <p>Held apart from what the lines are ({@link MarketWeightRowResolver}) because the two answer
 * different questions - that decides which lines a market breaks down into, this what one of them
 * says - and because the units and the grammar are exactly what two factors worded apart would
 * eventually word differently.
 */
public final class MarketFactorText {

    // A fraction as a whole-number percentage, which is how every cut in the box is stated: a
    // penalty is a player-set fraction, and the digits past a percent of it are float noise.
    private static final int PERCENT_SCALE = 100;

    // The reading at which a cut has taken nothing and so is left unsaid.
    private static final int NO_PENALTY_PERCENT = 0;

    private MarketFactorText() {
    }

    /**
     * Words a colony's stability - the one reading every penalty below is derived from.
     *
     * @param stability the colony's stability on its own 0..10 band
     * @return the stability as the line states it
     */
    public static String formatStability(double stability) {
        return KmlibNumbers.formatCompactDecimal(stability);
    }

    /**
     * Words the base-size factor: the rating that entered the weight, what it became, and what low
     * stability took off it.
     *
     * @param factor         the market's base-size part
     * @param isFixedRating  whether the rating is the fixed token a hidden market folds in at
     *                       rather than the colony's own size - marked, because a size the colony
     *                       does not have is otherwise read as one it does
     * @return the factor as the line states it
     */
    public static String formatBaseSize(BaseSizeFactor factor, boolean isFixedRating) {
        var ratingText = KmlibNumbers.formatCompactDecimal(factor.sizeRating());

        if (isFixedRating) {
            ratingText = KmuStrings.format(KmuStrings.POLITICAL_MAP_TOOLTIP_FACTOR_FIXED, ratingText);
        }
        return appendPenalty(
            formatWeighed(ratingText, factor.contribution()),
            factor.stabilityPenaltyFraction());
    }

    /**
     * Words the station factor: what a station is worth, what this one became, and its two cuts in
     * the order the weight read took them - the hidden-market rate first, then low stability.
     *
     * @param factor the market's attached-station part
     * @return the factor as the line states it
     */
    public static String formatStation(StationFactor factor) {
        var weighedText = formatWeighed(
            KmlibNumbers.formatCompactDecimal(factor.weight()),
            factor.contribution());

        return appendPenalty(
            appendPenalty(weighedText, factor.hiddenMarketPenaltyFraction()),
            factor.stabilityPenaltyFraction());
    }

    /**
     * Words the patrol factor as a whole: how many patrols the colony fields, what the garrison
     * became, and what low stability took off it. The headcount is what the player can count on
     * the map, so it heads the line rather than the summed tier worth behind it.
     *
     * @param factor the market's fielded-patrol part
     * @return the factor as the line states it
     */
    public static String formatPatrols(PatrolFactor factor) {
        return appendPenalty(
            formatCounted(
                KmlibNumbers.formatGroupedInteger(factor.computeTotalCount()),
                factor.computeContribution()),
            factor.stabilityPenaltyFraction());
    }

    /**
     * Words one patrol tier: what a single patrol of it counts for, and what the tier became. The
     * cut is not restated here - all three tiers took the one stability cut the line above states,
     * and repeating it per tier would read as three separate deductions.
     *
     * @param tier one of the market's patrol tiers
     * @return the tier as the line states it
     */
    public static String formatPatrolTier(PatrolTierFactor tier) {
        return formatCounted(
            KmlibNumbers.formatCompactDecimal(tier.weight()),
            tier.contribution());
    }

    // A rating and the weight it became. The separator says the two are the same quantity stated
    // twice - before the grid and on it - rather than two quantities to be read against each other.
    private static String formatWeighed(String ratingText, double contribution) {
        return KmuStrings.format(
            KmuStrings.POLITICAL_MAP_TOOLTIP_FACTOR_WEIGHED,
            ratingText,
            formatWeight(contribution));
    }

    // A count and the weight it earned. Kept off the rating separator above because the two sides
    // are different quantities here - so many patrols, so much weight - and a reader tracing one
    // into the other would otherwise expect the units to match.
    private static String formatCounted(String countText, double contribution) {
        return KmuStrings.format(
            KmuStrings.POLITICAL_MAP_TOOLTIP_FACTOR_COUNTED,
            countText,
            formatWeight(contribution));
    }

    // A worth in size points as the weight the rest of the box counts in, so a factor line, its
    // market's line, and the bloc's line are all read in one unit and add up.
    private static String formatWeight(double contribution) {
        return KmlibNumbers.formatGroupedInteger(KnownMarketFootprints.roundToWeight(contribution));
    }

    // Runs a value on into a cut taken off it, and leaves it alone where nothing was taken. Applied
    // once per cut rather than per factor, so a factor that takes two states them the same way a
    // factor that takes one states its single cut.
    private static String appendPenalty(String valueText, double penaltyFraction) {
        var penaltyPercent = (int) Math.round(penaltyFraction * PERCENT_SCALE);

        if (penaltyPercent <= NO_PENALTY_PERCENT) {
            return valueText;
        }
        return KmuStrings.format(
            KmuStrings.POLITICAL_MAP_TOOLTIP_FACTOR_PENALTY,
            valueText,
            penaltyPercent);
    }
}
