package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.ui.widgets.TooltipRow;

import kmu.maplayers.politicalmap.base.PoliticalMapViewRegistry;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.SystemStandings;

import java.util.ArrayList;
import java.util.List;

/**
 * The domination breakdown a political-map layer shows for the hovered star system: the groups holding
 * markets there strongest first, each a crest, a name, and a score matching the weights the map paints
 * its fills by. The one {@link MapHoverTooltip} the faction and alliance views both inject - it adapts
 * flat vs nested off the active view's grouping, so those two layers share one tooltip that varies its
 * content rather than each carrying its own.
 *
 * <p>The two-tier shape is data-driven off the resolved rows: a lone-faction group (the faction view)
 * renders as one flat header, while an alliance bloc renders its header above its indented member
 * factions - and a one-member alliance still nests, since the {@link StandingGroupRow#nestsMembers()}
 * flag keys on the group's kind, not its member count. A system that ranks empty is not skipped - it
 * says why it holds no standing under the system name {@link SystemCellTooltip} heads the box with, so
 * the hover reads as landing on a real but uninhabited system rather than on nothing.
 *
 * <p>Stateless - it reads the active view, the live economy, and the settings each paint - so one
 * shared instance serves both views.
 */
public final class SystemDominationTooltip extends SystemCellTooltip {

    /** The one shared instance; stateless, so both views inject it. */
    public static final SystemDominationTooltip INSTANCE = new SystemDominationTooltip();

    private SystemDominationTooltip() {
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
        return groupRows.isEmpty()
                ? buildEmptyStateRows(sector, system, pass)
                : buildRows(groupRows);
    }

    // The one line shown under the system name for a system with no ranked presence: the shared status
    // row saying why it holds no standing. It resolves under this pass's reveal, the same filter the
    // standings above were ranked through, so the system counts as empty here exactly when the ranking
    // found nothing to show.
    private static List<TooltipRow> buildEmptyStateRows(
            SectorAPI sector,
            StarSystemAPI system,
            DominancePass pass) {
                
        return SystemStatusRow.resolveStatusRow(
                sector,
                system,
                pass.shouldIncludeUndiscoveredMarkets())
                .map(List::of)
                .orElseGet(List::of);
    }

    // Flattens the two-tier group rows into the flat draw rows the box paints top to bottom: a bloc
    // header per group, and - only when the group nests its members - its member factions indented
    // beneath. A lone-faction group (the faction view) does not nest, so its one member adds nothing
    // the header does not already show; an alliance bloc nests even with a single member, so it always
    // draws its members beneath. Branching on the nests-members flag, not the member count, is what
    // keeps a one-member alliance a tree while the faction view stays flat.
    private static List<TooltipRow> buildRows(List<StandingGroupRow> groupRows) {
        var rows = new ArrayList<TooltipRow>();
        for (var group : groupRows) {
            rows.add(buildTopTierRow(
                    group.crestSpritePath(),
                    group.displayName(),
                    DominationScoreFormat.formatScore(group.aggregateScore())));
            if (group.nestsMembers()) {
                for (var member : group.members()) {
                    rows.add(buildNestedRow(
                            member.crestSpritePath(),
                            member.fullName(),
                            DominationScoreFormat.formatScore(member.score())));
                }
            }
        }
        return rows;
    }
}
