package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.colonies.SystemColonies;
import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.systems.claims.FactionClaimStanding;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.starsector.systems.claims.WeighedClaimStanding;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;
import kmlib.text.KmlibNumbers;
import kmlib.text.KmlibStrings;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.CellTooltipMark;
import kmu.maplayers.base.tooltip.CellTooltipRows;
import kmu.maplayers.base.tooltip.CellTooltipSections;
import kmu.maplayers.base.visibility.ColonyKnowledge;
import kmu.maplayers.base.visibility.ColonyVisibility;
import kmu.maplayers.base.visibility.MapVisibilityRules;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderGroupingSource;
import kmu.util.KmuStrings;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * The shape every box built on a hovered system's claim contest takes: what the system is, then who
 * claims it, then the factions standing with the claimant, then the rivals who could have taken it,
 * then the factions present that were never eligible to.
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
 * it, so every block takes both kinds - a faction the mechanic weighed and one it never reached. A
 * faction present through concealed or unregistered colonies alone is a real presence the map is
 * drawing, and blocking it out of the box would leave the fill and the band naming a faction the
 * account of the system does not.
 *
 * <p>Two axes place a faction, and the relation is the outer one without exception: every faction
 * sharing the claim holder's bloc goes to the allied block whether or not it could have taken the
 * system, and eligibility divides only what is left. The claim mechanic is faction-scoped and knows
 * nothing of an alliance, so two allied factions in one system compete and one of them loses - filed
 * on eligibility alone, that loser reads as fighting its own ally for a system the two of them jointly
 * hold, which is the map contradicting itself one view over.
 *
 * <p>That leaves the allied block holding both kinds under a heading naming neither, so the fact moves
 * onto the row: an ineligible faction listed there is qualified on its own line, the device the decreed
 * claim already uses. The qualifier is drawn only where the heading has not already said it, so it is
 * absent under the block whose heading is that very fact - one thing said once per hover, the rule
 * {@link #isStatingCoreClaimInBody} follows for the decree.
 *
 * <p>All of that is settled here rather than per box because two boxes over one system have to be two
 * amounts of detail about the same contest, not two contests. The breakdown is read once, the status is
 * judged against that same read, and every faction present is placed into its block by the one rule - so
 * a box stating more detail cannot name a different claimant, judge the system populated where the other
 * called it empty, or sort a rival into a block the other put it elsewhere in. What is left open is the
 * one thing the detail is: what, if anything, a listed faction breaks down into.
 *
 * <p>Stateless past the two seams it is built around, so one shared instance per box serves the layer.
 */
public abstract class SystemClaimContestTooltip extends PoliticalMapCellTooltip {

    // Whether the block naming a faction has to say on its lines which of them could have taken the
    // system. The allied block's heading states a relation and no eligibility, so both kinds sit under
    // it and the line is the only place left to tell them apart; the other two blocks are headed by the
    // eligibility itself, where a qualifier would state one fact twice in the space of two rows.
    private static final boolean IS_STATING_ELIGIBILITY_ON_LINE = true;
    private static final boolean IS_ELIGIBILITY_LEFT_TO_THE_HEADING = false;

    // Where the alliance set behind the routing is taken from. A source rather than a grouping,
    // because a box lives for the whole session while alliances form and dissolve inside it - one
    // captured at construction would go on filing a faction under the alliance it left an hour ago.
    private final HolderGroupingSource holderGroupingSource;

    protected SystemClaimContestTooltip(
            ClaimBreakdownReader claimBreakdownReader,
            HolderGroupingSource holderGroupingSource) {

        super(claimBreakdownReader);
        this.holderGroupingSource = holderGroupingSource;
    }

    @Override
    protected final List<TooltipSection> buildBodySections(SectorAPI sector, StarSystemAPI system) {
        var sections = new ArrayList<TooltipSection>();

        // One read for the whole box: the claimant, the override behind it, the colony rule and
        // every standing the projection leaves it free to name are all taken from a single pass, so
        // no two lines can describe different states of the system.
        var contest = readListedContest(system);

        // That one rule settles both questions the box asks of it - whether people live in the
        // system, and which standings the player may be shown - since a banner and a list resolved
        // under two rules could withhold different colonies of the same system.
        //
        // The two questions differ under that one rule, and are meant to: the banner asks about
        // habitation, so a system holding only a derelict is headed "Unpopulated" while the list
        // beneath names the derelict, which is the true reading of such a system.
        var colonyVisibility = contest.colonyVisibility();

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

        // Between the claim and its rivals, so the box reads as the holder, who stands with it, who
        // stands against it, and who was never in it at all. Empty wherever the claimant has no ally
        // present - which is every system on an install with nothing grouping factions - and dropped
        // by the same rule that drops any other block standing over no entries.
        CellTooltipSections.appendSection(
            sections,
            KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_ALLIED_WITH_HOLDER),
            buildAlliedEntries(sector, contest, colonyReading));

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
    protected final boolean hasExpandableAccountFor(SectorAPI sector, StarSystemAPI system) {
        // The counterpart accounts for the colonies behind the factions this box lists, so a box
        // listing none has nothing for it to account for: both boxes would state the same claim line
        // and the key would do nothing the player could see.
        return readListedContest(system).hasListedStanding();
    }

    /**
     * Resolves what hangs beneath each faction the box lists, as the account of where that faction's
     * standing came from. The seam the whole class exists around: which factions are listed, under
     * which heading, above what, and how each presents are all settled by the time this is called, so
     * what is left to answer is only whether a listed faction breaks down further and into what.
     *
     * <p>Asked per faction rather than once per paint, since a standing already carries the markets
     * behind it - there is no second read of the economy for a box to save by asking earlier.
     *
     * <p>The whole contest is handed over beside the standing, rather than the scored read alone,
     * because an account needs two things of it that no standing carries: how the system was settled,
     * and the colony rule the listing above was projected under. Both travel with the contest that
     * was read once for the box, so an account states exactly what the listing states. Reading either
     * afresh here is what would let the account and the lines above it draw under different answers -
     * and the rule especially, a live settings read per faction being one the player could in
     * principle move between two factions of one box.
     *
     * <p>Taken on the standing's own interface rather than on the weighed kind, so a faction present
     * through colonies the mechanic never reached is accounted for like any other: it holds the very
     * colonies the map is drawing, which is what an account is of.
     *
     * <p>Listing a faction as the line naming it is the ordinary answer and the default, so a box with
     * nothing further to say overrides nothing.
     *
     * @param contest       the whole contest the box is being built from - the scored read, the
     *                      colony rule it was projected under, and the standings that projection
     *                      left the box free to name
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
            ListedClaimContest contest,
            FactionClaimStanding standing,
            SystemColonyReading colonyReading) {

        return List.of();
    }

    /**
     * What this family may show of a colony - read live off the same settings the faction layer's
     * pass samples, so crossing between the two layers cannot make one call a system empty that
     * the other calls held.
     *
     * <p>Asked once per box, where the contest is read, and carried from there to everything drawn
     * under it - the listing, the key hint's gate, and the account beneath each faction. A box
     * naming a faction whose every colony its own account then withholds is the shape one read
     * exists to rule out, and asking again per faction is how a box would arrive there.
     *
     * <p>The whole rule rather than the reveal alone, so a reader taking one term of it cannot be
     * drawing under a rule the rest of the box is not.
     *
     * @return the colony rule the player's live settings describe
     */
    protected static ColonyVisibility readColonyVisibility() {
        return MapVisibilityRules.readFromLunaSettings().colonyVisibility();
    }

    // The hovered system's contest as this box may state it: the whole scored read, the colony rule
    // it was projected under, the standings that projection leaves the box free to name, and the
    // alliance set they are placed against.
    //
    // One read behind both the body and the key hint at its foot, because the hint offers an account
    // of exactly the factions the body lists. Resolved apart, the two are free to be answered from
    // different readings of one system - and the shape that takes is a box advertising a key that
    // does nothing, or declining to over a system it has just named a faction in.
    //
    // The grouping is sampled here for the same reason and travels with the rest of the read: every
    // block is routed against one reading of the alliance set, so an alliance dissolving between two
    // of the box's own questions cannot leave one faction filed as an ally and another as a rival.
    private ListedClaimContest readListedContest(StarSystemAPI system) {
        return ListedClaimContest.selectFrom(
            claimBreakdownReader.readBreakdown(system),
            readColonyVisibility(),
            holderGroupingSource.resolveGrouping());
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
                CellTooltipMark.NO_MARK,
                KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_NONE),
                CellTooltipRows.NO_SCORE));
        }
        // The claimant's own standing, or none at all when it holds nothing the box may list. Two
        // states arrive here, both of them decreed, a claim won by score always resting on a market
        // the listing keeps: a core imposed on a system its faction has no colony in, claimed
        // without ever having been scored for it; and one whose every colony there is concealed or
        // unlisted and unseen, which the listing above dropped. The claim is stated either way -
        // vanilla settles it unfogged and the map paints it, so withholding the line would keep
        // back what the player can already see - while the number and the account are both read off
        // the one standing rather than looked up apart, which is what stops a line showing one
        // faction's score over another's colonies.
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
                .map(found -> resolveAccountEntries(contest, found, colonyReading))
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

    // Whether the claim was imposed rather than won. The narrower of the breakdown's two decree
    // questions - the claimant matching the decree rather than a decree merely existing - so the
    // marker states what the line it is drawn on actually shows: the faction named there holding
    // the system by decree.
    private static boolean isCoreClaim(SystemClaimBreakdown breakdown) {
        return breakdown.isClaimedByDecree();
    }

    // The allied block's lines: everyone present who stands in the claim holder's bloc, of either
    // eligibility, since the relation places a faction before its eligibility does. The heading names
    // no eligibility, so each line states its own where it is the ineligible one.
    private List<CellTooltipEntry> buildAlliedEntries(
            SectorAPI sector,
            ListedClaimContest contest,
            SystemColonyReading colonyReading) {

        return buildListedEntries(
            sector,
            contest,
            colonyReading,
            contest.selectAlliedStandings(),
            IS_STATING_ELIGIBILITY_ON_LINE);
    }

    // One rival block's lines: everyone present but the claimant and its allies, narrowed to the
    // eligibility that block is about. That eligibility is the heading, so no line beneath restates it.
    private List<CellTooltipEntry> buildRivalEntries(
            SectorAPI sector,
            ListedClaimContest contest,
            SystemColonyReading colonyReading,
            Predicate<FactionClaimStanding> isWantedKind) {

        return buildListedEntries(
            sector,
            contest,
            colonyReading,
            contest.selectRivalStandings(isWantedKind),
            IS_ELIGIBILITY_LEFT_TO_THE_HEADING);
    }

    // One block's entries over the standings routed into it, in the order the breakdown handed them
    // over - strongest first, which is the order a contest is read in and the same one the mechanic
    // itself settles it in. Each carries whatever this box accounts for it with, subordinated: an
    // account explains the line it hangs under rather than restating it more finely.
    private List<CellTooltipEntry> buildListedEntries(
            SectorAPI sector,
            ListedClaimContest contest,
            SystemColonyReading colonyReading,
            List<FactionClaimStanding> standings,
            boolean isStatingEligibilityOnLine) {

        var entries = new ArrayList<CellTooltipEntry>(standings.size());

        for (var standing : standings) {
            entries.add(CellTooltipEntry
                .createEntry(buildListedLine(sector, standing, isStatingEligibilityOnLine))
                .nesting(resolveAccountEntries(
                    contest,
                    standing,
                    colonyReading)));
        }
        return entries;
    }

    // One faction's line as the block listing it needs it: its standing, plus the word for a faction
    // that could never have taken the system where the block's own heading has not already said so.
    //
    // Decided by the block rather than by the standing, because what the qualifier answers is what the
    // heading above it leaves unsaid - the same fact under a heading that states it would be one thing
    // said twice in the space of two rows.
    private static CellTooltipEntryLine buildListedLine(
            SectorAPI sector,
            FactionClaimStanding standing,
            boolean isStatingEligibilityOnLine) {

        var standingLine = buildStandingLine(sector, standing);

        if (isStatingEligibilityOnLine && !standing.isTerritorial()) {
            return standingLine.qualifiedWith(
                KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_QUALIFIER_NON_TERRITORIAL));
        }
        return standingLine;
    }

    /**
     * The contest as this box may state it: the whole read, the standings the known projection leaves
     * it free to name, the colony rule that projection was taken under, and the alliance set the
     * blocks are routed against.
     *
     * <p>They travel as one value because the standings are a projection of the breakdown under the
     * rule, and most lines the box draws are read against more than one of them - the claimant off the
     * breakdown, the number beside its name off the standing the projection kept, the banner over them
     * both off the rule, the block each falls in off the grouping. Passed apart, one call's standings
     * could arrive beside another read's breakdown, and the box would state a claimant it had no
     * standing for - or judge a system's habitation under a rule its listing was never projected
     * through, or route two of its blocks against two different alliance sets.
     */
    record ListedClaimContest(
        SystemClaimBreakdown breakdown,
        ColonyVisibility colonyVisibility,
        List<FactionClaimStanding> listedStandings,
        HolderGrouping holderGrouping) {

        /**
         * Selects from a contest the standings the player may be shown, keeping the rule that
         * selected them and the grouping they are placed under.
         *
         * <p>The listing rule the market lines beneath a faction are drawn through, asked of the
         * standing as a whole rather than line by line: a faction is kept where the box may draw a
         * row for at least one of its colonies. A faction whose every colony the rule withholds
         * would otherwise be named over an account with nothing in it, which is precisely the
         * reading that tells the player what the fog is keeping back.
         *
         * <p>A weighed standing therefore keeps its place however little of the system the player
         * has explored. The mechanic scores colonies nobody has discovered and can hand one of them
         * the system, so the box explaining the mechanic states the faction and redacts the row
         * beneath it - what the fog takes is the colony's identity, not the fact that somebody is
         * there. A presence-only standing whose every colony is unknown is still dropped: the
         * contest never weighed it, so no number on screen is short of it and there is nothing but
         * a name to state.
         */
        static ListedClaimContest selectFrom(
                SystemClaimBreakdown breakdown,
                ColonyVisibility colonyVisibility,
                HolderGrouping holderGrouping) {

            var isListingUndiscoveredMarkets = colonyVisibility.shouldIncludeUndiscoveredMarkets();

            return new ListedClaimContest(
                breakdown,
                colonyVisibility,
                breakdown
                    .scores()
                    .stream()
                    .filter(standing -> hasListedColony(standing, isListingUndiscoveredMarkets))
                    .toList(),
                holderGrouping);
        }

        /** Whether anything survived the projection, which is what the box has to state at all. */
        boolean hasListedStanding() {
            return !listedStandings.isEmpty();
        }

        /**
         * One faction's place in the contest as the box may state it, or none where the faction
         * holds nothing the listing keeps - a decree over a system its holder has no colony in, or
         * one whose every colony there is concealed or unlisted and unseen.
         */
        Optional<FactionClaimStanding> findStanding(String factionId) {
            return listedStandings
                .stream()
                .filter(standing -> standing.factionId().equals(factionId))
                .findFirst();
        }

        /**
         * The standings shown under the allied block: everyone present who shares the claim
         * holder's bloc, of either eligibility.
         *
         * <p>Both kinds, because the heading states a relation and the relation is true of an
         * ineligible ally exactly as it is of a rival that nearly took the system. Which of the two
         * a line is stays sayable on the line itself, so nothing is lost by not splitting them.
         */
        List<FactionClaimStanding> selectAlliedStandings() {

            var claimantBlocId = resolveClaimantBlocId();

            return streamListedRivals()
                .filter(standing -> isStandingInBloc(standing, claimantBlocId))
                .toList();
        }

        /**
         * The standings shown under a block other than the claim and the allied one: everyone
         * present but the claimant and its allies, narrowed to the eligibility that block is about.
         * The claimant is dropped from every block, since a faction named twice would read as
         * holding two separate presences.
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

            var claimantBlocId = resolveClaimantBlocId();

            return streamListedRivals()
                .filter(standing -> !isStandingInBloc(standing, claimantBlocId))
                .filter(isWantedKind)
                .toList();
        }

        // Everyone the box may name but the claim holder itself, which is the pool all three blocks
        // below the claim are drawn from. Dropped once here rather than per block, so no routing rule
        // added later can readmit the claimant to a block that is by definition about somebody else.
        private Stream<FactionClaimStanding> streamListedRivals() {
            return listedStandings
                .stream()
                .filter(standing -> !standing.factionId().equals(breakdown.claimantFactionId()));
        }

        // The bloc the claim holder stands in, or none at all over a system nobody has claimed -
        // there being no holder to resolve one for, and so nobody for a listed faction to be allied
        // with. A fact of the contest rather than of any one faction in it, so it is settled once per
        // block rather than re-derived against every standing the block walks.
        private String resolveClaimantBlocId() {

            var claimantFactionId = breakdown.claimantFactionId();

            return KmlibStrings.hasText(claimantFactionId)
                ? holderGrouping.resolveBlocId(claimantFactionId)
                : null;
        }

        // Whether a faction stands in a given bloc. Bloc equality between two distinct factions is
        // what an alliance amounts to here - the claimant is already out of the pool, so a faction
        // can never match itself, and an ungrouped faction is its own bloc and matches nobody. That
        // is why no test of whether the bloc is an alliance is needed beside it, and why an install
        // with nothing grouping factions routes every rival exactly as it did before.
        //
        // A bloc nobody named matches nothing, which is what an unclaimed system yields: the allied
        // block comes out empty, and its heading - which names a holder - never draws over a system
        // without one.
        private boolean isStandingInBloc(FactionClaimStanding standing, String blocId) {
            return blocId != null
                && blocId.equals(holderGrouping.resolveBlocId(standing.factionId()));
        }

        // Whether any colony a faction's standing rests on is one the box may draw a row for. Asked
        // through the market rule itself rather than restated here, the box stating outcomes over
        // the very colonies it decides: a second copy of the rule beside this one would be free to
        // disagree, and a faction listed over rows all withheld - or dropped over rows it could
        // have drawn - is exactly what that disagreement would look like.
        private static boolean hasListedColony(
                FactionClaimStanding standing,
                boolean isListingUndiscoveredMarkets) {

            return standing
                .readHeldMarkets()
                .stream()
                .anyMatch(market ->
                    ListedClaimMarkets.isListedMarket(market, isListingUndiscoveredMarkets));
        }
    }
}
