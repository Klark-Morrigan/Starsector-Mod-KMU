package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.ClaimBreakdownReader;

import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;
import kmu.maplayers.base.tooltip.layout.CellTooltipBlocks;
import kmu.maplayers.base.tooltip.layout.CellTooltipBody;
import kmu.maplayers.base.tooltip.layout.ComposedCellBody;
import kmu.maplayers.politicalmap.base.PoliticalMapViewRegistry;
import kmu.maplayers.politicalmap.base.dominance.BlocCandidacy;
import kmu.maplayers.politicalmap.base.dominance.ContestSides;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderGroupingSource;
import kmu.maplayers.politicalmap.base.dominance.SystemStandings;
import kmu.util.KmuStrings;

import java.util.Optional;

/**
 * The shape every box built on a hovered system's standings takes: what the system is, then who
 * dominates it, then who stands with them by alliance, then who stands with them in disposition,
 * then who contests it, then who was never in the running - ranked under the active view's own
 * grouping and weighting.
 *
 * <p>The ranking reads as a contest rather than as a list: the group the map fills the system in
 * the colour of is named as dominating it and the rest as contesting it, so who holds the system
 * is stated outright instead of being left to be inferred from which line happens to sit at the
 * top.
 *
 * <p>Which group that is and where every other one is listed is {@link StandingBlockRouting}'s
 * answer, taken once per hover. A group taking no part in the contest - the placeholder owner of
 * every abandoned station - is set aside before a holder is picked, so the box never heads a system
 * with a bloc that has no interests to hold it with. A group standing in the holder's own alliance
 * is not one of the rivals either, and is lifted into a block of its own ({@link ContestSides}):
 * filed under the contested heading, two allies jointly holding a system would read as fighting each
 * other over it, which is the map contradicting itself one view over. Nor is a group on good terms
 * with the holder without standing in its alliance, that being the same fault a relation short of an
 * alliance produces - a bloc drawn beside the holder in friendly colours on no view at all, and
 * nonetheless reported as fighting it.
 *
 * <p>The headline stays on the group the map painted the cell for rather than on its alliance,
 * which is what keeps the box an explanation of the cell beneath it: two allies at 6,000 each under
 * a rival at 7,000 paint the rival, and a box headed by the alliance would answer a hover over a
 * cell in the rival's colours by naming somebody else. What is taken from the alliances layer is
 * the shape - a relation block between the holder and the rest - never its grouping, which would
 * merge allied runs, fills and rows.
 *
 * <p>What the system is beyond its standings - dead or unpopulated - is stated above the contest,
 * so a player crossing between this layer and the claims layer reads one fact one way. A system
 * that ranks empty is not skipped: that line is all it has, and it says why the system holds no
 * standing, so the hover reads as landing on a real but unheld system rather than on nothing. A
 * decree over the system is stated higher still, heading the box as it heads every one of this
 * layer's ({@link PoliticalMapCellTooltip}).
 *
 * <p>Everything above is settled here rather than per box, so a layer built on these standings states
 * one contest however deep it is read. The pass is read once, from the view that painted the fills,
 * the status is judged under that same pass's reveal, and the groups are resolved into their lines by
 * the one resolver - so no box on this shape can rank a system a shade differently from another,
 * judge it populated where the other called it empty, name a bloc by another crest, or answer a
 * decree one way where the other answered it another. What is left open is the one thing the detail
 * is: what, if anything, a listed faction breaks down into.
 *
 * <p>Stateless past the seams it is built around - the view, the live economy, the alliance set and
 * the settings are read afresh each paint - so one shared instance per box serves every view that
 * injects it.
 */
public abstract class SystemStandingsTooltip extends PoliticalMapCellTooltip {

    protected SystemStandingsTooltip(
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

        // One reading answers both halves: the blocks the box draws, and whether the key at its foot
        // has anything to offer over this system. Asked apart they would be two rankings of one
        // system per frame, and the hint could describe a contest the body no longer draws.
        return readRankedStandings(sector, system)
            .map(ranking -> new ComposedCellBody(
                buildBlocksFrom(sector, system, ranking, detailLevel),
                resolveDeepestHeldLevel(hasAnyStanding(ranking))))
            .orElse(ComposedCellBody.NOTHING);
    }

    @Override
    protected final HoverTooltipDetailLevel resolveDeepestHeldLevelFor(
            SectorAPI sector,
            StarSystemAPI system) {

        // The press-time path, which composes nothing and so has to rank the system for itself. A
        // paint reaches the same judgement above, off the ranking it already holds.
        return readRankedStandings(sector, system)
            .map(ranking -> resolveDeepestHeldLevel(hasAnyStanding(ranking)))
            .orElse(HoverTooltipDetailLevel.FACTIONS);
    }

