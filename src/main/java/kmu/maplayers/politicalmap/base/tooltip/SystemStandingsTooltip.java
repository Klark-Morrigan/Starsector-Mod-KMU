package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.base.tooltip.CellTooltipSections;
import kmu.maplayers.politicalmap.base.PoliticalMapViewRegistry;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.GroupStanding;
import kmu.maplayers.politicalmap.base.dominance.SystemStandings;
import kmu.util.KmuStrings;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The shape every box built on a hovered system's standings takes: what the system is, then who
 * dominates it, then who else contests it - ranked under the active view's own grouping and
 * weighting.
 *
 * <p>The ranking reads as a contest rather than as a list: the group the map fills the system in
 * the colour of is named as dominating it and the rest as contesting it, so who holds the system
 * is stated outright instead of being left to be inferred from which line happens to sit at the
 * top.
 *
 * <p>What the system is beyond its standings - dead or unpopulated - is stated above the contest,
 * so a player crossing between this layer and the claims layer reads one fact one way. A system
 * that ranks empty is not skipped: that line is all it has, and it says why the system holds no
 * standing, so the hover reads as landing on a real but unheld system rather than on nothing. A
 * decree over the system is stated higher still, heading the box as it heads every one of this
 * layer's ({@link PoliticalMapCellTooltip}).
 *
 * <p>Everything above is settled here rather than per box because two boxes over one system have
 * to be two amounts of detail about the same contest, not two contests. The pass is read once,
 * from the view that painted the fills, the status is judged under that same pass's reveal, and
 * the groups are resolved into their lines by the one resolver - so a box stating more detail
 * cannot rank a system a shade differently, judge it populated where the other called it empty,
 * name a bloc by another crest, or answer a decree one way where the other answered it another.
 * What is left open is the one thing the detail is: what, if anything, a listed faction breaks down
 * into.
 *
 * <p>Stateless past the reader it is built around - the view, the live economy, and the settings
 * are read afresh each paint - so one shared instance per box serves every view that injects it.
 */
public abstract class SystemStandingsTooltip extends PoliticalMapCellTooltip {

    // How many of the ranked groups the box names as dominating the system: the one whose colour the
    // map fills it in. Everything ranked below that contests the system rather than holding it.
    private static final int DOMINATING_GROUP_COUNT = 1;

    protected SystemStandingsTooltip(ClaimBreakdownReader claimBreakdownReader) {
        super(claimBreakdownReader);
    }

    @Override
    protected final List<TooltipSection> buildBodySections(SectorAPI sector, StarSystemAPI system) {
        return readRankedStandings(sector, system)
            .map(ranking -> buildSectionsFrom(sector, system, ranking))
            .orElseGet(List::of);
    }

