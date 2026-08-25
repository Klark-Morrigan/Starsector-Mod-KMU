package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.colonies.SystemColonies;
import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.systems.claims.FactionClaimStanding;
import kmlib.starsector.systems.claims.MarketClaimBreakdown;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.starsector.systems.claims.WeighedClaimStanding;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;
import kmlib.text.KmlibNumbers;
import kmlib.text.KmlibStrings;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.CellTooltipRows;
import kmu.maplayers.base.tooltip.CellTooltipSections;
import kmu.maplayers.base.visibility.ColonyKnowledge;
import kmu.maplayers.base.visibility.ColonyVisibility;
import kmu.maplayers.base.visibility.MapVisibilityRules;
import kmu.util.KmuStrings;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The shape every box built on a hovered system's claim contest takes: what the system is, then who
 * claims it, then the rivals who could have taken it, then the factions present that were never
 * eligible to.
 *
 * <p>The claim mechanic publishes only a winner, so a fill on its own leaves the player guessing at a
 * border they cannot check. Naming the claimant beside the contest behind it is what turns the layer
 * from a colouring into something readable: a system reads as narrowly contested, uncontested, or
 * held by decree over rivals who out-score its holder.
 *
 * <p>The claim block is drawn wherever there is an answer worth stating: a claimant, or a populated
 * system nobody has taken - which is a real finding, since the factions listed below are present and yet
 * none of them holds it. What it does not do is state "None" beneath a banner already saying the system
 * holds nobody, which answers the same absence twice over. A decree stays either way: holding a system
 * with nothing in it is the one thing that banner does not say.
 *
 * <p>A block says how a faction stands to the claim rather than what kind of standing the contest gave
 * it. Both blocks therefore take both kinds - a faction the mechanic weighed and one it never reached -
 * routed on eligibility alone, which is the single axis a reader of this box wants: able to take the
 * system, or not. A faction present through concealed or unregistered colonies alone is a real presence
 * the map is drawing, and blocking it out of the box would leave the fill and the band naming a faction
 * the account of the system does not.
 *
 * <p>All of that is settled here rather than per box because two boxes over one system have to be two
 * amounts of detail about the same contest, not two contests. The breakdown is read once, the status is
 * judged against that same read, and every faction present is placed into its block by the one rule - so
 * a box stating more detail cannot name a different claimant, judge the system populated where the other
 * called it empty, or sort a rival into a block the other put it elsewhere in. What is left open is the
 * one thing the detail is: what, if anything, a listed faction breaks down into.
 *
 * <p>Stateless past the reader it is built around, so one shared instance per box serves the layer.
 */
public abstract class SystemClaimContestTooltip extends PoliticalMapCellTooltip {

    protected SystemClaimContestTooltip(ClaimBreakdownReader claimBreakdownReader) {
        super(claimBreakdownReader);
    }

    @Override
    protected final List<TooltipSection> buildBodySections(SectorAPI sector, StarSystemAPI system) {
        var sections = new ArrayList<TooltipSection>();

        // Read once for the whole box and applied to both questions it settles - whether people
        // live in the system, and which standings the player may be shown - since a banner and a
        // list resolved under two rules could withhold different colonies of the same system.
        //
        // The two questions differ under that one rule, and are meant to: the banner asks about
        // habitation, so a system holding only a derelict is headed "Unpopulated" while the list
        // beneath names the derelict, which is the true reading of such a system.
        var colonyVisibility = readColonyVisibility();

        // One read for the whole box: the claimant, the override behind it, and every standing the
        // projection leaves it free to name are all taken from a single pass, so no two lines can
        // describe different states of the system.
        var contest = ListedClaimContest.selectFrom(
            claimBreakdownReader.readBreakdown(system),
            colonyVisibility.shouldIncludeUndiscoveredMarkets());

        // Why the system holds nobody comes before who claims it, so a dead system names its state
        // first and the claim below reads as a hold over an empty system rather than over a colony.
        //
        // The walk is selected here rather than inside the line, and it is this box's own: the
        // claim reader beneath deliberately holds no colony index - it is a session-long instance,
        // and one that did would answer a later hover off the sector an earlier one saw - so there
        // is no shared walk here to take, and the cost is stated where it is paid.
        var colonies = SystemColonies.readColoniesIn(sector, system);
        var colonyKnowledge = ColonyKnowledge.over(sector, colonyVisibility);
        var statusRow = SystemStatusRow.resolveStatusRow(colonies, colonyKnowledge);

        // What an account may say about each colony beyond its score - what kind of place it is,
        // and how old the box's news of it is - folded once for the whole box off the walk above.
        // An account lists a system's colonies once per faction standing, so either read taken
        // where a row is drawn would walk the set again for every faction listed.
        var colonyReading = SystemColonyReading.readColoniesIn(
            sector,
            system,
            colonies,
            colonyKnowledge);

        CellTooltipSections.appendBannerSection(sections, statusRow);

        // The status is what the claim block is judged against, so the two are read from the one
        // resolve: a banner that appeared and a claim that says nobody would otherwise be settled by
        // two reads of the economy, one of which could call the system populated after the other had
        // already told the player it was not.
        CellTooltipSections.appendSection(
            sections,
            KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_CLAIM),
            buildClaimEntries(sector, contest, colonyReading, statusRow.isPresent()));

