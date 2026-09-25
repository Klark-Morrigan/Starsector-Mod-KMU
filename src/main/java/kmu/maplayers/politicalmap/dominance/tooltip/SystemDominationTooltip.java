package kmu.maplayers.politicalmap.dominance.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.ClaimBreakdownReader;

import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;
import kmu.maplayers.base.tooltip.layout.CellTooltipBlocks;
import kmu.maplayers.base.tooltip.layout.CellTooltipBody;
import kmu.maplayers.base.tooltip.layout.ComposedCellBody;
import kmu.maplayers.ownermap.holding.ContestSides;
import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.ownermap.holding.HolderGroupingSource;
import kmu.maplayers.ownermap.tooltip.SystemColonyReading;
import kmu.maplayers.ownermap.tooltip.SystemStatusRow;
import kmu.maplayers.politicalmap.dominance.BlocCandidacy;
import kmu.maplayers.politicalmap.dominance.DominancePass;
import kmu.maplayers.politicalmap.dominance.standings.SystemStandings;
import kmu.maplayers.politicalmap.tooltip.ContestWordingSource;
import kmu.maplayers.politicalmap.tooltip.PoliticalMapCellTooltip;
import kmu.mods.nexerelin.NexerelinAlliances;
import kmu.mods.nexerelin.NexerelinContestWording;

import java.util.List;

/**
 * The domination breakdown a political-map layer shows for the hovered star system: what the system
 * is, then who dominates it, who stands with them by alliance, who stands with them in disposition,
 * who contests it, and who was never in the running - ranked under the injecting view's own grouping
 * and weighting, and beneath every faction named, the colonies it holds the system with and the
 * factors each colony's weight was summed from. The {@link MapHoverTooltip} the faction and alliance
 * views both inject, each holding one bound to its own grouping.
 *
 * <p>The ranking reads as a contest rather than as a list: the group the map fills the system in the
 * colour of is named as dominating it, so who holds the system is stated outright instead of being
 * inferred from which line sits at the top. Which group that is and where every other one is listed is
 * {@link StandingBlockRouting}'s answer, taken once per hover; why an ally or a friend of the holder is
 * lifted out of the rivals is {@link ContestSides}'. The headline stays on the group the map painted
 * the cell for rather than on its alliance, which is what keeps the box an explanation of the cell
 * beneath it.
 *
 * <p>The two-tier shape at the top is settled where the group's kind is known
 * ({@link StandingRowResolver}): a lone-faction group reads as one flat line, an alliance bloc as a
 * line over its member factions. The colonies hang under the faction flying them rather than under the
 * bloc, since a bloc's score is the sum over its members'.
 *
 * <p>The account beneath each faction is one tree read to whatever depth was asked for and composed
 * only as far as the cut draws it ({@link CellTooltipBody}), so the shallowest level pays for no colony
 * read at all. Its parts come from the very pass that ranked the groups
 * ({@link DominancePass#readWeightBreakdownsByFaction}), so the lines always add up to the number the
 * top line and the map's own fills show. Every colony line says what the box has found out about the
 * place beyond its weight ({@link SystemColonyReading}).
 *
 * <p>Stateless past the seams it is built around - the painting view's grouping, the live economy, the
 * alliance set and the settings are read afresh each paint. The grouping the ranking runs under is
 * handed over by the view that injects the box, so a box is always ranked under that view and no
 * second layer's pick can reach it.
 */
public final class SystemDominationTooltip extends PoliticalMapCellTooltip {

    // The grouping of the view whose fills this box explains, read afresh each paint because a
    // view's grouping can follow a live alliance set.
    private final HolderGroupingSource paintedGroupingSource;

    // What hangs beneath each listed faction as the account of its score, built once per paint and
    // only where the level admits an account at all.
    private final FactionAccountSource factionAccountSource;