    @Override
    protected final Optional<String> resolveExpandedDetailName(
            SectorAPI sector,
            StarSystemAPI system) {

        // The counterpart accounts for the colonies behind the standings, so a system ranking none
        // has nothing for it to account for: both boxes would state the same banner and the key
        // would do nothing the player could see.
        //
        // Judged on the ranking rather than on the status line the body heads itself with, which
        // answers a different question of the same pass: the line says whether anybody runs the
        // place, while the standings name everyone the player may be told about. A system whose
        // colonies are all collapsed or derelict is headed Decivilised or Unpopulated and still
        // ranks whoever holds them - and those colonies are exactly what the counterpart opens up.
        //
        // Answered for the pair of boxes at once rather than by each, because it is the one thing
        // they agree on: the counterpart accounts for the very scores the ordinary box ranks by, so
        // a player switching either way is being offered the same account. Which direction the hint
        // reads follows from which of the two is being drawn, and is none of this class's business.
        return readRankedStandings(sector, system)
            .filter(RankedStandings::hasStanding)
            .map(ranking -> KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_DETAIL_CONTRIBUTIONS));
    }

    /**
     * Resolves what hangs beneath each faction the box lists, as the account of where that faction's
     * score came from. The seam the whole class exists around: which groups are listed, under which
     * heading, above what, and how each presents are all settled by the time this is called, so what
     * is left to answer is only whether a listed faction breaks down further and into what.
     *
     * <p>Asked once per paint rather than once per faction, so a box that has to read the economy to
     * account for a score reads it once for the whole box.
     *
     * <p>Listing a faction as the line naming it is the ordinary answer and the default, so a box
     * with nothing further to say overrides nothing.
     *
     * @param system the star system under the cursor
     * @param pass   the weighting rule, colony rule, grouping and colony walk the ranking resolved
     *               under, so a box reading further into the system reads it under the same knobs
     *               and off the same walk rather than repeating it
     * @return what to hang beneath each listed faction; {@link FactionAccountResolver#NO_ACCOUNT}
     *         leaves every one of them listed as its line alone
     */
    protected FactionAccountResolver createFactionAccountResolver(
            StarSystemAPI system,
            DominancePass pass) {

        return FactionAccountResolver.NO_ACCOUNT;
    }

    // The hovered system as this layer reads it: the pass the active view paints under, and the
    // groups that pass ranks the system into.
    //
    // One read behind both the body and the key hint at its foot, because the hint offers an account
    // of exactly the standings the body lists. Resolved apart, the two are free to be answered from
    // different readings of one system - and the shape that takes is a box advertising a key that
    // does nothing, or declining to over a system it has just listed somebody in.
    //
    // Empty where no view is painting: there is then no grouping to rank under, and no body being
    // drawn for a hint to sit beneath.
    private static Optional<RankedStandings> readRankedStandings(
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
            SystemStandings.rankByDominationScore(system, pass)));
    }

    // The body itself, off the one reading: what the system is, then the contest over it.
    private List<TooltipSection> buildSectionsFrom(
            SectorAPI sector,
            StarSystemAPI system,
            RankedStandings ranking) {

        var pass = ranking.pass();
        var sections = new ArrayList<TooltipSection>();

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
        CellTooltipSections.appendBannerSection(
            sections,
            SystemStatusRow.resolveStatusRow(
                pass.readColoniesIn(system),
                pass.colonyKnowledge()));

        // The account is settled once for the whole box, before any group is named, so a box reading
        // the economy to build one reads it once however many groups hold the system - and every
        // faction listed is explained from that one read rather than from a read of its own.
        appendStandingSections(sections, StandingRowResolver.resolveRows(
            sector,
            ranking.groupStandings(),
            pass.grouping(),
            createFactionAccountResolver(system, pass)));

        return sections;
    }

    // The standings as the two blocks they are read in: whoever dominates the system, then whoever
    // else is present to contest it. Both are offered unconditionally - an uncontested system simply
    // has no groups for the second, and an unheld one none for either, so the heading that would have
    // stood over nothing is dropped rather than left to be read as a block that failed to fill.
    private static void appendStandingSections(
            List<TooltipSection> sections,
            List<CellTooltipEntry> groupEntries) {

        CellTooltipSections.appendSection(
            sections,
            KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_DOMINATED),
            groupEntries
                .stream()
                .limit(DOMINATING_GROUP_COUNT)
                .toList());

        CellTooltipSections.appendSection(
            sections,
            KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_CONTESTED),
            groupEntries
                .stream()
                .skip(DOMINATING_GROUP_COUNT)
                .toList());
    }

    /**
     * One reading of a hovered system's standings: the pass it was ranked under, and the ranking
     * itself.
     *
     * <p>The two travel as one value because the box reads them against each other - the status
     * line and the accounts hanging under the groups come off the pass, the groups themselves off
     * the ranking. Passed apart, one read's ranking could arrive beside another read's pass, and the
     * box would explain one reading of the system under another's rule.
     */
    private record RankedStandings(
        DominancePass pass,
        List<GroupStanding> groupStandings) {

        /** Whether anybody stands in the system at all, which is what a box has to list. */
        boolean hasStanding() {
            return !groupStandings.isEmpty();
        }
    }
}
