package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.base.tooltip.CellTooltipSections;
import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.politicalmap.base.PoliticalMapViewRegistry;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.SystemStandings;
import kmu.util.KmuStrings;

import java.util.ArrayList;
import java.util.List;

/**
 * The domination breakdown a political-map layer shows for the hovered star system: the groups holding
 * markets there strongest first, each a crest, a name, and a score matching the weights the map paints
 * its fills by. The one {@link MapHoverTooltip} the faction and alliance views both inject - it adapts
 * flat vs nested off the active view's grouping, so those two layers share one tooltip that varies its
 * content rather than each carrying its own.
 *
 * <p>The ranking reads as a contest rather than as a list: the group the map fills the system in the
 * colour of is named as dominating it and the rest as contesting it, so who holds the system is stated
 * outright instead of being left to be inferred from which line happens to sit at the top.
 *
 * <p>The two-tier shape is settled where the group's kind is known ({@link StandingRowResolver}) rather
 * than here: a lone-faction group (the faction view) resolves to an entry made up of nothing and reads
 * as one flat line, while an alliance bloc resolves to one carrying its member factions and reads as a
 * line over them, however few it holds. This box states only which groups it lists and under which
 * heading.
 *
 * <p>What the system is beyond its standings - dead or unpopulated - is stated above the contest, so a
 * player crossing between this layer and the claims layer reads one fact one way. A system that ranks
 * empty is not skipped: that line is all it has, and it says why the system holds no standing, so the
 * hover reads as landing on a real but unheld system rather than on nothing. A decree over the system
 * is stated higher still, heading the box as it heads every one of this layer's
 * ({@link PoliticalMapCellTooltip}).
 *
 * <p>Stateless past the reader it is built around - the view, the live economy, and the settings are
 * read afresh each paint - so one shared instance serves both views.
 */
public final class SystemDominationTooltip extends PoliticalMapCellTooltip {

    // How many of the ranked groups the box names as dominating the system: the one whose colour the
    // map fills it in. Everything ranked below that contests the system rather than holding it.
    private static final int DOMINATING_GROUP_COUNT = 1;

    /** The one shared instance; stateless, so both views inject it. */
    public static final SystemDominationTooltip INSTANCE =
        new SystemDominationTooltip(VANILLA_CLAIM_BREAKDOWN_READER);

    SystemDominationTooltip(ClaimBreakdownReader claimBreakdownReader) {
        super(claimBreakdownReader);
    }

    @Override
    protected List<TooltipSection> buildBodySections(SectorAPI sector, StarSystemAPI system) {
        var activeView = PoliticalMapViewRegistry.getActiveView();
        if (activeView == null) {
            return List.of();
        }

        // Ranks the hovered system under the active view's grouping and dominance rule - the same the
        // map paints under - so the tooltip's numbers and its bloc grouping match the fills exactly.
        var grouping = activeView.resolveGrouping();
        var pass = DominancePass.readFromLunaSettings(grouping);
        var standings = SystemStandings.rankByDominationScore(sector, system, pass);
        var groupEntries = StandingRowResolver.resolveRows(sector, standings, grouping);
        var sections = new ArrayList<TooltipSection>();

        // What the system is comes before who holds it, so the standings below read as a contest over
        // a known system. The status resolves under this pass's reveal, the same filter the standings
        // were ranked through, so the system counts as empty here exactly when the ranking found
        // nothing to show - the two can never describe different systems.
        CellTooltipSections.appendBannerSection(
            sections,
            SystemStatusRow.resolveStatusRow(sector, system, pass.shouldIncludeUndiscoveredMarkets()));

        appendStandingSections(sections, groupEntries);
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
}
