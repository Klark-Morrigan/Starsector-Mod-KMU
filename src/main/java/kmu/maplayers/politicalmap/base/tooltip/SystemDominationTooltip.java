package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.markets.DecivilisedMarkets;
import kmlib.starsector.ui.color.StarsectorUiColor;
import kmlib.starsector.ui.render.gl.CursorTooltipRenderer;
import kmlib.starsector.ui.render.gl.CursorTooltipStyle;
import kmlib.starsector.ui.widgets.TooltipRow;

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
 * <p>The two-tier shape is data-driven off the resolved rows: a lone-faction group (the faction view)
 * renders as one flat header, while an alliance bloc renders its header above its indented member
 * factions - and a one-member alliance still nests, since the {@link StandingGroupRow#nestsMembers()}
 * flag keys on the group's kind, not its member count. A system that ranks empty is not skipped - it
 * draws an empty-state box naming the system and why it holds no standing, so the hover reads as
 * landing on a real but uninhabited system rather than on nothing.
 *
 * <p>Stateless - it reads the active view, the live economy, and the settings each paint - so one
 * shared instance serves both views.
 */
public final class SystemDominationTooltip implements MapHoverTooltip {

    /** The one shared instance; stateless, so both views inject it. */
    public static final SystemDominationTooltip INSTANCE = new SystemDominationTooltip();

    // The insignia body face, a graphics/fonts basename the font cache resolves to a loadable path,
    // and the size every row's text renders at - which is also each row's line height for the box fit.
    private static final String BODY_FONT = "insignia15LTaa";
    private static final double FONT_SIZE = 15d;

    // A member row indents under its bloc header to read as nested; a top-tier header sits flush at
    // zero indent.
    private static final float MEMBER_INDENT = 14f;

    // The box's own look, handed to the tooltip widget as its style: a thin bright frame over a near
    // opaque black fill, so the breakdown reads over the map without blocking it entirely.
    private static final float BORDER_WIDTH = 1f;
    private static final float OPACITY = 0.9f;

    // The score for a row that carries no number - the empty-state lines naming a system and its
    // status. Rendered as-is it draws nothing and measures zero width, so the score column collapses.
    private static final String NO_SCORE = "";

    private SystemDominationTooltip() {
    }

    @Override
    public void renderFor(SectorAPI sector, StarSystemAPI system) {
        // The breakdown reads the live economy for each faction's footprint, so a sector without one
        // (never on the open campaign map, but guarded since the read assumes it) has nothing to rank.
        if (sector.getEconomy() == null) {
            return;
        }
        var activeView = PoliticalMapViewRegistry.getActiveView();
        if (activeView == null) {
            return;
        }
        // Ranks the hovered system under the active view's grouping and dominance rule - the same the
        // map paints under - so the tooltip's numbers and its bloc grouping match the fills exactly.
        var grouping = activeView.resolveGrouping();
        var pass = DominancePass.readFromLunaSettings(grouping);
        var standings = SystemStandings.rankByDominationScore(sector, system, pass);
        var groupRows = StandingRowResolver.resolveRows(sector, standings, grouping);
        var rows = groupRows.isEmpty() ? buildEmptyStateRows(system) : buildRows(groupRows);
        CursorTooltipRenderer.render(rows, buildStyle());
    }

    // The tooltip's fixed look: the body font and size every row draws in, the shared opacity, and the
    // frame over a black fill in the map's own player palette. Built per paint so its colours resolve
    // live rather than being baked at class load.
    private static CursorTooltipStyle buildStyle() {
        return new CursorTooltipStyle(
                BODY_FONT,
                FONT_SIZE,
                OPACITY,
                BORDER_WIDTH,
                StarsectorUiColor.BLACK.resolve(),
                StarsectorUiColor.VANILLA_PLAYER_BASE.resolve());
    }

    // Builds the two-line box shown over a system with no ranked presence: the system's own name as the
    // header, and under it why it holds no standing - a dead colony the player has already seen reads
    // "Decivilised", any other empty system "Unpopulated". Naming the empty system tells the player the
    // hover registered on a real but uninhabited system, not that it missed. The status carries no crest
    // or score, so an all-crestless box lays these two lines flush with no crest gutter.
    private static List<TooltipRow> buildEmptyStateRows(StarSystemAPI system) {
        var statusKey = DecivilisedMarkets.hasRevealedDecivilisedPlanet(system)
                ? KmuStrings.POLITICAL_MAP_TOOLTIP_DECIVILISED
                : KmuStrings.POLITICAL_MAP_TOOLTIP_UNPOPULATED;
        return List.of(
                new TooltipRow(
                        0f,
                        null,
                        system.getName(),
                        StarsectorUiColor.VANILLA_PLAYER_BRIGHT.resolve(),
                        NO_SCORE,
                        StarsectorUiColor.VANILLA_TEXT.resolve()),
                new TooltipRow(
                        MEMBER_INDENT,
                        null,
                        KmuStrings.get(statusKey),
                        StarsectorUiColor.VANILLA_TEXT.resolve(),
                        NO_SCORE,
                        StarsectorUiColor.VANILLA_TEXT.resolve()));
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
            rows.add(new TooltipRow(
                    0f,
                    group.crestSpritePath(),
                    group.displayName(),
                    StarsectorUiColor.VANILLA_PLAYER_BRIGHT.resolve(),
                    DominationScoreFormat.formatScore(group.aggregateScore()),
                    StarsectorUiColor.VANILLA_HIGHLIGHT_GOLD.resolve()));
            if (group.nestsMembers()) {
                for (var member : group.members()) {
                    rows.add(new TooltipRow(
                            MEMBER_INDENT,
                            member.crestSpritePath(),
                            member.fullName(),
                            StarsectorUiColor.VANILLA_TEXT.resolve(),
                            DominationScoreFormat.formatScore(member.score()),
                            StarsectorUiColor.VANILLA_TEXT.resolve()));
                }
            }
        }
        return rows;
    }
}
