package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.systems.claims.FactionClaimScore;
import kmlib.starsector.systems.claims.MarketClaimBreakdown;
import kmlib.text.KmlibNumbers;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.util.KmuStrings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Resolves the arithmetic behind a faction's claim standing into the entries a block lists it as: every
 * market it holds in the hovered system, beneath each the terms that market's own score is built from,
 * and at the foot of them all the presence the faction's several holdings earned every one of them.
 *
 * <p>What makes a claim standing checkable rather than merely stated. The mechanic never sums a
 * faction's holdings - the contest is a comparison between single markets, so a faction is represented
 * by its strongest one alone - and the number on the faction's line is therefore one market's score.
 * Left as that number, a reader has no way to tell which market it was, nor to resist reading it as the
 * total of what the faction holds, which is exactly what it is not.
 *
 * <p>That market leads the list and is called out on its own line. It is the strongest by construction,
 * so leading is the descending order rather than an exception to it - what the marker adds is that this
 * is the one whose score the faction's line above carries. It goes unsaid over a system held by decree,
 * where nothing about the contest settled anything and a called-out market would be credited with an
 * outcome it did not produce.
 *
 * <p>The presence term is the faction's rather than any one market's, so it is stated once beneath the
 * list instead of on each market in it. The mechanic gives every market of a faction a point for each of
 * that faction's others in the system, which is the same number on all of them - repeated per market it
 * reads as several separate findings, and the count could not be checked against anything, whereas at
 * the foot of the very markets it counts it can be. Stated as the working rather than the result for the
 * same reason: the count is checkable, the result alone is not.
 *
 * <p>A term that earned a market nothing has no line. The box exists to say what built a number, and a
 * market that is no garrison is not one whose garrison came to nothing - it is one where the term never
 * arose.
 *
 * <p>No rating-to-weight grammar reaches these lines, unlike the dominance side's
 * ({@link MarketFactorText}): a claim score is a small whole number of points with no grid behind it, so
 * a term states the points it added and nothing about a unit change that never happens.
 *
 * <p>Pure over a standing with no Starsector types, so a whole faction's worth of lines is exercised on
 * hand-built parts.
 */
public final class ClaimScoreRowResolver {

    // The faction's remaining markets rank descending by what they scored, a tie falling to the lowest
    // name so the order is total and never depends on the economy walk order the breakdowns arrive in.
    private static final Comparator<MarketClaimBreakdown> MARKET_ORDER =
        Comparator
            .comparingInt(MarketClaimBreakdown::computeTotalScore)
            .reversed()
            .thenComparing(MarketClaimBreakdown::marketName);

    // A market and its terms are named rather than crested: the faction line above already carries the
    // crest, and repeating it down every line below would read as a second holder each time.
    private static final String NO_MARK = null;

    // The sibling count of a faction holding this system with one market alone - the reading at which
    // the term never arose rather than one at which it counted for nothing.
    private static final int NO_SIBLING_MARKETS = 0;

    // The market a sibling count is being counted for, which is what parts that count from how many
    // the faction holds here: a market is not its own sibling. Named because the line states the
    // subtraction, and a bare 1 there would read as a point taken off rather than a market.
    private static final int THE_MARKET_BEING_SCORED = 1;

    private ClaimScoreRowResolver() {
    }

    /**
     * Resolves the markets behind one faction's claim standing into the entries listed beneath it -
     * its strongest market first, then the rest in descending order, each carrying the terms of its
     * own score - closed by the presence its several holdings earned every one of them.
     *
     * @param standing                  the faction's ranked place in the hovered system's claim
     *                                  contest
     * @param isContestSettlingTheSystem whether the contest is what settled the system, rather than
     *                                  a decree imposed over it - the strongest market is called out
     *                                  only where it decided something
     * @return the entries in the order they are read
     */
    public static List<CellTooltipEntry> resolveMarketRows(
            FactionClaimScore standing,
            boolean isContestSettlingTheSystem) {

        var entries = new ArrayList<CellTooltipEntry>();
        var standingMarket = standing.standingMarket();
        var standingMarketLine = createMarketLine(standingMarket);

        // Called out on the line rather than left to be inferred from its place, since a reader
        // following the number upward needs to know which of these markets the faction's own score
        // came from - a faction's score is one market's, never the sum of the list beneath it.
        //
        // Unsaid over a decreed system: nothing about the contest settled that one, so calling a
        // market out would credit it with an outcome it did not produce.
        if (isContestSettlingTheSystem) {
            standingMarketLine = standingMarketLine.qualifiedWith(
                KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_STRONGEST));
        }
        entries.add(resolveMarketEntry(standingMarketLine, standingMarket));