    /**
     * @param paintedGroupingSource the grouping the injecting view paints its fills under, which the
     *                              ranking runs under so the box's numbers match the fills
     * @param claimBreakdownReader  the claim read every political box heads with
     * @param holderGroupingSource  the alliance grouping the contest's sides are read under
     * @param contestWordingSource  the wording of the rival block
     */
    public SystemDominationTooltip(
            HolderGroupingSource paintedGroupingSource,
            ClaimBreakdownReader claimBreakdownReader,
            HolderGroupingSource holderGroupingSource,
            ContestWordingSource contestWordingSource) {

        this(
            paintedGroupingSource,
            claimBreakdownReader,
            holderGroupingSource,
            contestWordingSource,
            SystemDominationTooltip::createFactionAccountResolver);
    }

    SystemDominationTooltip(
            HolderGroupingSource paintedGroupingSource,
            ClaimBreakdownReader claimBreakdownReader,
            HolderGroupingSource holderGroupingSource,
            ContestWordingSource contestWordingSource,
            FactionAccountSource factionAccountSource) {

        super(claimBreakdownReader, holderGroupingSource, contestWordingSource);
        this.paintedGroupingSource = paintedGroupingSource;
        this.factionAccountSource = factionAccountSource;
    }

    /**
     * The box one view injects, ranking under that view's own grouping.
     *
     * <p>This is where the alliance set behind the allied block is bound, and the wording of the rival
     * block beside it, both off the one mod that supplies them. Both are gates rather than branches:
     * each answers with the plain reading wherever that mod is absent, so the box states one shape and
     * the install decides what it comes to.
     *
     * <p>Deliberately not the active view's own grouping, which is what the map paints under: the
     * faction view pins that to identity so fills, runs and rows stay per faction, and reusing it
     * would leave the allied block permanently empty on the one layer that draws it.
     *
     * @param paintedGroupingSource the grouping the injecting view paints its fills under
     * @return the box that view shows for a hovered system
     */
    public static SystemDominationTooltip createPaintedBy(HolderGroupingSource paintedGroupingSource) {
        return new SystemDominationTooltip(
            paintedGroupingSource,
            VANILLA_CLAIM_BREAKDOWN_READER,
            NexerelinAlliances::resolveGrouping,
            NexerelinContestWording::resolveWording);
    }

    @Override
    public ComposedCellBody composeBody(
            SectorAPI sector,
            StarSystemAPI system,
            HoverTooltipDetailLevel detailLevel) {

        // One reading answers both halves: the blocks the box draws, and whether the key at its foot
        // has anything to offer over this system. Asked apart they would be two rankings of one
        // system per frame, and the hint could describe a contest the body does not draw.
        var ranking = readRankedStandings(sector, system);

        return new ComposedCellBody(
            buildBlocksFrom(sector, system, ranking, detailLevel),
            resolveDeepestHeldLevel(hasAnyStanding(ranking)));
    }

    @Override
    public HoverTooltipDetailLevel resolveDeepestAccountLevel() {
        // The colonies behind a faction, the factors behind a colony's weight, and the small, medium
        // and large split behind the patrol factor - which is the deepest tier the levels declare, so
        // this box fills the cycle out.
        return HoverTooltipDetailLevel.PATROL_DETAILS;
    }

    @Override
    public HoverTooltipDetailLevel resolveDeepestHeldLevelFor(
            SectorAPI sector,
            StarSystemAPI system) {

        // The press-time path, which composes nothing and so has to rank the system for itself. A
        // paint reaches the same judgement above, off the ranking it already holds.
        return resolveDeepestHeldLevel(hasAnyStanding(readRankedStandings(sector, system)));
    }

