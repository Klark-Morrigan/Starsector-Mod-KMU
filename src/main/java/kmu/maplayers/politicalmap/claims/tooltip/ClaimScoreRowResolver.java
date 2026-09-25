package kmu.maplayers.politicalmap.claims.tooltip;

import kmlib.starsector.systems.claims.FactionClaimStanding;
import kmlib.starsector.systems.claims.MarketClaimBreakdown;
import kmlib.starsector.systems.claims.WeighedClaimStanding;
import kmlib.text.KmlibNumbers;

import kmu.maplayers.base.tooltip.content.CellTooltipEntry;
import kmu.maplayers.base.tooltip.content.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.content.CellTooltipIndexOutcome;
import kmu.maplayers.base.tooltip.content.CellTooltipMark;
import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;
import kmu.maplayers.ownermap.tooltip.ColonyQualifier;
import kmu.maplayers.ownermap.tooltip.TermTooltipLine;
import kmu.util.KmuStringKeys;

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
 * <p>Pure over a standing with no Starsector types, so a whole faction's worth of lines is resolved
 * without a live economy. How a market the box may not name is drawn is {@link RedactedMarketLines}'s,
 * which listing ties decided anything is {@link ClaimTieOutcomes}'s, and what a market's line calls out
 * beyond its number is {@link ColonyQualifier}'s.
 */
final class ClaimScoreRowResolver {

    // The faction's markets rank descending by what they brought to the contest, a tie falling to the
    // earlier place in the economy's listing - which is the mechanic's own tie rule, so the list reads
    // in the order the contest would settle it and the market representing the faction comes out on
    // top by that order rather than by being put there.
    //
    // Ranked on the contest score rather than the arithmetic one, or a hidden market the mechanic
    // never weighed would sort above the market that actually took the system - which is exactly the
    // reading the order exists to rule out.
    private static final Comparator<MarketClaimBreakdown> MARKET_ORDER =
        Comparator
            .comparingInt(ClaimScoreRowResolver::resolveContestScore)
            .reversed()
            .thenComparingInt(MarketClaimBreakdown::listingPosition);

    // The sibling count of a faction holding this system with one market alone - the reading at which
    // the term never arose rather than one at which it counted for nothing.
    private static final int NO_SIBLING_MARKETS = 0;

    // What a market the mechanic never scored brought to the contest. Nought rather than absent,
    // because the market is on the list and the reader is being told what it counted for: a blank
    // column would read as a number the box failed to work out.
    private static final int NO_CONTEST_SCORE = 0;

    // The market a sibling count is being counted for, which is what parts that count from how many
    // the faction holds here: a market is not its own sibling. Named because the line states the
    // subtraction, and a bare 1 there would read as a point taken off rather than a market.
    private static final int THE_MARKET_BEING_SCORED = 1;

    // What a market of a faction the contest never weighed is called out as: nothing at all. Named
    // rather than passed as a bare false, since the flag says something about the system - no market
    // of such a faction took it - rather than about this line.
    private static final boolean NOTHING_TOOK_THE_SYSTEM = false;

    // What a market that did not take the system leads its line with: nothing, the claim being the
    // one finding this box states ahead of the shared vocabulary.
    private static final String NO_LEADING_FINDING = null;

    // The same for the market a standing rests on: a faction the contest never weighed rests on none,
    // so no line of such an account is the one its faction's score was taken from.
    private static final boolean NO_MARKET_CARRIES_THE_STANDING = false;

    private ClaimScoreRowResolver() {
    }

