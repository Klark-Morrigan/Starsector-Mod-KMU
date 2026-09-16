package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.markets.colonies.SystemColonies;
import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.systems.claims.FactionClaimStanding;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.starsector.systems.claims.WeighedClaimStanding;
import kmlib.text.KmlibStrings;

import kmu.maplayers.base.tooltip.content.CellTooltipEntry;
import kmu.maplayers.base.tooltip.content.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.content.CellTooltipMark;
import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;
import kmu.maplayers.base.tooltip.layout.CellTooltipBody;
import kmu.maplayers.base.tooltip.layout.ComposedCellBody;
import kmu.maplayers.base.visibility.colonies.ColonyKnowledge;
import kmu.maplayers.base.visibility.colonies.ColonyVisibility;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.politicalmap.base.dominance.HolderGroupingSource;
import kmu.util.KmuStrings;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape every box built on a hovered system's claim contest takes: what the system is, then who
 * claims it, then the blocks the factions around the claimant are listed in ({@link ClaimContestBlock},
 * which is where they and their order are declared).
 *
 * <p>The claim mechanic publishes only a winner, so a fill on its own leaves the player guessing at a
 * border they cannot check. Naming the claimant beside the contest behind it is what turns the layer
 * from a colouring into something readable: a system reads as narrowly taken, taken outright, or held
 * by decree over rivals who out-score its holder.
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
 * <p>Two axes place a faction, and how it stands to the claim holder is the outer one without
 * exception: every faction sharing the holder's bloc goes to the allied block, everyone else on good
 * terms with it goes to the friendly one, and eligibility divides only what neither took. The claim
 * mechanic is faction-scoped and knows nothing of an alliance, so two allied factions in one system
 * compete and one of them loses - filed on eligibility alone, that loser reads as fighting its own
 * ally for a system the two of them jointly hold, which is the map contradicting itself one view over.
 * A faction merely on good terms with the holder is the same fault a step down the scale: it is drawn
 * beside the holder in friendly colours on no view at all, and would nonetheless be reported as
 * fighting it.
 *
 * <p>Alliance stays outside disposition, so a bloc's own ally is never re-sorted by how it feels about
 * it: one gone sour is still an ally, and one on excellent terms gains nothing by it. Which
 * disposition earns the friendly block is
 * {@link kmlib.starsector.factions.relation.StarsectorFactionRelations}' - the base game's own
 * step from indifference to goodwill, a landmark the player is shown on every faction screen, where a
 * cut taken elsewhere in the scale would be one they never see.
 *
 * <p>That leaves the two relation blocks holding both eligibilities under headings naming neither, so
 * the fact moves onto the row: an ineligible faction listed in either is qualified on its own line,
 * the device the decreed claim already uses. Which blocks need that of their lines is declared with
 * the blocks themselves, since it follows from what each heading already says - one thing said once
 * per hover, the rule {@link #isStatingCoreClaimInBody} follows for the decree.
 *
 * <p>All of that is settled here rather than per box, so a layer built on this contest states one
 * contest however deep it is read. The breakdown is read once, the status is judged against that same
 * read, and every faction present is placed into its block by the one rule - so no box on this shape
 * can name a different claimant from another, judge the system populated where the other called it
 * empty, or sort a rival into a block the other put it elsewhere in. What that one read comes to, and
 * which block it puts each standing in, is {@link ListedClaimContest}'s; what is left open is the one
 * thing the detail is: what, if anything, a listed faction breaks down into.
 *
 * <p>Stateless past the two seams it is built around, so one shared instance per box serves the layer.
 */
public abstract class SystemClaimContestTooltip extends PoliticalMapCellTooltip {

    protected SystemClaimContestTooltip(
            ClaimBreakdownReader claimBreakdownReader,
            HolderGroupingSource holderGroupingSource,
            ContestWordingSource contestWordingSource) {

        super(claimBreakdownReader, holderGroupingSource, contestWordingSource);
    }

    @Override
    protected final ComposedCellBody composeBody(
            SectorAPI sector,
            StarSystemAPI system,
            HoverTooltipDetailLevel detailLevel) {

        // One read for the whole box: the claimant, the override behind it, the colony rule and
        // every standing the projection leaves it free to name are all taken from a single pass, so
        // no two lines can describe different states of the system.
        var contest = readListedContest(sector, system);

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

        // The four things every line below is drawn from, gathered once. They are settled together
        // and true of the whole paint, so carrying them one by one down the blocks is what would let
        // a later block be handed one of them from somewhere else - or read at a depth another was
        // not.
        var reading = new HoveredClaimReading(sector, contest, colonyReading, detailLevel);

        var body = CellTooltipBody.openBody(detailLevel);

        body.appendBannerSection(statusRow);

        // The status is what the claim block is judged against, so the two are read from the one
        // resolve: a banner that appeared and a claim that says nobody would otherwise be settled by
        // two reads of the economy, one of which could call the system populated after the other had
        // already told the player it was not.
        //
        // Appended here rather than beside the blocks below for that reason alone: it is the one
        // block whose contents turn on what the banner answered, and every other is placed by how a
        // faction stands to the claimant this one names.
        body.appendSection(
            KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_CLAIM),
            buildClaimEntries(reading, statusRow.isPresent()));

        appendStandingSections(body, reading);

        // How deep the box goes goes back beside the blocks, judged on the very contest they were
        // drawn from: a box listing nobody has nothing for any deeper level to account for, and
        // asking again would read the system a second time to settle what this one already knows.
        return new ComposedCellBody(
            body.readBlocks(),
            resolveDeepestHeldLevel(contest.hasListedStanding()));
    }

    @Override
    protected final boolean isStatingCoreClaimInBody() {
        // The claim block names the claimant and marks a decreed hold on that very line, and the
        // breakdown sets the claimant to the decreed faction wherever a decree exists - so a decree
        // this box could head with is a decree its body is about to state anyway.
        return true;
    }

    @Override
    protected final HoverTooltipDetailLevel resolveDeepestHeldLevelFor(
            SectorAPI sector,
            StarSystemAPI system) {

        // The deeper tiers account for the colonies behind the factions this box lists, so a box
        // listing none has nothing for them to account for: every level would state the same claim
        // line and the key would do nothing the player could see.
        return resolveDeepestHeldLevel(readListedContest(sector, system).hasListedStanding());
    }

    /**
     * Resolves what hangs beneath each faction the box lists, as the account of where that faction's
     * standing came from. The seam the whole class exists around: which factions are listed, under
     * which heading, above what, and how each presents are all settled by the time this is called, so
     * what is left to answer is only whether a listed faction breaks down further and into what.
     *
     * <p>Asked per faction rather than once per paint, since a standing already carries the markets
     * behind it - there is no second read of the economy for a box to save by asking earlier. Asked
     * at all only where the level admits an account, so the shallowest level does none of the
     * selecting, ranking and wording an account of every faction present comes to.
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
     * <p>Answered by every box on this shape rather than defaulted: a box inheriting an empty account
     * would draw the same thing at every detail level while the key went on offering to open it
     * up, which is the one failure the level cycle cannot show the player.
     *
     * @param contest       the whole contest the box is being built from - the scored read, the
     *                      colony rule it was projected under, and the standings that projection
     *                      left the box free to name
     * @param standing      the faction's ranked place in that contest, of either kind
     * @param colonyReading what the box may say about the system's colonies beyond their scores,
     *                      folded once for the box - a claim row carries the ID of the market it
     *                      was weighed from and nothing of the place behind it, so this is where
     *                      an account tells an unowned collapse from an unowned hulk, and where it
     *                      learns how old its news of either is
     * @param detailLevel   how deep the box has been asked to read, so an account carrying tiers of
     *                      its own works out only the ones that will be drawn
     * @return the entries listed beneath its line, in the order they are read; empty leaves the faction
     *         listed as its line alone
     */
    protected abstract List<CellTooltipEntry> resolveAccountEntries(
        ListedClaimContest contest,
        FactionClaimStanding standing,
        SystemColonyReading colonyReading,
        HoverTooltipDetailLevel detailLevel);

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

    // Everyone present other than the claimant, in the blocks their standing to it puts them in, laid
    // down in the order those blocks are declared in rather than in one restated here. So the box reads
    // as the holder and then the contest around it.
    //
    // Every block is offered unconditionally - the allied one is empty wherever the claimant has no
    // ally present, which is every system on an install with nothing grouping factions, and the
    // friendly one wherever nobody present is above neutral with the holder - since a block standing
    // over no entries is dropped by the same rule that drops any other.
    //
    // The wording is sampled once for the whole run, so the box is headed under a single reading of
    // the install rather than one taken again per block.
    private void appendStandingSections(CellTooltipBody body, HoveredClaimReading reading) {

        var contestWording = resolveContestWording();

        for (var block : ClaimContestBlock.values()) {
            body.appendSection(
                KmuStrings.get(block.resolveHeadingKey(contestWording)),
                buildListedEntries(
                    reading,
                    block.selectStandings(reading.contest()),
                    block.isStatingEligibilityOnLine()));
        }
    }

    // The hovered system's contest as this box may state it: the whole scored read, the colony rule
    // it was projected under, the standings that projection leaves the box free to name, and the two
    // relations they are placed against.
    //
    // One read behind both the body and the key hint at its foot, for the reason ComposedCellBody
    // sets out: the hint offers an account of exactly the factions the body lists.
    //
    // Both relations travel with it because they are sampled together (PoliticalMapCellTooltip):
    // every block is routed against one reading of each, so an alliance dissolving or a disposition
    // sliding past neutral between two of the box's own questions cannot leave one faction filed as
    // an ally or a friend and another as a rival.
    private ListedClaimContest readListedContest(SectorAPI sector, StarSystemAPI system) {

        var blocRelations = sampleBlocRelations(sector);

        return ListedClaimContest.selectFrom(
            claimBreakdownReader.readBreakdown(system),
            readColonyVisibility(),
            blocRelations.affiliation(),
            blocRelations.friendliness());
    }

    // What the claim block lists: the one line naming whoever holds the system, and nothing at all
    // where nobody does and the banner above has already said the system holds nobody. Answered as an
    // empty listing rather than by skipping the call, so the block is dropped through the same rule that
    // drops every other empty one and the box cannot grow a heading standing over nothing.
    private List<CellTooltipEntry> buildClaimEntries(
            HoveredClaimReading reading,
            boolean isSystemHoldingNobody) {

        if (isSystemHoldingNobody
                && !KmlibStrings.hasText(reading.contest().breakdown().claimantFactionId())) {

            return List.of();
        }
        return List.of(buildClaimantEntry(reading));
    }

    // The one entry the claim section lists where it has one: whoever holds the system, or the plain
    // word for nobody when no eligible faction scored and no decree imposed one.
    private CellTooltipEntry buildClaimantEntry(HoveredClaimReading reading) {

        var sector = reading.sector();
        var contest = reading.contest();
        var breakdown = contest.breakdown();
        var claimantFactionId = breakdown.claimantFactionId();

        if (!KmlibStrings.hasText(claimantFactionId)) {
            return CellTooltipEntry.createEntry(CellTooltipEntryLine.createLine(
                CellTooltipMark.NO_MARK,
                KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_CLAIM_NONE),
                CellTooltipEntryLine.NO_SCORE));
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
                CellTooltipEntryLine.NO_SCORE));

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
                .map(found -> resolveAdmittedAccountEntries(reading, found))
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

        var standingLine = FactionTooltipLine.buildCountedFactionLine(
            sector,
            standing.factionId(),
            standing.score());

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

    // One block's entries over the standings routed into it, in the order the breakdown handed them
    // over - strongest first, which is the order a contest is read in and the same one the mechanic
    // itself settles it in. Each carries whatever this box accounts for it with, subordinated: an
    // account explains the line it hangs under rather than restating it more finely.
    private List<CellTooltipEntry> buildListedEntries(
            HoveredClaimReading reading,
            List<FactionClaimStanding> standings,
            boolean isStatingEligibilityOnLine) {

        var entries = new ArrayList<CellTooltipEntry>(standings.size());

        for (var standing : standings) {
            entries.add(CellTooltipEntry
                .createEntry(buildListedLine(
                    reading.sector(),
                    standing,
                    isStatingEligibilityOnLine))
                .nesting(resolveAdmittedAccountEntries(reading, standing)));
        }
        return entries;
    }

    // The account this paint hangs beneath one faction, and nothing at all where the level shows no
    // line of one - which spares the selecting, ranking and wording of the markets behind every
    // faction the box names. Asked here rather than per box, the answer being about the cut.
    private List<CellTooltipEntry> resolveAdmittedAccountEntries(
            HoveredClaimReading reading,
            FactionClaimStanding standing) {

        if (!reading.detailLevel().isAdmittingAccounts()) {
            return List.of();
        }
        return resolveAccountEntries(
            reading.contest(),
            standing,
            reading.colonyReading(),
            reading.detailLevel());
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
     * One reading of a hovered system's claim contest: the sector it was read against, what that read
     * came to, and what the box may say about the colonies behind it.
     *
     * <p>The four travel as one value because every block is drawn from all four and they are
     * settled together, once, before any block is appended. Threaded one by one instead, a block
     * added later could be handed a contest read here beside a colony reading taken somewhere else,
     * and the box would explain one reading of the system under another's.
     *
     * <p>The sector rides along rather than being reached for, so a box drawn over a second sector
     * names that sector's factions - and so the crest on a line and the standing beside it come from
     * the one sector between them.
     *
     * @param sector        the sector the hovered system stands in, whose factions the lines are named
     *                      from
     * @param contest       the whole scored read, the colony rule it was projected under, and the
     *                      standings that projection leaves the box free to name
     * @param colonyReading what the box may say about the system's colonies beyond their scores,
     *                      folded once off the same walk the status line was judged from
     * @param detailLevel   how deep the player asked this box to read, which is what decides whether
     *                      an account is worked out at all and how far into one it goes
     */
    private record HoveredClaimReading(
        SectorAPI sector,
        ListedClaimContest contest,
        SystemColonyReading colonyReading,
        HoverTooltipDetailLevel detailLevel) {
    }
}