    // The account of every faction the box lists, read once for the whole box rather than per
    // faction: read per faction, two of them could be explained from different selections over the
    // system, and the walk behind them is the most expensive thing a hover does. The pass remembers
    // each system it walks, so the reads below cost one traversal between them.
    //
    // Taken off the pass rather than assembled here: the weighting rule and the visibility rule are
    // the pass's, and a box that named them itself could explain a system under a rule the map did
    // not paint it under.
    static FactionAccountResolver createFactionAccountResolver(
            StarSystemAPI system,
            DominancePass pass,
            HoverTooltipDetailLevel detailLevel) {

        var breakdownsByFactionId = pass.readWeightBreakdownsByFaction(system);

        // The colonies the pass could not weigh, selected beside the ones it did. They stay a
        // separate read rather than becoming a second kind of breakdown because the pass must see
        // only the markets it can weigh: a colony the economy does not list has nothing to weigh,
        // and one admitted there would hand its owner weight nobody worked out.
        var unweighedColoniesByFactionId = pass.readUnweighedColoniesByFaction(system);

        // What the box may say about each colony beyond its weight, settled once for the whole box
        // off the same walk the two reads above came from: what sort of place it is, whether the
        // player has found it, and - unless somebody is looking at it as the box is drawn - when it
        // was last seen.
        var colonyReading = SystemColonyReading.readColoniesIn(
            pass.sector(),
            system,
            pass.readColoniesIn(system),
            pass.colonyKnowledge());

        // A faction the reads found nothing for is listed as its line alone rather than as a heading
        // over an empty account, which is what an empty answer means to the shape above.
        var reading = new WeightAccountReading(pass.rules(), colonyReading, detailLevel);

        return standing -> MarketWeightRowResolver.resolveMarketRows(
            breakdownsByFactionId.getOrDefault(standing.factionId(), List.of()),
            unweighedColoniesByFactionId.getOrDefault(standing.factionId(), List.of()),
            reading);
    }

    // Whether the ranking found anybody the box may name - the one judgement behind both the hint at
    // the foot and the press that acts on it. Named once so the two entries cannot come to ask
    // different questions of one reading.
    //
    // Judged on the ranking rather than on the status line the body heads itself with, which answers
    // a different question of the same pass: the line says whether anybody runs the place, while the
    // standings name everyone the player may be told about. A system whose colonies are all collapsed
    // or derelict is headed Decivilised or Unpopulated and still ranks whoever holds them - and those
    // colonies are exactly what a deeper level opens up.
    private static boolean hasAnyStanding(RankedStandings ranking) {
        return ranking.routing().hasAnyStanding();
    }

    // The account this paint is to hang beneath its factions, and nothing at all where the level
    // shows no line of one - which spares the colony walk behind it, the most expensive read a hover
    // makes.
    private FactionAccountResolver createAdmittedAccountResolver(
            StarSystemAPI system,
            DominancePass pass,
            HoverTooltipDetailLevel detailLevel) {

        return detailLevel.isAdmittingAccounts()
            ? factionAccountSource.createFactionAccountResolver(system, pass, detailLevel)
            : FactionAccountResolver.NO_ACCOUNT;
    }

    // The hovered system as this layer reads it: the pass the injecting view paints under, the
    // groups that pass ranks the system into, and the block each of those groups is listed under.
    //
    // One read behind both the body and the key hint at its foot, for the reason ComposedCellBody
    // sets out: the hint offers an account of exactly the standings the body lists.
    //
    // The routing travels with it because it is settled over one sampling of the live relations
    // (PoliticalMapCellTooltip): a box routing its blocks one at a time could file a group as an ally
    // and the next block's read file it as a rival out of a single hover.
    private RankedStandings readRankedStandings(SectorAPI sector, StarSystemAPI system) {

        // Ranks the hovered system under the painting view's grouping and dominance rule - the same
        // the map paints under - so the tooltip's numbers and its bloc grouping match the fills
        // exactly.
        var pass = DominancePass.readFromLunaSettings(
            sector,
            paintedGroupingSource.resolveGrouping());

        return new RankedStandings(
            pass,
            StandingBlockRouting.routeRankedStandings(
                SystemStandings.rankByDominationScore(system, pass),
                buildBlockRules(sector, pass.grouping())));
    }