    /**
     * Resolves the markets behind one faction's claim standing into the entries listed beneath it,
     * strongest first, each carrying the terms of its own score - closed by the presence its several
     * holdings earned every one of them.
     *
     * @param standing the faction's ranked place in the contest, of either kind
     * @param reading  what the box knows about the hovered system beside this one standing - the
     *                 whole contest, the colony reading, which colonies may be named and how deep
     *                 to go. The markets themselves are always listed, this resolver being
     *                 consulted at all only where they are
     * @return the entries in the order they are read
     */
    public static List<CellTooltipEntry> resolveMarketRows(
            FactionClaimStanding standing,
            ClaimAccountReading reading) {

        var listedMarkets = selectListedMarkets(
            standing,
            reading.isListingUndiscoveredMarkets());

        // Routed on the kind of standing because the two things the fuller account is built from -
        // the market that carried the score, and the presence term counted for it - exist only on a
        // weighed one. The other arm is the presence-only kind, the standing being sealed over the
        // two.
        if (standing instanceof WeighedClaimStanding weighedStanding) {
            return resolveWeighedRows(weighedStanding, listedMarkets, reading);
        }
        return resolvePresenceOnlyRows(listedMarkets, reading);
    }

    // The account of a faction the contest weighed: its markets strongest first, the one that took
    // the system called out where this faction took it, and the presence its several holdings earned
    // every one of them at the foot.
    private static List<CellTooltipEntry> resolveWeighedRows(
            WeighedClaimStanding standing,
            List<MarketClaimBreakdown> listedMarkets,
            ClaimAccountReading reading) {

        var breakdown = reading.breakdown();

        // Whether this faction is the one the contest handed the system to, and so whose strongest
        // market is the one that took it. A decree settles the system before a single market is
        // weighed, so under one no market is the holder however the scores fell.
        var isHoldingTheClaim = !breakdown.isSettledByDecree()
            && standing.factionId().equals(breakdown.claimantFactionId());

        var entries = new ArrayList<CellTooltipEntry>();

        for (var market : listedMarkets) {

            // The market the faction's own line above states the score of, which settles two separate
            // questions: whether the call-out for taking the system belongs on this line, and - where
            // the name is blocked out - whether the score may be stated on it at all.
            var isCarryingTheStanding = market == standing.standingMarket();

            entries.add(resolveMarketEntry(
                createMarketLine(
                    market,
                    ClaimTieOutcomes.resolveOutcome(breakdown, standing, market),
                    isCarryingTheStanding),
                market,
                isHoldingTheClaim && isCarryingTheStanding,
                reading));
        }

        // Stated unless the list carries a market the count never included, which is the one way the
        // two can disagree in the direction that reads as the box having miscounted. A count running
        // ahead of a shortened list says only that the faction holds more than is shown - which every
        // listed market's own arithmetic says already, its score carrying a presence its terms do not.
        if (isEveryListedMarketCounted(listedMarkets)) {
            resolveSiblingEntry(standing).ifPresent(entries::add);
        }
        return List.copyOf(entries);
    }

    // The account of a faction the contest never weighed: the colonies it holds, each at nought, and
    // nothing else. Neither of the weighed account's two closing statements can be made here - no
    // market took the system, a presence-only standing scoring nought against a lead that changes
    // only on a score strictly greater than nought, and no presence term arose, the count being a
    // term of a score that was never computed.
    //
    // Every market takes the uncontested reading of its listing place for the same reason the tie
    // judgement would give it one: the mechanic passed over every colony behind such a standing, so
    // none of them won or lost a tie against anything.
    private static List<CellTooltipEntry> resolvePresenceOnlyRows(
            List<MarketClaimBreakdown> listedMarkets,
            ClaimAccountReading reading) {

        var entries = new ArrayList<CellTooltipEntry>(listedMarkets.size());

        for (var market : listedMarkets) {
            entries.add(resolveMarketEntry(
                createMarketLine(
                    market,
                    CellTooltipIndexOutcome.UNCONTESTED,
                    NO_MARKET_CARRIES_THE_STANDING),
                market,
                NOTHING_TOOK_THE_SYSTEM,
                reading));
        }
        return List.copyOf(entries);
    }

