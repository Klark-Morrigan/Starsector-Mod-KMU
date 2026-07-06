package kmu.politicalmap.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.CampaignUIRenderingListener;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.util.Misc;

import kmlib.color.Colors;
import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.font.LazyFontCache;
import kmlib.starsector.ui.input.UiCursor;
import kmlib.starsector.ui.map.CampaignMapView;

import kmu.politicalmap.layer.PoliticalMapLayer;
import kmu.politicalmap.layer.PoliticalMapLayers;
import kmu.settings.KmuLunaSettings;
import kmu.util.KmuStrings;

import org.apache.log4j.Logger;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lazywizard.lazylib.ui.LazyFont.DrawableString;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws the political-map layer bar on the sector map: a row of tabs, one per registered
 * layer, styled like the map's own Sector/System tabs - a black strip with the active tab lit
 * and its bound key shown in brackets - plus a caption beneath for the active layer's note.
 * It is the bar's paint only; a click on a tab and the layer hotkeys are read by
 * {@link PoliticalMapSidebarInput}, since a render pass gets no input events and cannot
 * consume them.
 *
 * <p>The sector map is a vanilla core-UI tab with no seam to attach a mod panel, so the bar is
 * drawn in UI coordinates through {@link CampaignUIRenderingListener} - specifically the
 * above-tooltips pass, the only one composited after the opaque core-UI map, so nothing the
 * map draws occludes it. It shows only where the overlay belongs
 * ({@link CampaignMapView#isSectorMapWithStarscapeOff()}). The bar reads the same
 * {@link SidebarLayout} placement the input listener hit-tests, so what is drawn and what is
 * clickable line up.
 */
public final class PoliticalMapSidebar implements CampaignUIRenderingListener {
    private static final Logger LOG = Global.getLogger(PoliticalMapSidebar.class);

    private static final String LABEL_FONT = "insignia15LTaa";
    private static final float TAB_FONT_SIZE = 14f;
    private static final float CAPTION_FONT_SIZE = 13f;

    // The bar's own black backdrop, so its labels read over the busy map; the player-colour
    // accent lights only the active and hovered tabs, keeping the general fill black.
    private static final Color BACKDROP = Color.BLACK;

    private static final float UNDERLINE_THICKNESS = 2f;
    private static final float BASELINE_THICKNESS = 1f;
    // How much of the bar opacity each accent touch carries, so a lit tab reads as a highlight
    // over the black rather than a second opaque block.
    private static final float SELECTED_FILL_ALPHA_MULT = 0.30f;
    private static final float HOVER_FILL_ALPHA_MULT = 0.15f;
    private static final float DIVIDER_ALPHA_MULT = 0.40f;

    // Cached across instances and reloads: the bar's labels are a handful of static strings, so
    // one GL text buffer per distinct label serves the whole run rather than leaking a buffer
    // per save reload. Keyed by font size and text, since a rebound key changes a tab's text.
    private static final Map<String, DrawableString> TEXT_CACHE = new HashMap<>();

    // View-state trace. The bar has no error state - when a signal blocks it, it is simply
    // absent - so the log is the only place "why hidden" or "drawn where" is answerable.
    // Deduped on the whole line: a steady state is one line, every change a fresh one. Null to
    // start, so the first pass logs and thereby proves the listener is registered and fires.
    private String lastLoggedLine;

    @Override
    public void renderInUICoordsBelowUI(ViewportAPI viewport) {
        // Below the whole campaign UI - under the map screen. Nothing belongs here.
    }

    @Override
    public void renderInUICoordsAboveUIBelowTooltips(ViewportAPI viewport) {
        // Fires from a panel inside the same tree as the core map screen, so the opaque map
        // covers anything drawn here. The bar draws in the above-tooltips pass instead.
    }

    @Override
    public void renderInUICoordsAboveUIAndTooltips(ViewportAPI viewport) {
        // The only pass composited after the entire map screen (and its tooltips), so it is
        // the sole layer the opaque core-UI map cannot occlude - the bar has to draw here.
        if (!CampaignMapView.isSectorMapWithStarscapeOff()) {
            logViewStateOnChange("hidden; " + CampaignMapView.describeViewState());
            return;
        }
        var settings = Global.getSettings();
        var placement = SidebarLayout.computePlacement(settings.getScreenWidth(),
                settings.getScreenHeight(), KmuLunaSettings.getPoliticalMapSidebarAnchor(),
                PoliticalMapLayers.getLayers());
        var opacity = KmuLunaSettings.getPoliticalMapSidebarBackgroundOpacity();
        // Logged before the draw, with the resolved footprint / screen / opacity, so a bar
        // gated in but never seen is diagnosed from the numbers rather than another run.
        logViewStateOnChange("showing; " + CampaignMapView.describeViewState() + "; screen="
                + settings.getScreenWidth() + "x" + settings.getScreenHeight()
                + " box=" + formatRect(placement.box()) + " opacity=" + opacity);
        drawBar(placement, opacity);
    }

    private void drawBar(SidebarPlacement placement, float opacity) {
        // Belt-and-suspenders around the raw GL: the map chrome and tooltips draw after this
        // pass, so any enable / colour / blend state the bar touches must be restored.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        var activeLayer = PoliticalMapLayers.getActiveLayer();
        var accent = Misc.getBasePlayerColor();
        // Hover reads the cursor in UI coords - the same space the tab rects live in - so the
        // tab under the pointer lights without an input event.
        var cursorX = UiCursor.getUiX();
        var cursorY = UiCursor.getUiY();
        drawTabFills(placement, activeLayer, accent, cursorX, cursorY, opacity);
        drawTabDividersAndBaseline(placement, accent, opacity);
        drawTabLabels(placement, activeLayer, cursorX, cursorY, opacity);
        drawCaption(placement.caption(), activeLayer, opacity);
        GL11.glPopAttrib();
    }

    // Black backdrop under every tab, with an accent wash on the active tab and a fainter one
    // on whichever tab the cursor is over.
    private static void drawTabFills(SidebarPlacement placement, PoliticalMapLayer activeLayer,
            Color accent, float cursorX, float cursorY, float opacity) {
        for (var tab : placement.tabs()) {
            var bounds = tab.bounds();
            Misc.renderQuadAlpha(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    BACKDROP, opacity);
            var isSelected = tab.layer() == activeLayer;
            if (isSelected) {
                Misc.renderQuadAlpha(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                        accent, opacity * SELECTED_FILL_ALPHA_MULT);
            } else if (bounds.containsPoint(cursorX, cursorY)) {
                Misc.renderQuadAlpha(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                        accent, opacity * HOVER_FILL_ALPHA_MULT);
            }
        }
    }

    // A faint baseline grounds the whole tab row, a bright underline caps the active tab
    // (echoing the map's own tab highlight), and a divider separates each tab from the next.
    private static void drawTabDividersAndBaseline(SidebarPlacement placement, Color accent,
            float opacity) {
        var tabs = placement.tabs();
        for (var index = 0; index < tabs.size(); index++) {
            var bounds = tabs.get(index).bounds();
            Misc.renderQuadAlpha(bounds.x(), bounds.y(), bounds.width(), BASELINE_THICKNESS,
                    accent, opacity * DIVIDER_ALPHA_MULT);
            if (index > 0) {
                Misc.renderQuadAlpha(bounds.x(), bounds.y(), BASELINE_THICKNESS,
                        bounds.height(), accent, opacity * DIVIDER_ALPHA_MULT);
            }
        }
        for (var tab : placement.tabs()) {
            if (PoliticalMapLayers.isActive(tab.layer())) {
                var bounds = tab.bounds();
                Misc.renderQuadAlpha(bounds.x(), bounds.y(), bounds.width(),
                        UNDERLINE_THICKNESS, accent, opacity);
            }
        }
    }

    // The tab label reads "Name [K]", bright on the active tab, accent under the cursor, gray
    // otherwise - so the bar's state and each tab's hotkey read at a glance.
    private static void drawTabLabels(SidebarPlacement placement, PoliticalMapLayer activeLayer,
            float cursorX, float cursorY, float opacity) {
        for (var tab : placement.tabs()) {
            var bounds = tab.bounds();
            var label = resolveText(composeTabText(tab.layer()), TAB_FONT_SIZE);
            if (label == null) {
                continue;
            }
            var isSelected = tab.layer() == activeLayer;
            var color = isSelected
                    ? Misc.getBrightPlayerColor()
                    : bounds.containsPoint(cursorX, cursorY)
                            ? Misc.getBasePlayerColor()
                            : Misc.getGrayColor();
            label.setBaseColor(Colors.scaleAlpha(color, opacity));
            label.draw(bounds.x() + bounds.width() / 2f, bounds.y() + bounds.height() / 2f);
        }
    }

    // The caption band shows only for a layer that carries one (No Layer's "nothing drawn"
    // note); a layer whose paint speaks for itself leaves the strip blank, not a dead band.
    private static void drawCaption(Rectangle caption, PoliticalMapLayer activeLayer,
            float opacity) {
        var captionKey = activeLayer.getCaptionKey();
        if (captionKey == null) {
            return;
        }
        Misc.renderQuadAlpha(caption.x(), caption.y(), caption.width(), caption.height(),
                BACKDROP, opacity);
        var text = resolveText(KmuStrings.get(captionKey), CAPTION_FONT_SIZE);
        if (text == null) {
            return;
        }
        text.setBaseColor(Colors.scaleAlpha(Misc.getGrayColor(), opacity));
        text.draw(caption.x() + caption.width() / 2f, caption.y() + caption.height() / 2f);
    }

    // Builds a tab's "Name [K]" text: the layer's label plus its current shortcut key, so a
    // rebound key shows the new letter. The hint is dropped when the shortcut names no key -
    // either an unbound keycode (0, cleared with Escape; LWJGL still names it "NONE") or a
    // code LWJGL cannot name.
    private static String composeTabText(PoliticalMapLayer layer) {
        var name = KmuStrings.get(layer.getTabLabelKey());
        var keycode = KmuLunaSettings.getPoliticalMapLayerShortcut(
                layer.getShortcutSettingKey(), layer.getDefaultShortcutKeycode());
        if (keycode <= 0) {
            return name;
        }
        var keyName = Keyboard.getKeyName(keycode);
        return keyName == null ? name : name + "  [" + keyName + "]";
    }

    // Logs the composed view-state line once per change; the dedupe keeps a steady state to one
    // line while every real transition prints a fresh one.
    private void logViewStateOnChange(String line) {
        if (line.equals(lastLoggedLine)) {
            return;
        }
        lastLoggedLine = line;
        LOG.debug("Political map layer bar " + line);
    }

    private static String formatRect(Rectangle rect) {
        return "[" + rect.x() + "," + rect.y() + " " + rect.width() + "x" + rect.height() + "]";
    }

    // Mints a centered drawable string once per (size, text) and reuses it for the run; the
    // base colour is re-set before each draw, so one buffer serves every frame. Null when the
    // font face cannot load, in which case the bar draws without that text.
    private static DrawableString resolveText(String text, float fontSize) {
        var key = fontSize + "|" + text;
        var cached = TEXT_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        var font = LazyFontCache.loadByBasename(LABEL_FONT);
        if (font == null) {
            return null;
        }
        var drawable = font.createText(text, Misc.getBrightPlayerColor(), fontSize);
        drawable.setAnchor(LazyFont.TextAnchor.CENTER);
        TEXT_CACHE.put(key, drawable);
        return drawable;
    }
}
