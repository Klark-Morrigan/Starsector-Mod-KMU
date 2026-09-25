package kmu.maplayers.politicalmap.claims.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.markets.colonies.SystemColonies;
import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.systems.claims.FactionClaimStanding;
import kmlib.starsector.systems.claims.VanillaClaimBreakdownReader;
import kmlib.starsector.systems.claims.WeighedClaimStanding;
import kmlib.text.KmlibStrings;

import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.base.tooltip.content.CellTooltipEntry;
import kmu.maplayers.base.tooltip.content.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.content.CellTooltipMark;
import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;
import kmu.maplayers.base.tooltip.layout.CellTooltipBody;
import kmu.maplayers.base.tooltip.layout.ComposedCellBody;
import kmu.maplayers.base.visibility.colonies.ColonyKnowledge;
import kmu.maplayers.base.visibility.colonies.ColonyVisibility;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.ownermap.holding.HolderGroupingSource;
import kmu.maplayers.ownermap.tooltip.ColonyObservationNotes;
import kmu.maplayers.ownermap.tooltip.FactionTooltipLine;
import kmu.maplayers.ownermap.tooltip.SystemColonyReading;
import kmu.maplayers.ownermap.tooltip.SystemStatusRow;
import kmu.maplayers.politicalmap.tooltip.ContestWordingSource;
import kmu.maplayers.politicalmap.tooltip.PoliticalMapCellTooltip;
import kmu.mods.nexerelin.NexerelinAlliances;
import kmu.mods.nexerelin.NexerelinContestWording;
import kmu.util.KmuStringKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * The claim contest a political-map layer shows for the hovered star system: what the system is, who
 * claims it, and the factions around the claimant in the blocks {@link ClaimContestBlock} declares -
 * each named with its crest and the standing the mechanic weighed it at, and beneath each the colonies
 * it holds the system with and the terms each colony's score was summed from. The one
 * {@link MapHoverTooltip} the claims view injects.
 *
 * <p>The claim mechanic publishes only a winner, so a fill on its own leaves the player guessing at a
 * border they cannot check. Naming the claimant beside the contest behind it is what turns the layer
 * from a colouring into something readable. Which standings are listed, and under which block, is
 * {@link ListedClaimContest}'s answer, taken once per hover - so the banner, the claim line, the blocks
 * and the key hint all state one read of the system.
 *
 * <p>The account beneath each faction is one tree read to whatever depth was asked for - the colonies a
 * tier down, their terms a tier below - and composed only as far as the cut draws it
 * ({@link CellTooltipBody}). Its lines are read from the very standing the number above them came out
 * of ({@link ClaimScoreRowResolver}), so they always add up to what the faction's line shows and the
 * map painted its fill by.
 *
 * <p>Every colony line says how old the box's news of it is ({@link ColonyObservationNotes}). That
 * reaches further here than on the domination side: this listing carries a concealed base the
 * mechanic skipped before scoring and a colony the economy does not list, both at nought, where when
 * it was last seen is the only thing the account has left to add.
 *
 * <p>Stateless past the seams it is built around, so one shared instance serves the layer.
 */
public final class SystemClaimTooltip extends PoliticalMapCellTooltip {

    /**
     * The one shared instance, explaining vanilla claims - the same mechanic the layer's fills are
     * resolved through, so the box and the cell under it can never name different claimants.
     *
     * <p>This is where the alliance set behind the allied block is bound, and the wording of the rival
     * block beside it, both off the one mod that supplies them. Both are gates rather than branches:
     * each answers with the plain reading wherever that mod is absent, so the box states one shape and
     * the install decides what it comes to.
     *
     * <p>Deliberately not the claims view's own grouping, which pins itself to identity so the fills
     * stay per claiming faction: that decision is about what the map paints, while the block needs the
     * live alliance set, and reusing the view's would leave it permanently empty on the one layer that
     * draws it.
     */
    public static final SystemClaimTooltip INSTANCE = new SystemClaimTooltip(
        VANILLA_CLAIM_BREAKDOWN_READER,
        SystemClaimTooltip::openBreakdownReaderUnder,
        ClaimScoreRowResolver::resolveMarketRows,
        NexerelinAlliances::resolveGrouping,
        NexerelinContestWording::resolveWording);

