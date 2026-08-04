package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.ui.widgets.TooltipRow;
import kmlib.text.KmlibNumbers;

import kmu.maplayers.base.tooltip.CellTooltipRows;
import kmu.maplayers.base.tooltip.CellTooltipSections;
import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.base.tooltip.SystemCellTooltip;
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
 * <p>The two-tier shape is data-driven off the resolved rows: a lone-faction group (the faction view)
 * renders as one flat header, while an alliance bloc renders its header above its indented member
 * factions - and a one-member alliance still nests, since the {@link StandingGroupRow#nestsMembers()}
 * flag keys on the group's kind, not its member count.
 *
 * <p>What the system is beyond its standings - dead or unpopulated, or held by decree as some
 * faction's core - is stated above the contest, so a player crossing between this layer and the claims
 * layer reads one fact one way. A decree is not merely first but heads the box: it is the only line
 * here that settles the system outright, so it is centred tight under the system name
 * {@link SystemCellTooltip} titles the box with and reads as part of that heading, with the box's one
 * parting falling beneath it. A system that ranks empty is not skipped: those lines are all it has, and
 * they say why it holds no standing, so the hover reads as landing on a real but unheld system rather
 * than on nothing.
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
    protected List<TooltipRow> buildTitleRows(SectorAPI sector, StarSystemAPI system) {
        // Whose space this is heads the box rather than sitting in it: a decree settles the system
        // outright, so it is read straight off the system name above it instead of being found among
        // the findings below. Being a title line is also what parts it from the status beneath - the
        // box's one break falls under it rather than above it.
        //
        // The decree is read on its own rather than out of the full claim breakdown: this box only has
        // to know whether one holds the system, and scoring every market in it to answer that would
        // charge the whole claim computation to every faction and alliance hover.
        //
        // No view is needed, unlike the ranking below: a decree is a fact about the system rather than
        // about how this layer happens to be grouping it, so it reads the same under either view.
        var rows = new ArrayList<TooltipRow>();

        CoreTerritoryRow
            .resolveCoreTerritoryRow(sector, claimBreakdownReader.readCoreFactionId(system))
            .ifPresent(rows::add);

        return rows;
    }

    @Override
    protected List<TooltipRow> buildBodyRows(SectorAPI sector, StarSystemAPI system) {
        var activeView = PoliticalMapViewRegistry.getActiveView();
        if (activeView == null) {
            return List.of();
        }

        // Ranks the hovered system under the active view's grouping and dominance rule - the same the
        // map paints under - so the tooltip's numbers and its bloc grouping match the fills exactly.
        var grouping = activeView.resolveGrouping();
        var pass = DominancePass.readFromLunaSettings(grouping);
        var standings = SystemStandings.rankByDominationScore(sector, system, pass);
        var groupRows = StandingRowResolver.resolveRows(sector, standings, grouping);
        var rows = new ArrayList<TooltipRow>();

        // What the system is comes before who holds it, so the standings below read as a contest over
        // a known system. The status resolves under this pass's reveal, the same filter the standings
        // were ranked through, so the system counts as empty here exactly when the ranking found
        // nothing to show - the two can never describe different systems.
        SystemStatusRow
            .resolveStatusRow(sector, system, pass.shouldIncludeUndiscoveredMarkets())
            .ifPresent(rows::add);

        appendStandingSections(rows, groupRows);
        return rows;
    }

    // The standings as the two blocks they are read in: whoever dominates the system, then whoever
    // else is present to contest it. Both are offered unconditionally - an uncontested system simply
    // has no rows for the second, and an unheld one none for either, so the heading that would have
    // stood over nothing is dropped rather than left to be read as a block that failed to fill.
    private static void appendStandingSections(
            List<TooltipRow> rows,
            List<StandingGroupRow> groupRows) {

        CellTooltipSections.appendSection(
            rows,
            KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_DOMINATED),
            buildGroupRows(groupRows
                .stream()
                .limit(DOMINATING_GROUP_COUNT)
                .toList()));

        CellTooltipSections.appendSection(
            rows,
            KmuStrings.get(KmuStrings.POLITICAL_MAP_TOOLTIP_SECTION_CONTESTED),
            buildGroupRows(groupRows
                .stream()
                .skip(DOMINATING_GROUP_COUNT)
                .toList()));
    }

    // Flattens the two-tier group rows into the flat draw rows the box paints top to bottom: a bloc
    // header per group, and - only when the group nests its members - its member factions indented
    // beneath. A lone-faction group (the faction view) does not nest, so its one member adds nothing
    // the header does not already show; an alliance bloc nests even with a single member, so it always
    // draws its members beneath. Branching on the nests-members flag, not the member count, is what
    // keeps a one-member alliance a tree while the faction view stays flat.
    private static List<TooltipRow> buildGroupRows(List<StandingGroupRow> groupRows) {
        var rows = new ArrayList<TooltipRow>();

        for (var group : groupRows) {

            rows.add(CellTooltipRows.buildTopTierRow(
                group.crestSpritePath(),
                group.displayName(),
                KmlibNumbers.formatGroupedInteger(group.aggregateScore())));

            if (group.nestsMembers()) {

                for (var member : group.members()) {
                    rows.add(CellTooltipRows.buildNestedRow(
                        member.crestSpritePath(),
                        member.fullName(),
                        KmlibNumbers.formatGroupedInteger(member.score())));
                }
            }
        }
        return rows;
    }
}