    // What the blocks are placed by, bound to this hover's sector and to the grouping the map painted
    // its fills by.
    //
    // The bar and the membership both come off that painting grouping: the ranking names its groups
    // by its bloc IDs, so a membership read off any other fold would answer about blocs the box never
    // listed. How a group stands with the holder is the one rule that does not - both halves are
    // sampled live off the shape every political box shares, being the axis the bands judge their
    // contest by, and the faction and claims layers deliberately paint under a grouping that is not
    // it.
    private StandingBlockRules buildBlockRules(SectorAPI sector, HolderGrouping paintingGrouping) {

        var blocRelations = sampleBlocRelations(sector);

        return new StandingBlockRules(
            BlocCandidacy.createForGrouping(paintingGrouping),
            blocRelations.affiliation(),
            blocRelations.friendliness(),
            paintingGrouping::resolveMemberFactionIds);
    }

    // The body itself, off the one reading: what the system is, then the contest over it.
    private CellTooltipBlocks buildBlocksFrom(
            SectorAPI sector,
            StarSystemAPI system,
            RankedStandings ranking,
            HoverTooltipDetailLevel detailLevel) {

        var pass = ranking.pass();
        var body = CellTooltipBody.openBody(detailLevel);

        // What the system is comes before who holds it, so the standings below read as a contest over
        // a known system. The status resolves under this pass's rule, the same filter the standings
        // were ranked through, so neither can admit a colony the other withholds.
        //
        // It answers a different question of that one rule, though: the line says whether people
        // live here, while the standings name everyone the player may be told about. A system whose
        // only market is a derelict is therefore headed "Unpopulated" over a list naming the
        // derelict - which is what both surfaces are for, rather than a disagreement between them.
        // Off the pass's own walk of the system - the very one the standings were ranked from - so
        // the line and the list beneath it are two readings of one traversal rather than two.
        body.appendBannerSection(SystemStatusRow.resolveStatusRow(
            pass.readColoniesIn(system),
            pass.colonyKnowledge()));

        // The account is settled once for the whole box, before any group is named, so a box reading
        // the economy to build one reads it once however many groups hold the system - and every
        // faction listed is explained from that one read rather than from a read of its own.
        var accountResolver = createAdmittedAccountResolver(system, pass, detailLevel);
        var grouping = pass.grouping();

        // Each block is named through the one resolver and under the pass's own grouping, so which
        // block a group falls in is the only thing that varies between them: a group reads the same
        // way whichever heading it ends up beneath.
        appendContestBlockSections(
            body,
            StandingBlock.values(),
            block -> StandingRowResolver.resolveRows(
                sector,
                ranking.routing().selectStandingsIn(block),
                grouping,
                accountResolver));

        return body.readBlocks();
    }

    /**
     * Builds what a box hangs beneath each faction it lists, as the account of where that faction's
     * score came from - the one part of the box that turns on what the account explains rather than
     * on how the contest is laid out.
     */
    @FunctionalInterface
    interface FactionAccountSource {

        /**
         * @param system      the star system under the cursor
         * @param pass        the weighting rule, colony rule, grouping and colony walk the ranking
         *                    resolved under, so an account reading further into the system reads it
         *                    under the same knobs and off the same walk rather than repeating it
         * @param detailLevel how deep the box has been asked to read, so an account carrying tiers of
         *                    its own works out only the ones that will be drawn
         * @return what to hang beneath each listed faction
         */
        FactionAccountResolver createFactionAccountResolver(
            StarSystemAPI system,
            DominancePass pass,
            HoverTooltipDetailLevel detailLevel);
    }

    /**
     * One reading of a hovered system's standings: the pass it was ranked under, and where the
     * groups it ranked are listed.
     *
     * <p>The two travel as one value because the box reads them against each other - the status
     * line and the accounts hanging under the groups come off the pass, the block each group falls
     * in off the routing. Passed apart, one read's routing could arrive beside another read's pass,
     * and the box would explain one reading of the system under another's rule.
     *
     * <p>The ranking itself is not carried beside them. Every group it found is placed in some
     * block, so the routing answers both what the box lists and what it has to list at all, and a
     * value holding the ranking as well would be holding one thing in two states.
     */
    private record RankedStandings(
        DominancePass pass,
        StandingBlockRouting routing) {
    }
}