    // Opens the reader a hover's scored contest is read through, over that hover's sector and the
    // colony rule it sampled. Opened per hover rather than held, because this box lives for the whole
    // session while the rule is a setting the player may move between two hovers - and a claim
    // breakdown reports knowledge as a flag on a market rather than by leaving the market out, so a
    // stale rule would surface silently as a name the box should have withheld.
    private final BiFunction<SectorAPI, ColonyVisibility, ClaimBreakdownReader> breakdownReaderOpener;

    // What hangs beneath each listed faction as the account of its standing, asked only where the
    // level admits an account at all.
    private final BiFunction<FactionClaimStanding, ClaimAccountReading, List<CellTooltipEntry>>
        accountEntriesSource;

    /**
     * A box reading every hover's contest through {@code claimBreakdownReader} as handed, for a caller
     * holding a reader already opened over the walk it wants explained - a band baked off one pass,
     * say, whose box then states the very claim the band was drawn from.
     *
     * @param claimBreakdownReader the claim read the box heads with and lists from
     * @param holderGroupingSource the alliance grouping the contest's sides are read under
     * @param contestWordingSource the wording of the rival block
     */
    public SystemClaimTooltip(
            ClaimBreakdownReader claimBreakdownReader,
            HolderGroupingSource holderGroupingSource,
            ContestWordingSource contestWordingSource) {

        this(
            claimBreakdownReader,
            (sector, colonyVisibility) -> claimBreakdownReader,
            ClaimScoreRowResolver::resolveMarketRows,
            holderGroupingSource,
            contestWordingSource);
    }

    SystemClaimTooltip(
            ClaimBreakdownReader claimBreakdownReader,
            BiFunction<SectorAPI, ColonyVisibility, ClaimBreakdownReader> breakdownReaderOpener,
            BiFunction<FactionClaimStanding, ClaimAccountReading, List<CellTooltipEntry>> accountEntriesSource,
            HolderGroupingSource holderGroupingSource,
            ContestWordingSource contestWordingSource) {

        super(claimBreakdownReader, holderGroupingSource, contestWordingSource);
        this.breakdownReaderOpener = breakdownReaderOpener;
        this.accountEntriesSource = accountEntriesSource;
    }

    @Override
    public ComposedCellBody composeBody(
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
        // claim reader beneath deliberately holds no colony index - it is opened per hover, and one
        // that did would answer a later hover off the sector an earlier one saw - so there is no
        // shared walk here to take, and the cost is stated where it is paid.
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
            KmuStringKeys.get(KmuStringKeys.POLITICAL_MAP_TOOLTIP_SECTION_CLAIM),
            buildClaimEntries(reading, statusRow.isPresent()));

        // Everyone present other than the claimant, in the blocks their standing to it puts them in,
        // so the box reads as the holder and then the contest around it.
        appendContestBlockSections(
            body,
            ClaimContestBlock.values(),
            block -> buildListedEntries(
                reading,
                block.selectStandings(contest),
                block.isStatingEligibilityOnLine()));

