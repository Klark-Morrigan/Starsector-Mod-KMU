package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.text.KmlibNumbers;

import kmu.maplayers.politicalmap.base.dominance.BaseSizeFactor;
import kmu.maplayers.politicalmap.base.dominance.MarketWeights;
import kmu.maplayers.politicalmap.base.dominance.PatrolFactor;
import kmu.maplayers.politicalmap.base.dominance.PatrolTierFactor;
import kmu.maplayers.politicalmap.base.dominance.StationFactor;
import kmu.util.KmuStringKeys;

/**
 * How one dominance factor's arithmetic reads as words: the rating that went in, the weight that came
 * out, and each cut taken along the way.
 *
 * <p>Every one of these values answers the same question - "where did this number come from" - so
 * they are worded to one grammar rather than each factor inventing its own: what went in, then the
 * weight it became, then the cuts, in the order the weight read applied them. A reader who has
 * worked out one factor's line has worked out them all.
 *
 * <p>A factor states no rating where none would mean anything. The patrols' is the one case: their
 * three tiers count for different amounts, so the headcount across them explains nothing about the
 * weight beside it, and the tiers state their own counts on their own lines where the rate beside
 * each makes the count mean something.
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
            ratingText = KmuStringKeys.format(KmuStringKeys.POLITICAL_MAP_TOOLTIP_FACTOR_FIXED, ratingText);
        }
        return appendPenalty(
            formatWeighed(ratingText, factor.contribution()),
            factor.stabilityPenaltyFraction());
    }

    /**
     * Words the size a colony actually is, run on into the separator parting it from the rating that
     * entered its weight instead - the working a hidden colony's size line opens on, so the reader is
     * shown "how big it is" before "what it counted as" rather than only the second.
     *
     * <p>Stated apart from the rating below so the line can draw the two in different shades, the same
     * split a patrol tier's rate and total are stated in. Asked for only where the two part company: a
     * colony counting by its own size would open on the number it is about to repeat.
     *
     * @param rawMarketSize the colony's own size, as the economy reports it
     * @return the real size the line opens its value on
     */
    public static String formatRawSizeWorking(int rawMarketSize) {
        return formatRatedWorking(KmlibNumbers.formatGroupedInteger(rawMarketSize));
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
     * Words the name of a station that shares its colony's name, telling the two apart.
     *
     * <p>A colony on a station and the station defending it are separate entities under one name,
     * so the account states that name twice - once for the colony and once, a level down, for the
     * station bonus - and a reader is left to work out that the second is not a repetition of the
     * first. The clarifier says which of the two the line is about.
     *
     * @param stationName the station's own display name
     * @return that name with the clarifier the station line states it under
     */
    public static String formatMilitaryStationName(String stationName) {
        return KmuStringKeys.format(
            KmuStringKeys.POLITICAL_MAP_TOOLTIP_FACTOR_STATION_MILITARY,
            stationName);
    }

    /**
     * Words the patrol factor as a whole: what the fielded patrols came to, and what low stability
     * took off it.
     *
     * <p>No headcount heads it. The three tiers are weighted differently, so a summed count is a
     * number that explains nothing - two colonies fielding six patrols each can be worth wildly
     * different amounts - and stating it beside the weight invites the reader to divide one by the
     * other. What each tier fielded is on the tier's own line, where the rate beside it makes the
     * count mean something.
     *
     * @param factor the market's fielded-patrol part
     * @return the factor as the line states it
     */
    public static String formatPatrols(PatrolFactor factor) {
        return appendPenalty(
            formatWeight(factor.computeContribution()),
            factor.stabilityPenaltyFraction());
    }

    /**
     * Words the working behind one patrol tier's line: what a single patrol of that tier counts
     * for, run on into the separator parting it from what the tier came to. Stated apart from the
     * total below so the line can draw the two in different shades - the rate is the arithmetic, the
     * total is the finding - rather than as one number with a stray separator in it.
     *
     * @param tier one of the market's patrol tiers
     * @return the rate the tier's line opens its value on
     */
    public static String formatPatrolTierWorking(PatrolTierFactor tier) {
        return formatCountedWorking(KmlibNumbers.formatCompactDecimal(tier.weight()));
    }

    /**
     * Words what one patrol tier came to. The cut is not restated here - all three tiers took the
     * one stability cut the line above states, and repeating it per tier would read as three
     * separate deductions.
     *
     * @param tier one of the market's patrol tiers
     * @return the weight the tier folded in at
     */
    public static String formatPatrolTierTotal(PatrolTierFactor tier) {
        return formatWeight(tier.contribution());
    }

    // A rating and the weight it became, as one run. Composed from the half below rather than
    // stating the separator itself, so a line drawn in one shade and a line that opens on the same
    // half in grey read one separator: two spellings of it would eventually part company.
    private static String formatWeighed(String ratingText, double contribution) {
        return joinToWeight(formatRatedWorking(ratingText), contribution);
    }

    // The left half of a rated value: what the colony is or the player set, run on into the
    // separator saying the number past it is that same quantity on the dominance grid. The one home
    // of that separator, whether the value is drawn as one run or as the two a line colours apart.
    private static String formatRatedWorking(String ratingText) {
        return KmuStringKeys.format(KmuStringKeys.POLITICAL_MAP_TOOLTIP_FACTOR_RATED, ratingText);
    }

    // The left half of a counted value: how many of a thing there are, run on into the separator
    // parting them from what they earned. Kept off the rating separator above because the two sides
    // are different quantities here - so many patrols, so much weight - and a reader tracing one
    // into the other would otherwise expect the units to match.
    private static String formatCountedWorking(String countText) {
        return KmuStringKeys.format(KmuStringKeys.POLITICAL_MAP_TOOLTIP_FACTOR_COUNTED, countText);
    }

    // A working half and the weight it resolves to, set side by side. The one place a value's two
    // halves are run together, so however a half was worded the whole reads at the same spacing the
    // lines that colour their halves apart are drawn at.
    private static String joinToWeight(String workingText, double contribution) {
        return KmuStringKeys.format(
            KmuStringKeys.POLITICAL_MAP_TOOLTIP_FACTOR_JOINED,
            workingText,
            formatWeight(contribution));
    }

    // A worth in size points as the weight the rest of the box counts in, so a factor line, its
    // market's line, and the bloc's line are all read in one unit and add up.
    private static String formatWeight(double contribution) {
        return KmlibNumbers.formatGroupedInteger(MarketWeights.roundToWeight(contribution));
    }

    // Runs a value on into a cut taken off it, and leaves it alone where nothing was taken. Applied
    // once per cut rather than per factor, so a factor that takes two states them the same way a
    // factor that takes one states its single cut.
    private static String appendPenalty(String valueText, double penaltyFraction) {
        var penaltyPercent = (int) Math.round(penaltyFraction * PERCENT_SCALE);

        if (penaltyPercent <= NO_PENALTY_PERCENT) {
            return valueText;
        }
        return KmuStringKeys.format(
            KmuStringKeys.POLITICAL_MAP_TOOLTIP_FACTOR_PENALTY,
            valueText,
            penaltyPercent);
    }
}