        standing
            .otherMarkets()
            .stream()
            .sorted(MARKET_ORDER)
            .forEach(market -> entries.add(resolveMarketEntry(createMarketLine(market), market)));

        resolveSiblingEntry(standing).ifPresent(entries::add);

        return List.copyOf(entries);
    }

    // The presence term, stated once at the foot of the list rather than on each market's own
    // account. The mechanic gives every market of a faction a point for each of the faction's
    // others, so the term is the same number on every one of them - repeated per market it reads as
    // several separate findings, while one line at the foot of the very markets it counts lets the
    // reader check the count against the list it follows.
    //
    // Absent for a faction holding the system with one market, where the term never arose.
    private static Optional<CellTooltipEntry> resolveSiblingEntry(FactionClaimScore standing) {

        var siblingMarketCount = standing.standingMarket().siblingMarketCount();

        if (siblingMarketCount <= NO_SIBLING_MARKETS) {
            return Optional.empty();
        }
        return Optional.of(CellTooltipEntry.createEntry(CellTooltipEntryLine.createLine(
            NO_MARK,
            KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_OTHER_MARKETS),
            formatSiblingTotal(siblingMarketCount))));
    }

    // One market as the entry it is listed as: its line over the terms its score is built from. The
    // line arrives built, since only the caller knows whether this is the market the standing rests on.
    private static CellTooltipEntry resolveMarketEntry(
            CellTooltipEntryLine line,
            MarketClaimBreakdown market) {

        return CellTooltipEntry
            .createEntry(line)
            .nesting(resolveTermEntries(market));
    }

    // The shape every market's own line takes: named, uncrested, and carrying what it scored - the
    // whole score, presence included, since that is the number the contest weighed it at.
    private static CellTooltipEntryLine createMarketLine(MarketClaimBreakdown market) {
        return CellTooltipEntryLine.createLine(
            NO_MARK,
            market.marketName(),
            KmlibNumbers.formatGroupedInteger(market.computeTotalScore()));
    }

    // The terms of one market's score that are the market's own: the size it starts from, and what a
    // garrison adds. The presence every market of the faction shares is stated once below the list
    // rather than here, so these two are what the line above them adds that its siblings' do not.
    private static List<CellTooltipEntry> resolveTermEntries(MarketClaimBreakdown market) {

        var entries = new ArrayList<CellTooltipEntry>();

        // Always stated, even where it is the whole score: it is the term the sum starts from, and a
        // market listing no term at all would read as a number with no account behind it.
        entries.add(createTermEntry(
            KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_FACTOR_SIZE),
            KmlibNumbers.formatGroupedInteger(market.marketSize())));

        market.militaryBonus().ifPresent(bonus -> entries.add(createTermEntry(
            KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_MILITARY),
            formatBonus(bonus))));

        return entries;
    }

    // A term line, which breaks down no further - the claim score is two additions deep and no more.
    private static CellTooltipEntry createTermEntry(String labelText, String valueText) {
        return CellTooltipEntry.createEntry(
            CellTooltipEntryLine.createLine(NO_MARK, labelText, valueText));
    }

    // What a term added, signed so it reads as a term of a sum rather than as a quantity of its own -
    // the size above it is what the colony is, while these are what was added to it.
    private static String formatBonus(int amount) {
        return KmuStrings.format(
            KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_BONUS,
            KmlibNumbers.formatGroupedInteger(amount));
    }

    // The presence term worked out from what the reader can see: how many markets the faction holds
    // here, less the one whose own line the term is being counted for, giving what each of them
    // earned. Stated as the working rather than as its result, because the result alone is a number
    // to be taken on trust while the count is checkable against the very markets listed above it.
    //
    // The subtraction is of a market rather than of a point - it is the market being scored, which
    // does not count as its own sibling. The two sides of the equals nevertheless read the same
    // number, because the mechanic pays a flat point per sibling: the count is the term.
    private static String formatSiblingTotal(int siblingMarketCount) {
        return KmuStrings.format(
            KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_OTHER_MARKETS_TOTAL,
            KmlibNumbers.formatGroupedInteger(siblingMarketCount + THE_MARKET_BEING_SCORED),
            KmlibNumbers.formatGroupedInteger(siblingMarketCount));
    }
}