    // The presence term, stated once at the foot of the list rather than on each market's own
    // account. The mechanic gives every market of a faction a point for each of the faction's
    // others, so the term is the same number on every one of them - repeated per market it reads as
    // several separate findings, while one line at the foot of the very markets it counts lets the
    // reader check the count against the list it follows.
    //
    // The count is of what the faction holds rather than of the lines above it, so over a list a
    // market was withheld from it stands all the same and reads as exceeding what is shown. It gives
    // away nothing the list does not: each market's own line carries the whole score while its terms
    // state only its own, so the presence is already the difference between the two. Withheld, it
    // would leave each market's arithmetic short by an amount the reader can see.
    //
    // Absent for a faction holding the system with one market, where the term never arose.
    private static Optional<CellTooltipEntry> resolveSiblingEntry(WeighedClaimStanding standing) {

        var siblingMarketCount = standing.standingMarket().siblingMarketCount();

        if (siblingMarketCount <= NO_SIBLING_MARKETS) {
            return Optional.empty();
        }
        // A note about the list rather than one of the faction's holdings - the arithmetic of a term
        // all of them share - so it reads as quietly as the working inside any other value, down to
        // its name, and only the points it comes to stay a finding.
        return Optional.of(CellTooltipEntry.createEntry(TermTooltipLine
            .buildTermLine(
                KmuStringKeys.get(KmuStringKeys.POLITICAL_MAP_TOOLTIP_CLAIM_SIBLING_BONUS),
                formatBonus(siblingMarketCount))
            .derivesValueFrom(formatSiblingWorking(siblingMarketCount))
            .readsAsAside()));
    }

    // One market as the entry it is listed as: its line over the terms its score is built from.
    //
    // Only the market that actually took the system says so. Every faction is represented by its own
    // strongest, but exactly one of those won anything, and calling each of them out would read as
    // several holders of one system - which is the very thing a contest cannot have.
    private static CellTooltipEntry resolveMarketEntry(
            CellTooltipEntryLine line,
            MarketClaimBreakdown market,
            boolean isHoldingTheClaim,
            ClaimAccountReading reading) {

        var colonyReading = reading.colonyReading();

        // What sort of place a colony is, and how it is out of plain view, are facts about the world
        // rather than about the contest: a collapsed colony and a derelict both reach the list
        // unowned, off-economy and at nought, and only these words tell the two apart.
        //
        // Nothing chooses between the claim and what sort of place the colony is: the two are
        // findings about different things, and which of them leads is the shared resolver's to say
        // for every box at once. Stated here, the precedence would be a second copy of a rule the
        // sharing exists to have one of.
        var facts = colonyReading.readQualifierFacts(
            market.marketId(),
            market.isHiddenMarket(),
            !market.isOffEconomyMarket(),
            isHoldingTheClaim
                ? KmuStringKeys.get(KmuStringKeys.POLITICAL_MAP_TOOLTIP_CLAIM_HOLDER)
                : NO_LEADING_FINDING);

        // Described whatever the contest made of the market, because neither what sort of place a
        // colony is nor how current the box's news of it is has anything to do with whether the
        // mechanic weighed it.
        //
        // A blocked-out line breaks into nothing for a different reason from an empty breakdown
        // below - its terms were computed and are being withheld, rather than never having arisen - so
        // the two are stated apart rather than folded into one predicate that would read as one reason.
        return CellTooltipEntry
            .createEntry(colonyReading.describeColony(line, market.marketId(), facts))
            .nesting(RedactedMarketLines.isRedactedMarket(market)
                ? List.of()
                : resolveTermEntries(market, reading.detailLevel()));
    }

    // The shape every market's own line takes, whether or not the box may say what the market is
    // called: an opening image run, what stands for the name, what the contest made of the market, and
    // where the economy lists it.
    //
    // The name runs on into that listing place, because the place is the whole of the answer to the
    // one question the scores cannot settle: two markets on the same score are parted by nothing but
    // which the economy reached first - across factions as much as within one, a tie between two
    // factions' best markets being settled by it exactly as a tie between one faction's own is.
    // Stated on every market rather than only on a tied one, so a reader meets the ordering before
    // they need it and a tie reads as a rule they already understand rather than as an outcome the
    // box declines to explain.
    private static CellTooltipEntryLine createMarketLine(
            MarketClaimBreakdown market,
            CellTooltipIndexOutcome indexOutcome,
            boolean isCarryingTheStanding) {

        var line = RedactedMarketLines.isRedactedMarket(market)
            ? createBlockedOutMarketLine(market, isCarryingTheStanding)
            : createNamedMarketLine(market);

        // Stated on either shape, since the place identifies the market rather than describing the
        // colony - and a tie a blocked-out market won or lost is settled by that place alone, which no
        // other number on screen accounts for.
        return line.indexedAt(
            KmuStringKeys.format(
                KmuStringKeys.POLITICAL_MAP_TOOLTIP_CLAIM_LISTING_POSITION,
                KmlibNumbers.formatGroupedInteger(market.listingPosition())),
            indexOutcome);
    }