        // How deep the box goes goes back beside the blocks, judged on the very contest they were
        // drawn from: a box listing nobody has nothing for any deeper level to account for, and
        // asking again would read the system a second time to settle what this one already knows.
        return new ComposedCellBody(
            body.readBlocks(),
            resolveDeepestHeldLevel(contest.hasListedStanding()));
    }

    @Override
    public boolean isStatingCoreClaimInBody() {
        // The claim block names the claimant and marks a decreed hold on that very line, and the
        // breakdown sets the claimant to the decreed faction wherever a decree exists - so a decree
        // this box could head with is a decree its body is about to state anyway.
        return true;
    }

    @Override
    public HoverTooltipDetailLevel resolveDeepestAccountLevel() {
        // The colonies behind a faction, and the terms behind a colony's score - and there the claim
        // account ends. Vanilla settles a claim on size, a garrison and how many colonies the faction
        // holds beside it; no patrol enters the arithmetic anywhere, so the level below has nothing to
        // show and the cycle collapses from here instead of offering it.
        return HoverTooltipDetailLevel.MARKET_STATS;
    }

    @Override
    public HoverTooltipDetailLevel resolveDeepestHeldLevelFor(
            SectorAPI sector,
            StarSystemAPI system) {

        // The deeper tiers account for the colonies behind the factions this box lists, so a box
        // listing none has nothing for them to account for: every level would state the same claim
        // line and the key would do nothing the player could see.
        return resolveDeepestHeldLevel(readListedContest(sector, system).hasListedStanding());
    }

    // The vanilla breakdown over one hover's sector, under the colony rule that hover sampled. The
    // reader holds no colony index, so there is no snapshot of the sector to go stale between hovers:
    // a reader that walks afresh is what a surface with no pass behind it must have.
    static ClaimBreakdownReader openBreakdownReaderUnder(
            SectorAPI sector,
            ColonyVisibility colonyVisibility) {

        return new VanillaClaimBreakdownReader(ColonyKnowledge.over(sector, colonyVisibility));
    }

    // What hangs beneath one listed faction as the account of where its standing came from.
    //
    // The colony rule is taken off the contest rather than read afresh, so the account withholds
    // exactly what the listing above it withheld - one read of the player's settings serves the whole
    // box, however many factions it lists. The scored read travels with it for the same reason: who
    // the claim holder is and which listing ties decided something are facts of the contest, not of
    // one faction's list.
    List<CellTooltipEntry> resolveAccountEntries(
            ListedClaimContest contest,
            FactionClaimStanding standing,
            SystemColonyReading colonyReading,
            HoverTooltipDetailLevel detailLevel) {

        return accountEntriesSource.apply(
            standing,
            new ClaimAccountReading(
                contest.breakdown(),
                colonyReading,
                contest.colonyVisibility().shouldIncludeUndiscoveredMarkets(),
                detailLevel));
    }

    // What this family may show of a colony - read live off the same settings the faction layer's
    // pass samples, so crossing between the two layers cannot make one call a system empty that the
    // other calls held. The whole rule rather than the reveal alone, so a reader taking one term of
    // it cannot be drawing under a rule the rest of the box is not.
    private static ColonyVisibility readColonyVisibility() {
        return MapVisibilityRules.readFromLunaSettings().colonyVisibility();
    }

    // One faction's line as its standing states it: its crest, its name, and what the contest weighed
    // its presence at - quiet where the contest never reached the faction at all.
    private static CellTooltipEntryLine buildStandingLine(
            SectorAPI sector,
            FactionClaimStanding standing) {

        return FactionTooltipLine.buildCountedFactionLine(
            sector,
            standing.factionId(),
            standing.score(),
            standing instanceof WeighedClaimStanding);
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
                KmuStringKeys.get(KmuStringKeys.POLITICAL_MAP_TOOLTIP_QUALIFIER_NON_TERRITORIAL));
        }
        return standingLine;
    }

    // The hovered system's contest as this box may state it: the whole scored read, the colony rule
    // it was projected under, the standings that projection leaves the box free to name, and the two
    // relations they are placed against.
    //
    // One read behind both the body and the key hint at its foot, for the reason ComposedCellBody
    // sets out: the hint offers an account of exactly the factions the body lists.
    //
    // The colony rule is sampled once here and serves both the breakdown and the listing projected
    // over it, so the reader and the list cannot answer under two readings of the player's settings.
    // Both relations travel with it because they are sampled together (PoliticalMapCellTooltip): every
    // block is routed against one reading of each.
    private ListedClaimContest readListedContest(SectorAPI sector, StarSystemAPI system) {

        var colonyVisibility = readColonyVisibility();
        var blocRelations = sampleBlocRelations(sector);

        return ListedClaimContest.selectFrom(
            breakdownReaderOpener.apply(sector, colonyVisibility).readBreakdown(system),
            colonyVisibility,
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
                KmuStringKeys.get(KmuStringKeys.POLITICAL_MAP_TOOLTIP_CLAIM_NONE),
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
        // a decreed hold does not erase the faction's presence. The narrower of the breakdown's two
        // decree questions - the claimant matching the decree rather than a decree merely existing -
        // so the marker states what its line shows.
        if (breakdown.isClaimedByDecree()) {
            claimantLine = claimantLine.qualifiedWith(
                KmuStringKeys.get(KmuStringKeys.POLITICAL_MAP_TOOLTIP_CORE_MARKER));
        }
        return CellTooltipEntry
            .createEntry(claimantLine)
            .nesting(standing
                .map(found -> resolveAdmittedAccountEntries(reading, found))
                .orElseGet(List::of));
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
    // faction the box names. Every faction the box names takes an account, including one the
    // mechanic weighed nothing for: its colonies are exactly what the player can read nowhere else.
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
