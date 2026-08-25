package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.systems.claims.FactionClaimStanding;
import kmlib.starsector.systems.claims.MarketClaimBreakdown;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.starsector.systems.claims.WeighedClaimStanding;
import kmlib.text.KmlibNumbers;
import kmlib.text.KmlibStrings;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.CellTooltipIndexOutcome;
import kmu.maplayers.base.tooltip.CellTooltipMark;
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
 * <p>The markets read in the order the mechanic would settle them - strongest first, a tie falling to
 * the earlier place in the economy's listing - so the one representing the faction comes out on top by
 * that order rather than by being put there, and the list reads as the contest rather than as a ranking
 * laid over it.
 *
 * <p>A faction the mechanic weighed nothing for is accounted for the same way, and that is the case
 * the whole shape has to bend least for: its colonies are listed at nought and nothing else is
 * stated. No market is called out as the claim holder, nothing there having won the system, and no
 * presence term closes the list, no score having been computed for the count to be a term of. What
 * is left is the plain listing of what the faction holds, which is the whole of what the contest has
 * to say about a presence it never reached.
 *
 * <p>A market the mechanic never weighed is listed at nought. Two kinds reach the box that way: one
 * held in concealment, which the walk skips before scoring, and one the economy does not list, which
 * the walk never reaches at all. Either brought nothing to the contest however large it is - and
 * printing the score it would have carried would put a market that took no part above the one that
 * took the system. Both are listed all the same rather than dropped: a colony the player can see on
 * the map, in a faction's colours, has to appear in the account of who holds the system. Nothing calls
 * out which of the two it is, the nought being the whole of what the contest has to say about either.
 *
 * <p>What such a line does say is what kind of place it names ({@link ColonyKindQualifier}), which
 * is a fact about the world rather than about the contest. A collapsed colony and a derelict hulk
 * both reach the list unowned, off-economy and at nought, so without it the account could not tell
 * a place people still live from a wreck nobody ever did.
 *
 * <p>A market line also says how old the box's news of it is, where nobody is looking at the colony
 * as the box is drawn. Why that reaches further on this list than on the dominance side is
 * {@link ExpandedSystemClaimTooltip}'s to say.
 *
 * <p>Every market line leads with the glyph the sector map marks that market's entity with, scored or
 * not. The term lines beneath a market carry no mark at all: a size or a garrison bonus has nothing on
 * the map to point at. What a mark off the map is for and how it is coloured are
 * {@link kmu.maplayers.base.tooltip.CellTooltipMark#resolveMarkForMapIcon}'s.
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
 * Where a tie the mechanic actually consulted is drawn, the place stops being a bare identifier and
 * says which way it went - the market reached first as having won it, the rest as having lost. Which
 * ties those are is {@link ClaimTieOutcomes}'s answer, judged over the whole contest rather than over
 * this faction's list, exactly as the walk it explains compares.
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

    private ClaimScoreRowResolver() {
    }

    /**
     * Resolves the markets behind one faction's claim standing into the entries listed beneath it,
     * strongest first, each carrying the terms of its own score - closed by the presence its several
     * holdings earned every one of them.
     *
     * @param breakdown               the whole contest the standing was ranked in - what settles
     *                                who the claim holder is and which listing ties actually
     *                                decided something, neither of which one faction's standing
     *                                can answer
     * @param standing                the faction's ranked place in that contest, of either kind
     * @param colonyReading           what the box may say about the system's colonies beyond their
     *                                scores, folded once for the whole box. A claim row carries the
     *                                id of the market it was scored from and nothing of the place
     *                                behind it, so this is the only thing parting an unowned
     *                                collapse from an unowned hulk on the list, and the only thing
     *                                that can date either
     * @param isListingUnfoundMarkets whether a market the player has not found may be listed. False
     *                                is the ordinary state and leaves those markets off; true is
     *                                the dev reveal, under which the account is stated in full
     * @return the entries in the order they are read
     */
    public static List<CellTooltipEntry> resolveMarketRows(
            SystemClaimBreakdown breakdown,
            FactionClaimStanding standing,
            SystemColonyReading colonyReading,
            boolean isListingUnfoundMarkets) {

        var marketListing = MarketListing.selectFrom(standing, isListingUnfoundMarkets);

        // Routed on the kind of standing because the two things the fuller account is built from -
        // the market that carried the score, and the presence term counted for it - exist only on a
        // weighed one. The other arm is the presence-only kind, the standing being sealed over the
        // two.
        if (standing instanceof WeighedClaimStanding weighedStanding) {
            return resolveWeighedRows(
                breakdown,
                weighedStanding,
                colonyReading,
                marketListing);
        }
        return resolvePresenceOnlyRows(colonyReading, marketListing.listedMarkets());
    }

    // The account of a faction the contest weighed: its markets strongest first, the one that took
    // the system called out where this faction took it, and the presence its several holdings earned
    // every one of them at the foot.
    private static List<CellTooltipEntry> resolveWeighedRows(
            SystemClaimBreakdown breakdown,
            WeighedClaimStanding standing,
            SystemColonyReading colonyReading,
            MarketListing marketListing) {

        // Whether this faction is the one the contest handed the system to, and so whose strongest
        // market is the one that took it. A decree settles the system before a single market is
        // weighed, so under one no market is the holder however the scores fell.
        var isHoldingTheClaim = !KmlibStrings.hasText(breakdown.overrideFactionId())
            && standing.factionId().equals(breakdown.claimantFactionId());

        var entries = new ArrayList<CellTooltipEntry>();

        for (var market : marketListing.listedMarkets()) {
            entries.add(resolveMarketEntry(
                createMarketLine(market, ClaimTieOutcomes.resolveOutcome(
                    breakdown,
                    standing,
                    market)),
                market,
                colonyReading,
                isHoldingTheClaim && market == standing.standingMarket()));
        }

        // The presence term is stated only where the list above it is exactly the markets the count
        // counts. Its whole claim on the reader is that the number can be checked against the list it
        // follows, and it loses that either way the two can part company.
        if (marketListing.isEveryListedMarketCounted()) {
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
            SystemColonyReading colonyReading,
            List<MarketClaimBreakdown> listedMarkets) {

        var entries = new ArrayList<CellTooltipEntry>(listedMarkets.size());

        for (var market : listedMarkets) {
            entries.add(resolveMarketEntry(
                createMarketLine(market, CellTooltipIndexOutcome.UNCONTESTED),
                market,
                colonyReading,
                NOTHING_TOOK_THE_SYSTEM));
        }
        return List.copyOf(entries);
    }

    // The presence term, stated once at the foot of the list rather than on each market's own
    // account. The mechanic gives every market of a faction a point for each of the faction's
    // others, so the term is the same number on every one of them - repeated per market it reads as
    // several separate findings, while one line at the foot of the very markets it counts lets the
    // reader check the count against the list it follows.
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
        return Optional.of(CellTooltipEntry.createEntry(CellTooltipEntryLine
            .createLine(
                CellTooltipMark.NO_MARK,
                KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_SIBLING_BONUS),
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
            SystemColonyReading colonyReading,
            boolean isHoldingTheClaim) {

        // The two statuses cannot contend for the line's end. A market that took the system is a
        // colony somebody holds and the economy lists; the one kind that states anything here is a
        // world people left, which is unowned and off-economy and so was never weighed at all.
        var marketLine = isHoldingTheClaim
            ? line.qualifiedWith(KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_HOLDER))
            : ColonyKindQualifier.qualifyByKind(
                line,
                colonyReading.readKindOf(market.marketId()));

        // Remarked on whatever the contest made of the market, because how current the box's news
        // of a colony is has nothing to do with whether the mechanic weighed it.
        return CellTooltipEntry
            .createEntry(colonyReading.remarkOnColony(marketLine, market.marketId()))
            .nesting(resolveTermEntries(market));
    }

    // The shape every market's own line takes: led by the glyph the map marks its entity with, named,
    // and carrying what it brought to the contest - for an open market the whole score, presence
    // included, since that is the number the contest weighed it at.
    //
    // The glyph is read off the breakdown the market arrived in rather than looked up here, so it
    // belongs to the very market whose number sits beside it.
    //
    // The name runs on into where the economy lists the market, because that number is the whole of
    // the answer to the one question the scores cannot settle: two markets on the same score are
    // parted by nothing but which the economy reached first. Stated on every market rather than only
    // on a tied one, so a reader meets the ordering before they need it and a tie reads as a rule
    // they already understand rather than as an outcome the box declines to explain.
    //
    // A market the mechanic never scored says so with its number alone. Nothing calls out why it was
    // passed over - concealment, or an absence from the economy's listing: either word would raise a
    // question about the mechanic that the box would then owe an answer to, where the nought beside a
    // listed market already says the one thing that matters about it here - it counted for nothing in
    // this contest.
    //
    // That nought reads quiet, because it is the contest's statement about the market rather than
    // anything the market scored. In the list's own colour it would read as a score competed with
    // and lost on, which is the one thing it is not - the market was never weighed at all.
    private static CellTooltipEntryLine createMarketLine(
            MarketClaimBreakdown market,
            CellTooltipIndexOutcome indexOutcome) {

        var line = CellTooltipEntryLine
            .createLine(
                CellTooltipMark.resolveMarkForMapIcon(market.marketNameplate().mapIcon()),
                market.marketNameplate().displayName(),
                KmlibNumbers.formatGroupedInteger(resolveContestScore(market)))
            .indexedAt(
                KmuStrings.format(
                    KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_LISTING_POSITION,
                    KmlibNumbers.formatGroupedInteger(market.listingPosition())),
                indexOutcome);

        return market.isScoredOnItsOwnAccount() ? line : line.statesUncountedValue();
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
    // add up to a number the line above deliberately does not carry.
    private static List<CellTooltipEntry> resolveTermEntries(MarketClaimBreakdown market) {

        if (!market.isScoredOnItsOwnAccount()) {
            return List.of();
        }
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
            CellTooltipEntryLine.createLine(CellTooltipMark.NO_MARK, labelText, valueText));
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

    /**
     * A faction's markets as an account states them: the ones it lists, and the whole set the
     * standing holds them out of.
     *
     * <p>The two travel as one value because the only question either is asked apart from the other
     * is whether they agree - the presence term is stated only where the list is exactly the markets
     * the count counts. Passed apart, a listing selected from one standing could arrive beside
     * another's holdings, and the term would be checked against a list it was never drawn from.
     */
    private record MarketListing(
        List<MarketClaimBreakdown> listedMarkets,
        List<MarketClaimBreakdown> heldMarkets) {

        /**
         * Selects the markets an account lists from what a standing holds, in the order the contest
         * would settle them.
         *
         * <p>A market the player has not found is left off rather than blanked on the list: it
         * carries nothing the account needs, and a run of redacted lines would state the very count
         * the withholding is meant to keep. A weighed standing's strongest is never among them - a
         * market takes a standing only where it is not hidden, and one that is not hidden is one the
         * player knows of.
         */
        static MarketListing selectFrom(
                FactionClaimStanding standing,
                boolean isListingUnfoundMarkets) {

            var heldMarkets = standing.readHeldMarkets();

            return new MarketListing(
                heldMarkets
                    .stream()
                    .filter(market -> isListingUnfoundMarkets || market.isKnownToPlayer())
                    .sorted(MARKET_ORDER)
                    .toList(),
                heldMarkets);
        }

        /**
         * Whether the markets listed are neither fewer nor more than the ones the presence count
         * counts.
         *
         * <p>Fewer, where a market was withheld for being unfound: the term would then either
         * contradict what is on screen or state the very number the withholding exists to keep back.
         * More, where a market the economy does not list is on the list: the mechanic never reached
         * it, so the count does not include it, and the term would read as short by exactly that
         * market.
         */
        boolean isEveryListedMarketCounted() {
            return listedMarkets.size() == heldMarkets.size()
                && listedMarkets
                    .stream()
                    .noneMatch(MarketClaimBreakdown::isOffEconomyMarket);
        }
    }
}