    // A market the box may name: the glyph the map marks its entity with, its name, and what it
    // brought to the contest - for an open market the whole score, presence included, since that is
    // the number the contest weighed it at.
    //
    // The glyph is read off the breakdown the market arrived in rather than looked up here, so it
    // belongs to the very market whose number sits beside it.
    //
    // A market the mechanic never scored says so with its number alone. Why it was passed over -
    // concealment, or an absence from the economy's listing - is called out after the name rather than
    // beside the number, those being findings about the place instead of statements about what the
    // contest made of it.
    //
    // That nought reads quiet, because it is the contest's statement about the market rather than
    // anything the market scored. In the list's own colour it would read as a score competed with and
    // lost on, which is the one thing it is not - the market was never weighed at all.
    private static CellTooltipEntryLine createNamedMarketLine(MarketClaimBreakdown market) {

        var line = CellTooltipEntryLine.createCountedLine(
            CellTooltipMark.resolveMarkForMapIcon(market.marketNameplate().mapIcon()),
            market.marketNameplate().displayName(),
            resolveContestScore(market));

        return market.isScoredOnItsOwnAccount() ? line : line.statesUncountedValue();
    }

    // A market the box may not name, carrying a number only where the faction's own line above already
    // states it. Everywhere else the column stands empty, the row being ranked by the score all the
    // same, so the lines either side of it bound what it came to without the box stating the figure.
    //
    // A market the contest never scored is the exception, and states its nought outright: the figure is
    // the contest's own statement that the market counted for nothing, which is no part of what the row
    // withholds, where an empty column would read as a figure kept back from a row that has none.
    //
    // Quietened on exactly the same reading as its named counterpart, and by the same call, so the two
    // shapes cannot come to disagree about which numbers were competed on.
    private static CellTooltipEntryLine createBlockedOutMarketLine(
            MarketClaimBreakdown market,
            boolean isCarryingTheStanding) {

        var line = isStatingBlockedOutScore(market, isCarryingTheStanding)
            ? RedactedMarketLines.createCountedRedactedLine(market, resolveContestScore(market))
            : RedactedMarketLines.createRedactedLine(market, CellTooltipEntryLine.NO_SCORE);

        return market.isScoredOnItsOwnAccount() ? line : line.statesUncountedValue();
    }

    // Whether a blocked-out row states a number at all: the nought of a market the contest never
    // scored, and the score of the market its faction stands on. Everywhere else the column stands
    // empty.
    private static boolean isStatingBlockedOutScore(
            MarketClaimBreakdown market,
            boolean isCarryingTheStanding) {

        return !market.isScoredOnItsOwnAccount() || isCarryingTheStanding;
    }

    // What a market brought to the contest. An open market brings its score; a hidden one brings
    // nothing, because the mechanic skips it before scoring - it reaches the contest only through
    // the presence term, which counts it without ever weighing it.
    //
    // Read here rather than off the breakdown, which faithfully reports the score a market
    // <em>would</em> carry: what a skipped market is worth to the contest is the box's question, not
    // the arithmetic's, and a nought printed against a size the reader can see needs the word beside
    // it to be a finding rather than a fault.
    private static int resolveContestScore(MarketClaimBreakdown market) {
        return market.isScoredOnItsOwnAccount() ? market.computeTotalScore() : NO_CONTEST_SCORE;
    }

