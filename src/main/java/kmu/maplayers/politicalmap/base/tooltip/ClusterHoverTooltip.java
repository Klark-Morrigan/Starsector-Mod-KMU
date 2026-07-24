package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.listeners.CampaignUIRenderingListener;
import com.fs.starfarer.api.combat.ViewportAPI;

import kmlib.starsector.markets.DecivilisedMarkets;
import kmlib.starsector.systems.StarSystems;
import kmlib.starsector.ui.color.StarsectorUiColor;
import kmlib.starsector.ui.map.CampaignMapView;
import kmlib.starsector.ui.render.gl.CursorTooltipRenderer;
import kmlib.starsector.ui.render.gl.CursorTooltipStyle;
import kmlib.starsector.ui.widgets.TooltipRow;

import kmu.maplayers.politicalmap.base.PoliticalMapViewRegistry;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.SystemStandings;
import kmu.maplayers.politicalmap.base.hover.PoliticalMapHover;
import kmu.maplayers.politicalmap.base.hover.PoliticalMapHoverState;
import kmu.settings.KmuLunaSettings;
import kmu.util.KmuStrings;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws a small box at the cursor breaking down who dominates the hovered star system, ranked by the
 * active political-map view. It reads the hovered system the render pass published to
 * {@link PoliticalMapHoverState} and paints it a pass later, in the UI-coords above-tooltips layer -
 * the same layer the map sidebar draws in, the only one composited after the opaque core-UI map and
 * its tooltips.
 *
 * <p>The box is the feature's payload: for the hovered system it lists the groups holding markets
 * there strongest first, each a crest, a name, and a domination score matching the weights the map
 * paints its fills by. The two-tier shape is data-driven off the resolved rows - a singleton group
 * (the faction view, or a lone-member bloc) renders as one flat header, while a multi-member alliance
 * renders its bloc header above its indented member factions - so one draw path serves both views
 * without knowing which produced the rows.
 *
 * <p>This class owns only what is the political map's own - the map gate, the hover-state read, the
 * standings-to-rows build, the faction palette, and the LunaLib toggle. The generic free-floating
 * tooltip beneath it - sizing the box, following the cursor, and painting the crest-label-value rows -
 * is {@link CursorTooltipRenderer}, handed a plain row list and a look.
 *
 * <p>The pass is read-only over the hover state and consumes no input, so the vanilla star-system
 * tooltip keeps drawing alongside this box; the icon gate ({@link #shouldDrawTooltipFor}) steps the
 * box aside only directly over a star icon, where vanilla draws its own, so exactly one box ever
 * shows there while the highlight stays lit regardless.
 */
public final class ClusterHoverTooltip implements CampaignUIRenderingListener {
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

    @Override
    public void renderInUICoordsBelowUI(ViewportAPI viewport) {
        // Below the whole campaign UI - under the map screen. Nothing belongs here.
    }

    @Override
    public void renderInUICoordsAboveUIBelowTooltips(ViewportAPI viewport) {
        // Occluded by the opaque core-UI map, like the sidebar's same pass. The box draws above tooltips.
    }

    @Override
    public void renderInUICoordsAboveUIAndTooltips(ViewportAPI viewport) {
        // Root master switch: with the tooltip turned off in settings the box never draws, whatever
        // the map state or hover. Read live each frame so toggling it takes effect without a rebuild.
        if (!KmuLunaSettings.getPoliticalMapHoverTooltipEnabled()) {
            return;
        }
        // Only the sector map with the starscape filter off shows the overlay, so only then is a hover
        // meaningful; the same gate the sidebar uses.
        if (!CampaignMapView.isSectorMapWithStarscapeOff()) {
            return;
        }
        var hover = PoliticalMapHoverState.getInstance().getHover();
        if (!shouldDrawTooltipFor(hover)) {
            return;
        }
        var sector = Global.getSector();
        // The breakdown reads the live economy for each faction's footprint, so a sector without one
        // (never on the open campaign map, but guarded since the read assumes it) has nothing to rank.
        if (sector == null || sector.getEconomy() == null) {
            return;
        }
        // The hover carries a system id; resolve it to the live system, tolerating an id that no longer
        // resolves (a system dropped between the publish and this paint). Matched by getId - vanilla's
        // getStarSystem keys on the optional unique id first and would miss a base-name-keyed system.
        var system = StarSystems.findById(sector, hover.hoveredSystemId());
        if (system == null) {
            return;
        }
        drawTooltip(sector, system);
    }

    // Whether the box should draw for this hover: only when a cell is hovered and the cursor is not
    // on its star icon, where the vanilla star tooltip draws instead - so exactly one box ever
    // shows. The highlight ignores this gate and stays lit over the icon, since dropping it there
    // would flicker the territory off exactly when the player is pointing at its heart.
    static boolean shouldDrawTooltipFor(PoliticalMapHover hover) {
        return hover.isHovering() && !hover.isOverStarIcon();
    }

    // Ranks the hovered system under the active view's grouping, resolves the ranking into render-ready
    // rows, and hands them to the tooltip widget. The grouping and dominance rule are sampled from the
    // same active view and live settings the map paints under, so the tooltip's numbers and its bloc
    // grouping match the fills exactly. A system that ranks empty is not skipped - it draws an
    // empty-state box naming the system and why it holds no standing, so the hover reads as landing on
    // a real but empty system rather than on nothing.
    private static void drawTooltip(SectorAPI sector, StarSystemAPI system) {
        var activeView = PoliticalMapViewRegistry.getActiveView();
        if (activeView == null) {
            return;
        }
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
    // or score, so those columns fall empty and only the two lines of text show.
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
    // header per group, and - only for a multi-member bloc - its member factions indented beneath. A
    // singleton group is its own header (the faction view, and a lone-member alliance), so its one
    // member adds nothing the header does not already show; the member-count test is what keeps the
    // faction view flat off the same nested model.
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
            if (group.members().size() > 1) {
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
