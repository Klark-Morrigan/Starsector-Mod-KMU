package kmu.politicalmap.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.CampaignUIRenderingListener;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.util.Misc;

import kmlib.color.Colors;
import kmlib.input.ClickEdgeDetector;
import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.font.LazyFontCache;
import kmlib.starsector.ui.input.UiCursor;
import kmlib.starsector.ui.map.CampaignMapView;
import kmlib.starsector.ui.render.UiBoxes;

import kmu.settings.KmuLunaSettings;
import kmu.util.KmuStrings;

import org.apache.log4j.Logger;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lazywizard.lazylib.ui.LazyFont.DrawableString;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * Draws the political-map overlay's control box on the sector map and reads its one click.
 * The box is a small, non-modal panel anchored to a screen corner or edge with a single
 * "Political Map" tab: selecting the tab shows the overlay, deselecting it hides it.
 *
 * <p>The sector map is a vanilla core-UI tab with no seam to attach a mod panel and no
 * input events to hand one, so the box is drawn in UI coordinates through
 * {@link CampaignUIRenderingListener} and reads its own click by polling the raw mouse in
 * the same render pass - an every-frame script is throttled while the map pauses the
 * campaign, but the render hook keeps firing. Polling in the map's empty margin consumes
 * no event, so the map stays fully interactive behind the box. The box shows only when the
 * overlay does ({@link CampaignMapView#isSectorMapWithStarscapeOff()}), so it never appears
 * where it does not belong.
 */
public final class PoliticalMapSidebar implements CampaignUIRenderingListener {
    private static final Logger LOG = Global.getLogger(PoliticalMapSidebar.class);
    private static final String TAB_LABEL_FONT = "insignia15LTaa";
    private static final float TAB_LABEL_FONT_SIZE = 15f;
    private static final int LEFT_MOUSE_BUTTON = 0;
    private static final float BORDER_THICKNESS = 1f;
    // How much of the box opacity the selected tab strip carries, so the active tab reads
    // as a lit panel rather than a second opaque block.
    private static final float SELECTED_TAB_ALPHA_MULT = 0.5f;

    // Cached across instances: the tab label is one static string, so one GL text buffer
    // serves every sidebar for the run rather than leaking a buffer per save reload.
    private static DrawableString tabLabel;
    private static boolean hasTriedFontLoad;

    private final ClickEdgeDetector clickEdgeDetector = new ClickEdgeDetector();

    // Gate-transition trace. The box has no error state - when a signal blocks it, it is
    // simply absent - so the log is the only place "why is it hidden" can be answered.
    // Logging only on transitions keeps it to a handful of lines per session; starting
    // unevaluated makes the first render pass log once, which also proves the listener is
    // registered and the render hook fires at all.
    private boolean hasEvaluatedGate;
    private boolean wasSidebarShown;

    @Override
    public void renderInUICoordsBelowUI(ViewportAPI viewport) {
        // Below the whole campaign UI - under the map screen. Nothing belongs here.
    }

    @Override
    public void renderInUICoordsAboveUIBelowTooltips(ViewportAPI viewport) {
        // Fires from a panel inside the same tree as the core map screen, so the opaque
        // map covers anything drawn here. The box draws in the above-tooltips pass instead.
    }

    @Override
    public void renderInUICoordsAboveUIAndTooltips(ViewportAPI viewport) {
        // The only pass composited after the entire map screen (and its tooltips), so it is
        // the sole layer the opaque core-UI map cannot occlude - the box has to draw here.
        //
        // Poll the button every frame, even off the map, so the edge state stays current
        // and returning to the map mid-hold is not read as a fresh click.
        var isPress = clickEdgeDetector.detectPress(Mouse.isButtonDown(LEFT_MOUSE_BUTTON));
        var isSidebarShown = CampaignMapView.isSectorMapWithStarscapeOff();
        if (!hasEvaluatedGate || wasSidebarShown != isSidebarShown) {
            hasEvaluatedGate = true;
            wasSidebarShown = isSidebarShown;
            LOG.debug("Political map sidebar " + (isSidebarShown ? "showing" : "hidden")
                    + "; " + CampaignMapView.describeViewState());
        }
        if (!isSidebarShown) {
            return;
        }
        var settings = Global.getSettings();
        var placement = SidebarLayout.computePlacement(
                settings.getScreenWidth(), settings.getScreenHeight(),
                KmuLunaSettings.getPoliticalMapSidebarAnchor());
        var isSelected = PoliticalOverlayToggle.isOverlayEnabled();
        drawBox(placement, isSelected, KmuLunaSettings.getPoliticalMapSidebarBackgroundOpacity());
        if (isPress && placement.tab().containsPoint(UiCursor.getUiX(), UiCursor.getUiY())) {
            PoliticalOverlayToggle.toggleOverlayEnabled();
        }
    }

    private void drawBox(SidebarPlacement placement, boolean isSelected, float opacity) {
        // Belt-and-suspenders around the raw GL: the map chrome and tooltips draw after
        // this pass, so any enable / color / blend state the box touches must be restored.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        var box = placement.box();
        var tab = placement.tab();
        var backdrop = Misc.getDarkPlayerColor();
        var accent = Misc.getBasePlayerColor();
        Misc.renderQuadAlpha(box.x(), box.y(), box.width(), box.height(), backdrop, opacity);
        if (isSelected) {
            // A filled tab strip reads as the pressed/active tab; deselected it is bare.
            Misc.renderQuadAlpha(tab.x(), tab.y(), tab.width(), tab.height(), accent,
                    opacity * SELECTED_TAB_ALPHA_MULT);
        }
        UiBoxes.renderBorder(box.x(), box.y(), box.width(), box.height(), BORDER_THICKNESS,
                accent, opacity);
        // The seam between the tab strip and the empty body, so the tab reads on its own.
        Misc.renderQuadAlpha(tab.x(), tab.y(), tab.width(), BORDER_THICKNESS, accent, opacity);
        drawTabLabel(tab, isSelected, opacity);
        GL11.glPopAttrib();
    }

    private static void drawTabLabel(Rectangle tab, boolean isSelected, float opacity) {
        var label = resolveTabLabel();
        if (label == null) {
            return;
        }
        // Bright when the overlay is shown, dim when hidden, so the tab's state reads at a
        // glance; refaded to the box opacity so it dims with the rest of the panel.
        var color = isSelected ? Misc.getBrightPlayerColor() : Misc.getGrayColor();
        label.setBaseColor(Colors.scaleAlpha(color, opacity));
        label.draw(tab.x() + tab.width() / 2f, tab.y() + tab.height() / 2f);
    }

    // Loads the tab-label font once and mints its drawable string; null when the face
    // cannot load, in which case the box still draws without a label.
    private static DrawableString resolveTabLabel() {
        if (tabLabel != null) {
            return tabLabel;
        }
        if (hasTriedFontLoad) {
            return null;
        }
        hasTriedFontLoad = true;
        var font = LazyFontCache.loadByBasename(TAB_LABEL_FONT);
        if (font == null) {
            return null;
        }
        var text = font.createText(KmuStrings.get(KmuStrings.POLITICAL_MAP_SIDEBAR_TAB),
                Misc.getBrightPlayerColor(), TAB_LABEL_FONT_SIZE);
        text.setAnchor(LazyFont.TextAnchor.CENTER);
        tabLabel = text;
        return tabLabel;
    }
}
