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

/**
 * Resolves the arithmetic behind a faction's claim standing into the entries a block lists it as: every
 * colony it holds in the hovered system, and beneath each the terms that colony's score is the sum of.
 *
 * <p>What makes a claim standing checkable rather than merely stated. The mechanic never sums a
 * faction's holdings - it stands on its strongest colony alone - so the number on the faction's line is
 * one colony's score, and the reader has no way to tell which colony that was or why it beat the others
 * without seeing them all. Listing every scored colony is also what makes the sibling term legible: the
 * count is exactly the colonies listed beside it, so it can be checked against the list rather than
 * taken on trust.
 *
 * <p>The colony the standing rests on leads the list and says so on its own line. It is the strongest by
 * construction, so leading is the descending order rather than an exception to it - what the marker adds
 * is that this is the one whose score the faction's line above carries.
 *
 * <p>A term that earned the colony nothing has no line. The box exists to say what built a number, and
 * a colony with no sibling and no garrison is not one whose sibling and garrison terms came to nothing -
 * it is one where those terms never arose.
 *
 * <p>No rating-to-weight grammar reaches these lines, unlike the dominance side's
 * ({@link MarketFactorText}): a claim score is a small whole number of points with no grid behind it, so
 * a term states the points it added and nothing about a unit change that never happens.
 *
 * <p>Pure over a standing with no Starsector types, so a whole faction's worth of lines is exercised on
 * hand-built parts.
 */
public final class ClaimScoreRowResolver {

    // The faction's remaining colonies rank descending by what they scored, a tie falling to the lowest
    // name so the order is total and never depends on the economy walk order the breakdowns arrive in.
    private static final Comparator<MarketClaimBreakdown> MARKET_ORDER =
        Comparator
            .comparingInt(MarketClaimBreakdown::computeTotalScore)
            .reversed()
            .thenComparing(MarketClaimBreakdown::marketName);

    // A colony and its terms are named rather than crested: the faction line above already carries the
    // crest, and repeating it down every line below would read as a second holder each time.
    private static final String NO_MARK = null;

    // The sibling count of a faction holding this system with one colony alone - the reading at which
    // the term never arose rather than one at which it counted for nothing.
    private static final int NO_SIBLING_MARKETS = 0;

    private ClaimScoreRowResolver() {
    }

    /**
     * Resolves the colonies behind one faction's claim standing into the entries listed beneath it -
     * the colony its standing rests on first, then the rest strongest first - each carrying the terms
     * its own score is the sum of.
     *
     * @param standing the faction's ranked place in the hovered system's claim contest
     * @return the colony entries in the order they are read, each carrying its term lines
     */
    public static List<CellTooltipEntry> resolveMarketRows(FactionClaimScore standing) {

        var entries = new ArrayList<CellTooltipEntry>();
        var standingMarket = standing.standingMarket();

        // Marked on the line rather than left to be inferred from its place, since a reader following
        // the number upward needs to know which of these colonies the faction's own score came from.
        entries.add(resolveMarketEntry(
            createMarketLine(standingMarket)
                .qualifiedWith(KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_STANDING)),
            standingMarket));

        standing
            .otherMarkets()
            .stream()
            .sorted(MARKET_ORDER)
            .forEach(market -> entries.add(resolveMarketEntry(createMarketLine(market), market)));

        return List.copyOf(entries);
    }

    // One colony as the entry it is listed as: its line over the terms its score is the sum of. The
    // line arrives built, since only the caller knows whether this is the colony the standing rests on.
    private static CellTooltipEntry resolveMarketEntry(
            CellTooltipEntryLine line,
            MarketClaimBreakdown market) {

        return CellTooltipEntry
            .createEntry(line)
            .nesting(resolveTermEntries(market));
    }

    // The shape every colony's own line takes: named, uncrested, and carrying what it scored.
    private static CellTooltipEntryLine createMarketLine(MarketClaimBreakdown market) {
        return CellTooltipEntryLine.createLine(
            NO_MARK,
            market.marketName(),
            KmlibNumbers.formatGroupedInteger(market.computeTotalScore()));
    }

    // The terms of one colony's score, in the order the mechanic adds them: the size it starts from,
    // then what its faction's other holdings here add, then what a garrison adds.
    private static List<CellTooltipEntry> resolveTermEntries(MarketClaimBreakdown market) {

        var entries = new ArrayList<CellTooltipEntry>();

        // Always stated, even where it is the whole score: it is the term the sum starts from, and a
        // colony listing no terms at all would read as a number with no account behind it.
        entries.add(createTermEntry(
            KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_FACTOR_SIZE),
            KmlibNumbers.formatGroupedInteger(market.marketSize())));

        if (market.siblingMarketCount() > NO_SIBLING_MARKETS) {
            entries.add(createTermEntry(
                KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_COLONIES),
                formatBonus(market.siblingMarketCount())));
        }
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
}
