package kmu.maplayers.politicalmap.dominance.tooltip;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.logging.SessionWarning;

import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;
import kmu.maplayers.base.tooltip.layout.CellTooltipBlocks;
import kmu.maplayers.base.tooltip.layout.CellTooltipBody;
import kmu.maplayers.base.tooltip.layout.ComposedCellBody;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderGroupingSource;
import kmu.maplayers.politicalmap.dominance.BlocCandidacy;
import kmu.maplayers.politicalmap.dominance.DominancePass;
import kmu.maplayers.politicalmap.dominance.standings.GroupStanding;
import kmu.maplayers.politicalmap.dominance.standings.SystemStandings;
import kmu.maplayers.politicalmap.dominance.weighting.KnownMarketFootprints;
import kmu.maplayers.politicalmap.dominance.weighting.MarketFootprint;
import kmu.maplayers.politicalmap.dominance.weighting.MarketWeightBreakdown;
import kmu.maplayers.politicalmap.tooltip.PoliticalBoxSeams;
import kmu.maplayers.politicalmap.tooltip.PoliticalMapCellTooltip;
import kmu.maplayers.politicalmap.tooltip.SystemStatusRow;

import org.apache.log4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * The {@link MapHoverTooltip} the faction and alliance views inject: the ranked contest behind a held
 * fill, and beneath each faction the colonies and factors its score was summed from.
 *
 * <p>One box per view, each bound to the grouping that view paints under, so the standings are
 * ranked under the view whose fills they explain and no second layer's pick can reach them. The
 * headline names the group the cell is painted for rather than its alliance, which keeps the box an
 * explanation of the cell beneath it.
 *
 * <p>Stateless past the seams it is built around and one warning it says once a session: the
 * grouping, the live economy, the alliance set and the settings are read afresh each paint. How the
 * box is composed and cut, and what each block holds, is <a href="README.md">the domination box</a>'s
 * register.
 */
