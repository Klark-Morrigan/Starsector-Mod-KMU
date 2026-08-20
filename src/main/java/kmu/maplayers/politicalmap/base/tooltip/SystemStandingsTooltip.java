package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.base.tooltip.CellTooltipSections;
import kmu.maplayers.politicalmap.base.PoliticalMapViewRegistry;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
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
        var activeView = PoliticalMapViewRegistry.getActiveView();
        if (activeView == null) {
            return List.of();
        }

        // Ranks the hovered system under the active view's grouping and dominance rule - the same the
        // map paints under - so the tooltip's numbers and its bloc grouping match the fills exactly.
        var pass = DominancePass.readFromLunaSettings(sector, activeView.resolveGrouping());
        var standings = SystemStandings.rankByDominationScore(system, pass);
        var sections = new ArrayList<TooltipSection>();

        // What the system is comes before who holds it, so the standings below read as a contest over
        // a known system. The status resolves under this pass's rule, the same filter the standings
        // were ranked through, so neither can admit a colony the other withholds.
        //
        // It answers a different question of that one rule, though: the line says whether people
        // live here, while the standings name everyone the player may be told about. A system whose
        // only market is a derelict is therefore headed "Unpopulated" over a list naming the
        // derelict - which is what both surfaces are for, rather than a disagreement between them.
        CellTooltipSections.appendBannerSection(
            sections,
            SystemStatusRow.resolveStatusRow(sector, system, pass.colonyVisibility()));

        // The account is settled once for the whole box, before any group is named, so a box reading
        // the economy to build one reads it once however many groups hold the system - and every
        // faction listed is explained from that one read rather than from a read of its own.
        appendStandingSections(sections, StandingRowResolver.resolveRows(
            sector,
            standings,
            pass.grouping(),
            createFactionAccountResolver(system, pass)));

        return sections;
    }

    @Override
    protected final Optional<String> resolveExpandedDetailName(
            SectorAPI sector,
            StarSystemAPI system) {

        var activeView = PoliticalMapViewRegistry.getActiveView();
        if (activeView == null) {
            return Optional.empty();
        }
        // The counterpart accounts for the colonies behind a score, so a system holding none has
        // nothing for it to account for: both boxes would state the same banner and the key would do
        // nothing the player could see. Asked of the very line the box says that emptiness with,
        // under the same pass, so it can never offer to expand a system it has just called
        // unpopulated - which reading the economy a second way here would eventually allow.
        var pass = DominancePass.readFromLunaSettings(sector, activeView.resolveGrouping());
        if (SystemStatusRow
                .resolveStatusRow(sector, system, pass.colonyVisibility())
                .isPresent()) {
            return Optional.empty();
        }
        // Answered for the pair at once rather than by each box, because it is the one thing they
        // agree on: the counterpart accounts for the very scores the ordinary box ranks by, so a
        // player switching either way is being offered the same account. Which direction the hint
        // reads follows from which of the two is being drawn, and is none of this class's business.
        return Optional.of(KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_DETAIL_CONTRIBUTIONS));
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
}
