package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.systems.claims.FactionClaimScore;
import kmlib.starsector.systems.claims.MarketClaimBreakdown;
import kmlib.text.KmlibNumbers;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.CellTooltipIndexOutcome;
import kmu.util.KmuStrings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

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
 * <p>The markets read in the order the mechanic would settle them - strongest first, a tie falling to
 * the earlier place in the economy's listing - so the one representing the faction comes out on top by
 * that order rather than by being put there, and the list reads as the contest rather than as a ranking
 * laid over it.
 *
 * <p>Exactly one market in the whole box is called out as the claim holder: the one that actually took
 * the system. Every faction is represented by its strongest, but only one of those won anything, and a
 * marker on each would read as several holders of a system that can only have one. It goes unsaid
 * entirely over a system held by decree, where nothing any market scored settled the matter.
 *
 * <p>Every market states where the economy lists it. The contest is settled on a strictly greater
 * score, so two markets that tie are parted by nothing but which of them the economy reached first -
 * a rule with no trace anywhere else in the box, and one a reader would otherwise have to take a tied
 * outcome as arbitrary for. The number is the market's place among the system's owned markets, so it
 * counts across factions: a tie between two <em>factions'</em> best markets is settled the same way.
 * Where a tie is actually drawn, the place stops being a bare identifier and says which way it went:
 * the market reached first reads as having won it and the rest as having lost.
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

    // The faction's markets rank descending by what they scored, a tie falling to the earlier place in
    // the economy's listing - which is the mechanic's own tie rule, so the list reads in the order the
    // contest would settle it and the market representing the faction comes out on top by that order
    // rather than by being put there.
    private static final Comparator<MarketClaimBreakdown> MARKET_ORDER =
        Comparator
            .comparingInt(MarketClaimBreakdown::computeTotalScore)
            .reversed()
            .thenComparingInt(MarketClaimBreakdown::listingPosition);

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
     * Resolves the markets behind one faction's claim standing into the entries listed beneath it,
     * strongest first, each carrying the terms of its own score - closed by the presence its several
     * holdings earned every one of them.
     *
     * @param standing               the faction's ranked place in the hovered system's claim contest
     * @param isHoldingTheClaim      whether this faction is the one the contest handed the system to.
     *                               Only its own strongest market is called out as the claim holder,
     *                               since only one market in the whole box took anything; false for
     *                               every rival, and for every faction where a decree settled the
     *                               system before a market was weighed
     * @param isListingUnfoundMarkets whether a market the player has not found may be listed. False
     *                               is the ordinary state and leaves those markets off; true is the
     *                               dev reveal, under which the account is stated in full
     * @return the entries in the order they are read
     */
    public static List<CellTooltipEntry> resolveMarketRows(
            FactionClaimScore standing,
            boolean isHoldingTheClaim,
            boolean isListingUnfoundMarkets) {

        // A market the player has not found is left off rather than blanked on the list: it carries
        // nothing the account needs, and a run of redacted lines would state the very count the
        // withholding is meant to keep. The strongest is never among them - a market takes a standing
        // only where it is not hidden, and one that is not hidden is one the player knows of.
        var listedMarkets = Stream
            .concat(Stream.of(standing.standingMarket()), standing.otherMarkets().stream())
            .filter(market -> isListingUnfoundMarkets || market.isKnownToPlayer())
            .sorted(MARKET_ORDER)
            .toList();

        var entries = new ArrayList<CellTooltipEntry>();

        for (var market : listedMarkets) {
            entries.add(resolveMarketEntry(
                createMarketLine(market, resolveIndexOutcome(market, listedMarkets)),
                market,
                isHoldingTheClaim && market == standing.standingMarket()));
        }

        // The presence term is stated only where the list above it is whole. Its whole claim on the
        // reader is that the count can be checked against the markets it follows, so printed over a
        // list something was withheld from it would either contradict what is on screen or state the
        // very number the withholding exists to keep back.
        if (listedMarkets.size() == standing.otherMarkets().size() + THE_MARKET_BEING_SCORED) {
            resolveSiblingEntry(standing).ifPresent(entries::add);
        }
        return List.copyOf(entries);
    }

    // What a market's place in the listing decided, judged against the markets listed beside it: it
    // won where something else scored exactly the same and this one was reached first, lost where
    // something equal was reached before it, and settled nothing where its score stands alone.
    //
    // Judged over what is drawn rather than over the faction's whole holdings, because the marking
    // exists to answer a question the drawn list raises - why does that one sit above this one, when
    // the numbers beside them match - and a tie with a market nobody can see is not that question.
    private static CellTooltipIndexOutcome resolveIndexOutcome(
            MarketClaimBreakdown market,
            List<MarketClaimBreakdown> listedMarkets) {

        var isTied = false;
        var isFirstReached = true;

        for (var other : listedMarkets) {
            if (other == market || other.computeTotalScore() != market.computeTotalScore()) {
                continue;
            }
            isTied = true;
            isFirstReached &= market.listingPosition() < other.listingPosition();
        }
        if (!isTied) {
            return CellTooltipIndexOutcome.UNCONTESTED;
        }
        return isFirstReached ? CellTooltipIndexOutcome.WON : CellTooltipIndexOutcome.LOST;
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
        // Working throughout bar the number it arrives at: the line is not one of the faction's
        // holdings but the arithmetic of a term all of them share, so it reads as quietly as the
        // working inside any other value and only the points it came to stay a finding.
        return Optional.of(CellTooltipEntry.createEntry(CellTooltipEntryLine
            .createLine(
                NO_MARK,
                KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_SIBLING_BONUS),
                formatBonus(siblingMarketCount))
            .derivesValueFrom(formatSiblingWorking(siblingMarketCount))
            .readsAsWorking()));
    }

    // One market as the entry it is listed as: its line over the terms its score is built from.
    //
    // Only the market that actually took the system says so. Every faction is represented by its own
    // strongest, but exactly one of those won anything, and calling each of them out would read as
    // several holders of one system - which is the very thing a contest cannot have.
    private static CellTooltipEntry resolveMarketEntry(
            CellTooltipEntryLine line,
            MarketClaimBreakdown market,
            boolean isHoldingTheClaim) {

        var marketLine = isHoldingTheClaim
            ? line.qualifiedWith(KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_HOLDER))
            : line;

        return CellTooltipEntry
            .createEntry(marketLine)
            .nesting(resolveTermEntries(market));
    }

    // The shape every market's own line takes: named, uncrested, and carrying what it scored - the
    // whole score, presence included, since that is the number the contest weighed it at.
    //
    // The name runs on into where the economy lists the market, because that number is the whole of
    // the answer to the one question the scores cannot settle: two markets on the same score are
    // parted by nothing but which the economy reached first. Stated on every market rather than only
    // on a tied one, so a reader meets the ordering before they need it and a tie reads as a rule
    // they already understand rather than as an outcome the box declines to explain.
    private static CellTooltipEntryLine createMarketLine(
            MarketClaimBreakdown market,
            CellTooltipIndexOutcome indexOutcome) {

        return CellTooltipEntryLine
            .createLine(
                NO_MARK,
                market.marketName(),
                KmlibNumbers.formatGroupedInteger(market.computeTotalScore()))
            .indexedAt(
                KmuStrings.format(
                    KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_LISTING_POSITION,
                    KmlibNumbers.formatGroupedInteger(market.listingPosition())),
                indexOutcome);
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
    // here, less the one whose own line the term is being counted for. Stated ahead of the points it
    // comes to, and drawn quieter than them, because the count is checkable against the very markets
    // listed above the line while the points alone would be a number to take on trust.
    //
    // The subtraction is of a market rather than of a point - it is the market being scored, which
    // does not count as its own sibling. The two sides nevertheless read the same number, because the
    // mechanic pays a flat point per sibling: the count is the term.
    private static String formatSiblingWorking(int siblingMarketCount) {
        return KmuStrings.format(
            KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_SIBLING_WORKING,
            KmlibNumbers.formatGroupedInteger(siblingMarketCount + THE_MARKET_BEING_SCORED));
    }
}