public final class SystemDominationTooltip
        extends PoliticalMapCellTooltip {

    private static final Logger LOG = Global.getLogger(SystemDominationTooltip.class);

    // Said once per session: the box is composed on every cell the cursor reaches, so a ranking that
    // keeps parting from the fill would otherwise say so on every hover of every such system.
    private final SessionWarning unrankedPaintedBlocWarning = new SessionWarning(LOG);

    // The grouping of the view whose fills this box explains, read afresh each paint because a
    // view's grouping can follow a live alliance set.
    private final HolderGroupingSource paintedGroupingSource;

    // What hangs beneath each listed faction as the account of its score, built once per paint and
    // only where the level admits an account at all.
    private final FactionAccountSource factionAccountSource;

    /**
     * @param paintedGroupingSource the grouping the injecting view paints its fills under, which the
     *                              ranking runs under so the box's numbers match the fills
     * @param seams                 the live reads every political box is built around
     */
    public SystemDominationTooltip(
            HolderGroupingSource paintedGroupingSource,
            PoliticalBoxSeams seams) {

        this(paintedGroupingSource, seams, new PassFactionAccountSource());
    }

    SystemDominationTooltip(
            HolderGroupingSource paintedGroupingSource,
            PoliticalBoxSeams seams,
            FactionAccountSource factionAccountSource) {

        super(seams);

        this.paintedGroupingSource = paintedGroupingSource;
        this.factionAccountSource = factionAccountSource;
    }

    /**
     * The box one view injects, ranking under that view's own grouping.
     *
     * <p>The alliance set behind the allied block is the live one, not the active view's own
     * grouping, which is what the map paints under: the faction view pins that to identity so fills,
     * runs and rows stay per faction, and reusing it would leave the allied block permanently empty on
     * the one layer that draws it.
     *
     * @param paintedGroupingSource the grouping the injecting view paints its fills under
     * @return the box that view shows for a hovered system
     */
    public static SystemDominationTooltip createPaintedBy(HolderGroupingSource paintedGroupingSource) {
        return new SystemDominationTooltip(paintedGroupingSource, PoliticalBoxSeams.LIVE);
    }

    @Override
    public ComposedCellBody composeBody(
            SectorAPI sector,
            StarSystemAPI system,
            HoverTooltipDetailLevel detailLevel) {

        // One reading answers both halves: the blocks the box draws, and whether the key at its foot
        // has anything to offer over this system - which the press reads off this same composition.
        // Asked apart they would be two rankings of one system, and the hint could describe a
        // contest the body does not draw.
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

    // Whether the ranking found anybody the box may name - the one judgement behind both the hint at
    // the foot and the press that acts on it. Named once so the two entries cannot come to ask
    // different questions of one reading.
    //
    // Judged on the ranking rather than on the status line the body heads itself with, which answers
    // a different question of the same pass (SystemStatusRow states which). A system whose colonies
    // are all collapsed or derelict still ranks whoever holds them - and those colonies are exactly
    // what a deeper level opens up.
    private static boolean hasAnyStanding(RankedStandings ranking) {

        return ranking
            .routing()
            .hasAnyStanding();
    }

    // The account this paint is to hang beneath its factions, and nothing at all where the level
    // shows no line of one - which spares the colony walk behind it, the most expensive read a hover
    // makes.
    private FactionAccountResolver createAdmittedAccountResolver(
            StarSystemAPI system,
            RankedStandings ranking,
            HoverTooltipDetailLevel detailLevel) {

        return detailLevel.isAdmittingAccounts()
            ? factionAccountSource.createFactionAccountResolver(
                system,
                ranking.pass(),
                ranking.breakdownsByFactionId(),
                detailLevel)
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

        // Every market weighed once for the box: the parts an account lists, with the totals the
        // ranking compares folded out of those same parts. Read whatever the level, since the
        // totals cost the same weighing either way and only the parts are kept besides.
        var breakdownsByFactionId = pass.readWeightBreakdownsByFaction(system);

        // Folded once, for both questions asked of the totals: how the groups rank, and who holds
        // the system.
        var footprintByFactionId = KnownMarketFootprints.foldFootprintsByFaction(breakdownsByFactionId);

        var groupStandings = SystemStandings.rankByDominationScore(
            system,
            pass,
            footprintByFactionId);
        var paintedBlocId = resolvePaintedBlocId(system, pass, footprintByFactionId);

        warnOnUnrankedPaintedBloc(system, paintedBlocId, groupStandings);

        return new RankedStandings(
            pass,
            StandingBlockRouting.routeRankedStandings(
                groupStandings,
                paintedBlocId,
                buildBlockRules(sector, pass.grouping())),
            breakdownsByFactionId);
    }

    // The ranking and the fill are folded from one set of footprints under one grouping, so the bloc
    // the fill was painted for is always among the ranked groups. Where it is not, the two read the
    // system apart - the very fault that would name one holder in the box and paint another - and the
    // routing falls back to naming nobody. Said rather than left to that fallback, which on its own
    // looks like a system nobody holds.
    private void warnOnUnrankedPaintedBloc(
            StarSystemAPI system,
            String paintedBlocId,
            List<GroupStanding> groupStandings) {

        if (paintedBlocId == null) {
            return;
        }

        var isRanked = groupStandings
            .stream()
            .anyMatch(standing -> standing.blocId().equals(paintedBlocId));

        if (!isRanked) {
            unrankedPaintedBlocWarning.warnOnce("Domination box: the fill of system " + system.getId()
                + " was painted for bloc " + paintedBlocId
                + ", which its ranking does not list; the box names nobody as holding it");
        }
    }

    // The bloc the fill beneath the box was painted for, settled by the pass's own rule rather than by
    // the ranking. The ranking orders blocs on equal combined weight by ID, while the fill breaks that
    // tie on the heaviest market, planet weight and proximity first - so a holder read off the ranking
    // could name a bloc the cell is not painted for.
    private static String resolvePaintedBlocId(
            StarSystemAPI system,
            DominancePass pass,
            Map<String, MarketFootprint> footprintByFactionId) {

        return pass.resolveDominantBlocId(
            system,
            pass.regroupFootprintsByBloc(footprintByFactionId));
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

        return new StandingBlockRules(
            BlocCandidacy.createForGrouping(paintingGrouping),
            sampleBlocRelations(sector),
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
        // It answers a different question of that one rule, though, which SystemStatusRow states.
        // Off the pass's own walk of the system - the very one the standings were ranked from - so
        // the line and the list beneath it are two readings of one traversal rather than two.
        body.appendBannerSection(SystemStatusRow.resolveStatusRow(
            pass.readColoniesIn(system),
            pass.colonyKnowledge()));

        // The account is settled once for the whole box, before any group is named, so a box reading
        // the economy to build one reads it once however many groups hold the system - and every
        // faction listed is explained from that one read rather than from a read of its own.
        var rowResolver = StandingRowResolver.createForBox(
            sector,
            pass.grouping(),
            createAdmittedAccountResolver(system, ranking, detailLevel));

        // Each block is named through the one resolver and under the pass's own grouping, so which
        // block a group falls in is the only thing that varies between them: a group reads the same
        // way whichever heading it ends up beneath.
        appendContestBlockSections(
            body,
            StandingBlock.values(),
            block -> rowResolver.resolveRows(ranking.routing().selectStandingsIn(block)));

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
         * @param system                the star system under the cursor
         * @param pass                  the weighting rule, colony rule, grouping and colony walk the
         *                              ranking resolved under, so an account reading further into the
         *                              system reads it under the same knobs and off the same walk
         *                              rather than repeating it
         * @param breakdownsByFactionId each faction's weighed markets as the ranking weighed them, so
         *                              the account explains the very scores it ranked
         * @param detailLevel           how deep the box has been asked to read, so an account carrying
         *                              tiers of its own works out only the ones that will be drawn
         * @return what to hang beneath each listed faction
         */
        FactionAccountResolver createFactionAccountResolver(
            StarSystemAPI system,
            DominancePass pass,
            Map<String, List<MarketWeightBreakdown>> breakdownsByFactionId,
            HoverTooltipDetailLevel detailLevel);
    }

    /**
     * One reading of a hovered system's standings: the pass it was ranked under, where the groups it
     * ranked are listed, and the weights the ranking was summed from.
     *
     * <p>The three travel as one value because the box reads them against each other - the status
     * line and the accounts hanging under the groups come off the pass, the block each group falls
     * in off the routing, and each account's parts off the weights. Passed apart, one read's routing
     * could arrive beside another read's pass, and the box would explain one reading of the system
     * under another's rule.
     *
     * <p>The ranking itself is not carried beside them. Every group it found is placed in some
     * block, so the routing answers both what the box lists and what it has to list at all, and a
     * value holding the ranking as well would be holding one thing in two states.
     *
     * @param pass                  the pass the system was ranked under
     * @param routing               which block each ranked group is listed under
     * @param breakdownsByFactionId each faction's weighed markets, which the ranking's totals were
     *                              folded from
     */
    private record RankedStandings(
        DominancePass pass,
        StandingBlockRouting routing,
        Map<String, List<MarketWeightBreakdown>> breakdownsByFactionId) {
    }
}