    /**
     * Resolves what hangs beneath each faction the box lists, as the account of where that faction's
     * score came from. The seam the whole class exists around: which groups are listed, under which
     * heading, above what, and how each presents are all settled by the time this is called, so what
     * is left to answer is only whether a listed faction breaks down further and into what.
     *
     * <p>Asked once per paint rather than once per faction, so a box that has to read the economy to
     * account for a score reads it once for the whole box - and asked at all only where the level
     * admits an account, so the shallowest level costs that read nothing.
     *
     * <p>Answered by every box on this shape rather than defaulted: a box inheriting
     * {@link FactionAccountResolver#NO_ACCOUNT} would draw the same thing at every detail level
     * while the key went on offering to open it up, which is the one failure the level cycle cannot
     * show the player.
     *
     * @param system      the star system under the cursor
     * @param pass        the weighting rule, colony rule, grouping and colony walk the ranking
     *                    resolved under, so a box reading further into the system reads it under the
     *                    same knobs and off the same walk rather than repeating it
     * @param detailLevel how deep the box has been asked to read, so an account carrying tiers of
     *                    its own works out only the ones that will be drawn
     * @return what to hang beneath each listed faction; {@link FactionAccountResolver#NO_ACCOUNT}
     *         leaves every one of them listed as its line alone
     */
    protected abstract FactionAccountResolver createFactionAccountResolver(
        StarSystemAPI system,
        DominancePass pass,
        HoverTooltipDetailLevel detailLevel);

    // The account this paint is to hang beneath its factions, and nothing at all where the level
    // shows no line of one - which spares the colony walk behind it, the most expensive read a hover
    // makes. Asked once for the whole box rather than per box, the answer being about the cut.
    private FactionAccountResolver createAdmittedAccountResolver(
            StarSystemAPI system,
            DominancePass pass,
            HoverTooltipDetailLevel detailLevel) {

        return detailLevel.isAdmittingAccounts()
            ? createFactionAccountResolver(system, pass, detailLevel)
            : FactionAccountResolver.NO_ACCOUNT;
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

    // The hovered system as this layer reads it: the pass the active view paints under, the groups
    // that pass ranks the system into, and the block each of those groups is listed under. Empty
    // where no view is painting - there is then no grouping to rank under, and no body being drawn
    // for a hint to sit beneath.
    //
    // One read behind both the body and the key hint at its foot, for the reason ComposedCellBody
    // sets out: the hint offers an account of exactly the standings the body lists.
    //
    // The routing travels with it because it is settled over one sampling of the live relations
    // (PoliticalMapCellTooltip): a box routing its blocks one at a time could file a group as an ally
    // and the next block's read file it as a rival out of a single hover.
    private Optional<RankedStandings> readRankedStandings(
            SectorAPI sector,
            StarSystemAPI system) {

        var activeView = PoliticalMapViewRegistry.getActiveView();

        if (activeView == null) {
            return Optional.empty();
        }
        // Ranks the hovered system under the active view's grouping and dominance rule - the same the
        // map paints under - so the tooltip's numbers and its bloc grouping match the fills exactly.
        var pass = DominancePass.readFromLunaSettings(sector, activeView.resolveGrouping());

        return Optional.of(new RankedStandings(
            pass,
            StandingBlockRouting.routeRankedStandings(
                SystemStandings.rankByDominationScore(system, pass),
                buildBlockRules(sector, pass.grouping()))));
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
        // The wording is sampled once for the whole box, beside the account, so every block is headed
        // under one reading of the install - the same rule the relations behind the blocks follow.
        appendStandingSections(
            body,
            sector,
            ranking,
            createAdmittedAccountResolver(system, pass, detailLevel),
            resolveContestWording());

        return body.readBlocks();
    }

    // The standings as the blocks they are read in, laid down in the order those blocks are declared
    // in rather than in one restated here. Every block is offered unconditionally - an uncontested
    // system simply has no groups for the contested one, an install with nothing grouping factions
    // none for the allied one, and a system nobody political is present in none for the holder - so
    // a heading that would have stood over nothing is dropped rather than left to be read as a block
    // that failed to fill.
    //
    // Each block is named through the one resolver and under the pass's own grouping, so which
    // block a group falls in is the only thing that varies between them: a group reads the same way
    // whichever heading it ends up beneath.
    private static void appendStandingSections(
            CellTooltipBody body,
            SectorAPI sector,
            RankedStandings ranking,
            FactionAccountResolver accountResolver,
            ContestWording contestWording) {

        var grouping = ranking.pass().grouping();

        for (var block : StandingBlock.values()) {
            body.appendSection(
                KmuStrings.get(block.resolveHeadingKey(contestWording)),
                StandingRowResolver.resolveRows(
                    sector,
                    ranking.routing().selectStandingsIn(block),
                    grouping,
                    accountResolver));
        }
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