    // The terms of one market's score that are the market's own: the size it starts from, and what a
    // garrison adds. The presence every market of the faction shares is stated once below the list
    // rather than here, so these two are what the line above them adds that its siblings' do not.
    //
    // A market the mechanic passed over breaks down into nothing, because nothing was computed for
    // it: its size and its garrison never entered any sum, and listing them would invite a reader to
    // add up to a number the line above deliberately does not carry. It is not the only line that
    // breaks into nothing - a market the box may not name withholds terms that were computed, which is
    // a separate reading and is taken where that line is composed.
    //
    // A term that earned a market nothing has no line: a market that is no garrison is not one whose
    // garrison came to nothing, but one where the term never arose. The term lines carry no mark
    // either, a size or a garrison bonus having nothing on the map to point at.
    //
    // A third reading joins them at the shallower levels, and it is about the box rather than about
    // the market: the player asked for the colonies and not the arithmetic under them, so the terms
    // are never worked out. Told apart from the two above by the cut alone - nothing here says which
    // of the three left a market bare, and nothing on screen has to.
    private static List<CellTooltipEntry> resolveTermEntries(
            MarketClaimBreakdown market,
            HoverTooltipDetailLevel detailLevel) {

        if (!detailLevel.isReadingAtLeast(HoverTooltipDetailLevel.MARKET_STATS)
                || !market.isScoredOnItsOwnAccount()) {

            return List.of();
        }
        var entries = new ArrayList<CellTooltipEntry>();

        // Always stated, even where it is the whole score: it is the term the sum starts from, and a
        // market listing no term at all would read as a number with no account behind it.
        entries.add(TermTooltipLine.buildTermEntry(
            KmuStringKeys.get(KmuStringKeys.POLITICAL_MAP_TOOLTIP_FACTOR_SIZE),
            KmlibNumbers.formatGroupedInteger(market.marketSize())));

        market.militaryBonus().ifPresent(bonus -> entries.add(TermTooltipLine.buildTermEntry(
            KmuStringKeys.get(KmuStringKeys.POLITICAL_MAP_TOOLTIP_CLAIM_MILITARY),
            formatBonus(bonus))));

        return entries;
    }

    // What a term added, signed so it reads as a term of a sum rather than as a quantity of its own -
    // the size above it is what the colony is, while these are what was added to it. No rating grammar
    // reaches it, unlike the dominance side's (MarketFactorText): a claim score is a small whole number
    // of points with no grid behind it.
    private static String formatBonus(int amount) {
        return KmuStringKeys.format(
            KmuStringKeys.POLITICAL_MAP_TOOLTIP_CLAIM_BONUS,
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
        return KmuStringKeys.format(
            KmuStringKeys.POLITICAL_MAP_TOOLTIP_CLAIM_SIBLING_WORKING,
            KmlibNumbers.formatGroupedInteger(siblingMarketCount + THE_MARKET_BEING_SCORED));
    }

    // The markets an account lists, out of everything the standing holds, in the order the contest
    // would settle them.
    //
    // A market the contest counted is listed however undiscovered its colony - weighed on its own
    // account or merely counted toward the sibling term, either way its effect is in the numbers on
    // screen already, so the row is what makes those add up. What is left off is a market the economy
    // never listed and the player knows nothing of: it reaches no term of the arithmetic, so a row for
    // it would be disclosure and nothing else.
    private static List<MarketClaimBreakdown> selectListedMarkets(
            FactionClaimStanding standing,
            boolean isListingUndiscoveredMarkets) {

        return standing
            .readHeldMarkets()
            .stream()
            .filter(market ->
                ListedClaimMarkets.isListedMarket(market, standing, isListingUndiscoveredMarkets))
            .sorted(MARKET_ORDER)
            .toList();
    }

    // Whether every market on the list is one the presence count counts.
    //
    // A colony the economy does not list is the one market that fails it: the mechanic never reached
    // it, so it sits among the very lines the count invites the reader to check it against while
    // being outside the count, and the term would read as short by a market on screen.
    //
    // Asked of the list alone, which is all it has to be asked of: every market the count counts is
    // one the listing rule draws, so the list can be contradicted by an uncounted line but never
    // falls short of the count.
    private static boolean isEveryListedMarketCounted(List<MarketClaimBreakdown> listedMarkets) {
        return listedMarkets
            .stream()
            .noneMatch(MarketClaimBreakdown::isOffEconomyMarket);
    }
}