        CellTooltipSections.appendSection(
            sections,
            KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_CONTESTED),
            buildRivalEntries(
                sector,
                contest,
                colonyReading,
                FactionClaimStanding::isTerritorial));

        CellTooltipSections.appendSection(
            sections,
            KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_NON_TERRITORIAL),
            buildRivalEntries(
                sector,
                contest,
                colonyReading,
                standing -> !standing.isTerritorial()));

        return sections;
    }

    @Override
    protected final boolean isStatingCoreClaimInBody() {
        // The claim block names the claimant and marks a decreed hold on that very line, and the
        // breakdown sets the claimant to the decreed faction wherever a decree exists - so a decree
        // this box could head with is a decree its body is about to state anyway.
        return true;
    }

    @Override
    protected final Optional<String> resolveExpandedDetailName(
            SectorAPI sector,
            StarSystemAPI system) {

        // The counterpart accounts for the colonies behind the factions this box lists, so a box
        // listing none has nothing for it to account for: both boxes would state the same claim line
        // and the key would do nothing the player could see. Asked of the very read and the very
        // projection the box is built from, so it can never offer to expand a contest it is about to
        // draw as empty - which the fog alone can produce, a faction present only through colonies
        // the player has not found leaving a standing the box may not state.
        if (!ListedClaimContest
                .selectFrom(
                    claimBreakdownReader.readBreakdown(system),
                    readColonyVisibility().shouldIncludeUndiscoveredMarkets())
                .hasListedStanding()) {

            return Optional.empty();
        }
        // Answered for the pair at once rather than by each box, because it is the one thing they agree
        // on: the counterpart accounts for the very scores the ordinary box states, so a player
        // switching either way is being offered the same account. Which direction the hint reads
        // follows from which of the two is being drawn, and is none of this class's business.
        return Optional.of(KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_DETAIL_CONTRIBUTIONS));
    }

    /**
     * Resolves what hangs beneath each faction the box lists, as the account of where that faction's
     * standing came from. The seam the whole class exists around: which factions are listed, under
     * which heading, above what, and how each presents are all settled by the time this is called, so
     * what is left to answer is only whether a listed faction breaks down further and into what.
     *
     * <p>Asked per faction rather than once per paint, since a standing already carries the markets
     * behind it - there is no second read of the economy for a box to save by asking earlier. The
     * whole contest is handed over beside it because an account may turn on how the system was
     * settled rather than on the faction alone, and reading that a second way here is what would let
     * the account and the claim line above it disagree.
     *
     * <p>Taken on the standing's own interface rather than on the weighed kind, so a faction present
     * through colonies the mechanic never reached is accounted for like any other: it holds the very
     * colonies the map is drawing, which is what an account is of.
     *
     * <p>Listing a faction as the line naming it is the ordinary answer and the default, so a box with
     * nothing further to say overrides nothing.
     *
     * @param breakdown     the whole contest the box is being built from, in case the account turns
     *                      on it
     * @param standing      the faction's ranked place in that contest, of either kind
     * @param colonyReading what the box may say about the system's colonies beyond their scores,
     *                      folded once for the box - a claim row carries the id of the market it
     *                      was weighed from and nothing of the place behind it, so this is where
     *                      an account tells an unowned collapse from an unowned hulk, and where it
     *                      learns how old its news of either is
     * @return the entries listed beneath its line, in the order they are read; empty leaves the faction
     *         listed as its line alone
     */
    protected List<CellTooltipEntry> resolveAccountEntries(
            SystemClaimBreakdown breakdown,
            FactionClaimStanding standing,
            SystemColonyReading colonyReading) {

        return List.of();
    }

    /**
     * What this family may show of a colony - read live off the same settings the faction layer's
     * pass samples, so crossing between the two layers cannot make one call a system empty that
     * the other calls held.
     *
     * <p>Named here rather than spelled at each of the three places this family asks it - the
     * listing, the key hint's gate, and the account beneath a faction - because those three have to
     * agree. A box naming a faction whose every colony its own account then withholds is the shape
     * one read exists to rule out.
     *
     * <p>The whole rule rather than the reveal alone, so a reader taking one term of it cannot be
     * drawing under a rule the rest of the box is not.
     *
     * @return the colony rule the player's live settings describe
     */
    protected static ColonyVisibility readColonyVisibility() {
        return MapVisibilityRules.readFromLunaSettings().colonyVisibility();
    }

    // What the claim block lists: the one line naming whoever holds the system, and nothing at all
    // where nobody does and the banner above has already said the system holds nobody. Answered as an
    // empty listing rather than by skipping the call, so the block is dropped through the same rule that
    // drops every other empty one and the box cannot grow a heading standing over nothing.
    private List<CellTooltipEntry> buildClaimEntries(
            SectorAPI sector,
            ListedClaimContest contest,
            SystemColonyReading colonyReading,
            boolean isSystemHoldingNobody) {

        if (isSystemHoldingNobody
                && !KmlibStrings.hasText(contest.breakdown().claimantFactionId())) {

            return List.of();
        }
        return List.of(buildClaimantEntry(sector, contest, colonyReading));
    }

    // The one entry the claim section lists where it has one: whoever holds the system, or the plain
    // word for nobody when no eligible faction scored and no decree imposed one.
    private CellTooltipEntry buildClaimantEntry(
            SectorAPI sector,
            ListedClaimContest contest,
            SystemColonyReading colonyReading) {

        var breakdown = contest.breakdown();
        var claimantFactionId = breakdown.claimantFactionId();

        if (!KmlibStrings.hasText(claimantFactionId)) {
            return CellTooltipEntry.createEntry(CellTooltipEntryLine.createLine(
                null,
                KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_NONE),
                CellTooltipRows.NO_SCORE));
        }
        // The claimant's own standing, or none at all when it holds nothing the box may list: a core
        // imposed on a system its faction has no colony in is claimed without ever having been scored
        // for it - so the number and the account beneath it are both read off the one standing rather
        // than looked up apart, which is what stops a line showing one faction's score over another's
        // colonies.
        var standing = contest.findStanding(claimantFactionId);
        var claimantLine = standing
            .map(found -> buildStandingLine(sector, found))
            .orElseGet(() -> FactionTooltipLine.buildFactionLine(
                sector,
                claimantFactionId,
                CellTooltipRows.NO_SCORE));

        // A core is held by decree rather than won, so the claim is qualified on the very line it is
        // made - it is why that line outranks a higher-scoring one beneath it, which the banner heading
        // the box does not answer. Its market standing stays in the value column beside the qualifier:
        // a decreed hold does not erase the faction's presence.
        if (isCoreClaim(breakdown)) {
            claimantLine = claimantLine.qualifiedWith(
                KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_CORE_MARKER));
        }
        return CellTooltipEntry
            .createEntry(claimantLine)
            .nesting(standing
                .map(found -> resolveAccountEntries(breakdown, found, colonyReading))
                .orElseGet(List::of));
    }

    // One faction's line as its standing states it: its crest, its name, and what the contest weighed
    // its presence at.
    //
    // A presence-only standing's nought is drawn quiet, under the same treatment an unweighed market
    // line takes. It is the contest's statement about a faction it never reached rather than a figure
    // the faction competed with - drawn as loudly as the scores around it, that nought would read as
    // one competed for and lost, inviting exactly the comparison it cannot bear.
    private static CellTooltipEntryLine buildStandingLine(
            SectorAPI sector,
            FactionClaimStanding standing) {

        var standingLine = FactionTooltipLine.buildFactionLine(
            sector,
            standing.factionId(),
            KmlibNumbers.formatGroupedInteger(standing.score()));

        return standing instanceof WeighedClaimStanding
            ? standingLine
            : standingLine.statesUncountedValue();
    }

    // Whether the claim was imposed rather than won. Read off the claimant matching the override
    // rather than off the override merely being set, so the marker states what the line above it
    // actually shows - the faction named there holding the system by decree.
    private static boolean isCoreClaim(SystemClaimBreakdown breakdown) {
        return breakdown.claimantFactionId() != null
            && breakdown.claimantFactionId().equals(breakdown.overrideFactionId());
    }

    // One rival block's lines: everyone present but the claimant, narrowed to the eligibility that
    // block is about, already ranked strongest first by the breakdown - the order a contest is read
    // in, and the same order the mechanic itself settles it in. Each carries whatever this box
    // accounts for it with, subordinated: an account explains the line it hangs under rather than
    // restating it more finely.
    private List<CellTooltipEntry> buildRivalEntries(
            SectorAPI sector,
            ListedClaimContest contest,
            SystemColonyReading colonyReading,
            Predicate<FactionClaimStanding> isWantedKind) {

        var rivalStandings = contest.selectRivalStandings(isWantedKind);
        var entries = new ArrayList<CellTooltipEntry>(rivalStandings.size());

        for (var standing : rivalStandings) {
            entries.add(CellTooltipEntry
                .createEntry(buildStandingLine(sector, standing))
                .nesting(resolveAccountEntries(
                    contest.breakdown(),
                    standing,
                    colonyReading)));
        }
        return entries;
    }

    /**
     * The contest as this box may state it: the whole read, and the standings the known projection
     * leaves it free to name.
     *
     * <p>The two travel as one value because the second is a projection of the first and most lines
     * the box draws are read against both - the claimant off the breakdown, the number beside its
     * name off the standing the projection kept. Passed apart, one call's standings could arrive
     * beside another read's breakdown, and the box would state a claimant it had no standing for.
     */
    private record ListedClaimContest(
        SystemClaimBreakdown breakdown,
        List<FactionClaimStanding> listedStandings) {

        /**
         * Selects from a contest the standings the player may be shown.
         *
         * <p>The known projection over the contest - the same fog the market lines beneath a faction
         * are drawn through - applied to the listing rather than line by line, since a faction whose
         * every colony is withheld would otherwise be named over an account with nothing in it,
         * which is precisely the reading that tells the player what the fog is keeping back.
         *
         * <p>A weighed standing always survives it: the mechanic scores only markets held in the
         * open, and one held in the open is one the player knows of. What this ever drops is a
         * presence-only standing resting on undiscovered colonies alone.
         */
        static ListedClaimContest selectFrom(
                SystemClaimBreakdown breakdown,
                boolean isListingUnfoundMarkets) {

            return new ListedClaimContest(
                breakdown,
                breakdown
                    .scores()
                    .stream()
                    .filter(standing -> isListingUnfoundMarkets || hasFoundColony(standing))
                    .toList());
        }

        /** Whether anything survived the projection, which is what the box has to state at all. */
        boolean hasListedStanding() {
            return !listedStandings.isEmpty();
        }

        /**
         * One faction's place in the contest as the box may state it, or none where the faction
         * holds nothing the projection lists - a decree over a system its holder has no colony in,
         * or one whose every colony there the player has yet to find.
         */
        Optional<FactionClaimStanding> findStanding(String factionId) {
            return listedStandings
                .stream()
                .filter(standing -> standing.factionId().equals(factionId))
                .findFirst();
        }

        /**
         * The standings shown under a block other than the claim: everyone present but the
         * claimant, narrowed to the eligibility that block is about. The claimant is dropped from
         * both, since a faction named twice would read as holding two separate presences.
         *
         * <p>Narrowed on eligibility rather than on which kind of standing the contest gave a
         * faction, because that is what the two headings actually say. A territorial faction
         * holding only concealed bases is in the running by the mechanic's own gate and scored
         * nothing in this system, which is what {@code Contested by:} plus a nought states exactly
         * - while sorting it by record kind would file it beside a Remnant station's owner, which
         * is ineligible where it is not.
         */
        List<FactionClaimStanding> selectRivalStandings(
                Predicate<FactionClaimStanding> isWantedKind) {

            return listedStandings
                .stream()
                .filter(standing -> !standing.factionId().equals(breakdown.claimantFactionId()))
                .filter(isWantedKind)
                .toList();
        }

        // Whether the player has found any of the colonies a faction's standing rests on - one
        // found colony being enough, since the faction is then present on the map in its own
        // colours and the box is telling the player nothing they cannot already see.
        private static boolean hasFoundColony(FactionClaimStanding standing) {
            return standing
                .readHeldMarkets()
                .stream()
                .anyMatch(MarketClaimBreakdown::isKnownToPlayer);
        }
    }
}
