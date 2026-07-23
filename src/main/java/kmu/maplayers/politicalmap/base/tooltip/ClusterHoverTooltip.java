package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.listeners.CampaignUIRenderingListener;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.util.Misc;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.systems.StarSystems;
import kmlib.starsector.ui.font.LazyFontCache;
import kmlib.starsector.ui.font.LazyFontMeasurer;
import kmlib.starsector.ui.input.UiCursor;
import kmlib.starsector.ui.layout.TooltipBoxLayout;
import kmlib.starsector.ui.map.CampaignMapView;
import kmlib.starsector.ui.render.gl.BorderedBoxRenderer;
import kmlib.starsector.ui.render.gl.GlStateGuard;
import kmlib.starsector.ui.render.gl.LabelRenderer;

import kmu.maplayers.politicalmap.base.hover.PoliticalMapHover;
import kmu.maplayers.politicalmap.base.hover.PoliticalMapHoverState;
import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.maplayers.politicalmap.base.politics.SectorPolitics;
import kmu.settings.KmuLunaSettings;

import org.lazywizard.lazylib.ui.LazyFont;

import java.awt.Color;

/**
 * Draws a small box at the cursor naming the hovered star system while the political overlay is up. It
 * reads the hovered system the render pass published to {@link PoliticalMapHoverState} and paints it a
 * pass later, in the UI-coords above-tooltips layer - the same layer the map sidebar draws in, the only
 * one composited after the opaque core-UI map and its tooltips.
 *
 * <p>The pass is read-only over the hover state and consumes no input, so the vanilla star-system tooltip
 * keeps drawing alongside this box when the cursor is on a star icon - both surfaces coexist rather than
 * one blocking the other. This is the minimal surface: just the system name and, when the system is owned,
 * its dominant faction. The ranked domination breakdown is layered on later; this proves the box draws,
 * follows the cursor, clears off the territory, and coexists with the vanilla tooltip.
 */
public final class ClusterHoverTooltip implements CampaignUIRenderingListener {
    // The insignia body face, a graphics/fonts basename the font cache resolves to a loadable path,
    // and the size the name and owner line render at - which is also each line's height for the box fit.
    private static final String BODY_FONT = "insignia15LTaa";
    private static final double FONT_SIZE = 15d;

    // The box's own look. Its padding, line gap, and cursor offset live on TooltipBoxLayout, since the
    // sizing and the text placement below both read them.
    private static final float BORDER_WIDTH = 1f;
    private static final float OPACITY = 0.9f;
    private static final Color FILL = Color.BLACK;

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
        if (sector == null) {
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

    // Resolves the box's content - the system name, its owner colour, and the owner line - then draws
    // it. An uninhabited system shows the name alone in the player colour; an owned one colours the name
    // by its owner and adds the faction line beneath.
    private static void drawTooltip(SectorAPI sector, StarSystemAPI system) {
        var owner = SectorPolitics.resolveDominantOwner(sector, system);
        var ownerLine = resolveOwnerName(sector, owner);
        var nameColor = owner != null ? owner.primaryColor() : Misc.getBrightPlayerColor();
        var name = system.getName();

        var face = LazyFontCache.loadByBasename(BODY_FONT);
        if (face == null) {
            // No text means no box worth drawing - a blank frame would only mislead.
            return;
        }
        var box = layOutBox(face, name, ownerLine);
        // The map chrome and its tooltips draw after this pass, so the raw-GL box and text run inside the
        // shared state save that restores the blend and colour state on the way out.
        GlStateGuard.bracket(() -> drawBox(box, name, ownerLine, nameColor));
    }

    // Paints the frame and its one or two lines into the laid-out box: the name at the top-left interior
    // corner, and the owner line one line-height below it when the system is owned.
    private static void drawBox(Rectangle box, String name, String ownerLine, Color nameColor) {
        BorderedBoxRenderer.render(box, BORDER_WIDTH, FILL, Misc.getBasePlayerColor(), OPACITY);
        var leftX = box.x() + TooltipBoxLayout.PADDING;
        var topY = box.y() + box.height() - TooltipBoxLayout.PADDING;
        LabelRenderer.render(
                BODY_FONT,
                name,
                leftX,
                topY,
                LazyFont.TextAnchor.TOP_LEFT,
                nameColor,
                OPACITY,
                FONT_SIZE);
        if (ownerLine != null) {
            var ownerY = topY - (float) FONT_SIZE - TooltipBoxLayout.LINE_GAP;
            LabelRenderer.render(
                    BODY_FONT,
                    ownerLine,
                    leftX,
                    ownerY,
                    LazyFont.TextAnchor.TOP_LEFT,
                    Misc.getTextColor(),
                    OPACITY,
                    FONT_SIZE);
        }
    }

    // The dominant faction's display name, or null when the system is uninhabited or the faction cannot be
    // resolved - either way the tooltip falls back to showing just the system name.
    private static String resolveOwnerName(SectorAPI sector, DominantOwner owner) {
        if (owner == null) {
            return null;
        }
        var faction = sector.getFaction(owner.factionId());
        return faction == null ? null : faction.getDisplayName();
    }

    // Measures the name (and owner line when present) and hands the widths, line count, and live cursor
    // and screen coordinates to the pure box layout, which sizes and clamps the box.
    private static Rectangle layOutBox(LazyFont face, String name, String ownerLine) {
        var measurer = new LazyFontMeasurer(face);
        var nameWidth = measurer.measureLineWidth(name, FONT_SIZE);
        var ownerWidth = ownerLine == null ? 0d : measurer.measureLineWidth(ownerLine, FONT_SIZE);
        var contentWidth = Math.max(nameWidth, ownerWidth);
        var lineCount = ownerLine == null ? 1 : 2;

        var settings = Global.getSettings();
        return TooltipBoxLayout.computeBox(
                contentWidth,
                lineCount,
                FONT_SIZE,
                UiCursor.getUiX(),
                UiCursor.getUiY(),
                settings.getScreenWidth(),
                settings.getScreenHeight());
    }
}
