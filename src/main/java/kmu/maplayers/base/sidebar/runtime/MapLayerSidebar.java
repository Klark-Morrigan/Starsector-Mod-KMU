package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.CampaignUIRenderingListener;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.util.Misc;

import kmlib.color.Colors;
import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.font.LazyFontCache;
import kmlib.starsector.ui.input.UiCursor;
import kmlib.starsector.ui.map.CampaignMapView;
import kmlib.starsector.ui.widgets.Checkbox;
import kmlib.starsector.ui.widgets.RadioRow;
import kmlib.starsector.ui.widgets.TabPanel;
import kmlib.starsector.ui.render.gl.ToggleButton;
import kmlib.starsector.ui.render.gl.VanillaTabColors;
import kmlib.text.KmlibStrings;

import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.sidebar.LiveSidebarPlacement;
import kmu.maplayers.base.sidebar.SidebarControl;
import kmu.maplayers.base.sidebar.SidebarControlSpec;
import kmu.maplayers.base.sidebar.SidebarLayout;
import kmu.maplayers.base.sidebar.SidebarPlacement;
import kmu.settings.KmuLunaSettings;

import org.apache.log4j.Logger;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lazywizard.lazylib.ui.LazyFont.DrawableString;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the on-map layer sidebar: an outer-bordered panel carrying a row of vanilla-styled tabs -
 * one per registered layer, painted like the map's own Sector/System tabs - over a control body
 * the active tab fills with its own controls. It is the panel's paint only; a click on a tab, the
 * layer hotkeys, and a click on a body control are read by {@link MapLayerSidebarInput}, since a
 * render pass gets no input events and cannot consume them.
 *
 * <p>The frame and header are the reusable KMLib {@link TabPanel} (a bordered frame over a
 * vanilla-styled tab strip); the body controls are the KMLib {@link Checkbox}/{@link RadioRow}/{@link
 * ToggleButton} widgets. So this class owns only the paint wiring: which tab is selected, where the
 * cursor hovers, and drawing each body control in the live state its spec carries. It stays agnostic
 * to what any control means - a control's lit state rides along in its {@link SidebarControlSpec}, so
 * this draws a faction toggle or a future alliances control the same way without knowing either.
 *
 * <p>The sector map is a vanilla core-UI tab with no seam to attach a mod panel, so the sidebar is
 * drawn in UI coordinates through {@link CampaignUIRenderingListener} - specifically the
 * above-tooltips pass, the only one composited after the opaque core-UI map, so nothing the map
 * draws occludes it. It shows only where the overlay belongs
 * ({@link CampaignMapView#isSectorMapWithStarscapeOff()}). The panel draws the
 * {@link LiveSidebarPlacement} the input listener also hit-tests, so what is drawn and what is
 * clickable line up. Every draw scales its alpha by one opacity, so lowering it fades the whole
 * panel - border, tab chrome, labels, and body controls alike - not just the text.
 */
public final class MapLayerSidebar implements CampaignUIRenderingListener {
    private static final Logger LOG = Global.getLogger(MapLayerSidebar.class);

    // The body face: the insignia body font, a graphics/fonts basename the font cache resolves to a
    // loadable path. The tab face is the resolver's, since it both measures and draws the tabs.
    private static final String BODY_FONT = "insignia15LTaa";

    // The whole panel's backdrop: the bordered box fills its footprint with this, and the tab strip
    // and body controls draw their player-colour accents over it, so the general fill stays black
    // and only lit elements carry colour.
    private static final Color PANEL_FILL = Color.BLACK;

    // Cached across instances and reloads: the body labels are a handful of static strings, so one
    // GL text buffer per distinct (size, text) serves the whole run rather than leaking a buffer
    // per frame. Keyed by font size and text. The tab strip caches its own labels; this is only
    // the body's.
    private static final Map<String, DrawableString> BODY_TEXT_CACHE = new HashMap<>();

    // View-state trace. The panel has no error state - when a signal blocks it, it is simply
    // absent - so the log is the only place "why hidden" or "drawn where" is answerable. Deduped
    // on the whole line: a steady state is one line, every change a fresh one. Null to start, so
    // the first pass logs and thereby proves the listener is registered and fires.
    private String lastLoggedLine;

    // Whether the overlay was on screen last pass, so the transition to off can be caught. The
    // body controls write their settings through LunaLib's deferred path (live value, batched
    // disk write), so leaving the overlay is when a map session's edits are flushed to disk.
    private boolean wasOverlayShowing;

    @Override
    public void renderInUICoordsBelowUI(ViewportAPI viewport) {
        // Below the whole campaign UI - under the map screen. Nothing belongs here.
    }

    @Override
    public void renderInUICoordsAboveUIBelowTooltips(ViewportAPI viewport) {
        // Fires from a panel inside the same tree as the core map screen, so the opaque map
        // covers anything drawn here. The panel draws in the above-tooltips pass instead.
    }

    @Override
    public void renderInUICoordsAboveUIAndTooltips(ViewportAPI viewport) {
        // The only pass composited after the entire map screen (and its tooltips), so it is the
        // sole layer the opaque core-UI map cannot occlude - the panel has to draw here.
        boolean isOverlayShowing = CampaignMapView.isSectorMapWithStarscapeOff();
        // On the pass the overlay turns off - the player closed the map or switched on the
        // starscape - persist the body controls' deferred writes, collapsing the map session's
        // edits into one disk write.
        if (wasOverlayShowing && !isOverlayShowing) {
            KmuLunaSettings.flushPendingWrites();
        }
        wasOverlayShowing = isOverlayShowing;
        if (!isOverlayShowing) {
            logViewStateOnChange("hidden; " + CampaignMapView.describeViewState());
            return;
        }
        // The same placement the input listener hit-tests, resolved from one source so the drawn
        // box and the clickable box line up. Null means the tab font could not load - the layout
        // snaps tabs to measured text and cannot run without it - so the panel stays absent, logged
        // once like any other hidden reason.
        var placement = LiveSidebarPlacement.resolveCurrentPlacement();
        if (placement == null) {
            logViewStateOnChange("hidden; tab font '" + LiveSidebarPlacement.TAB_FONT
                    + "' unavailable");
            return;
        }
        var settings = Global.getSettings();
        var borderWidth = KmuLunaSettings.getPoliticalMapSidebarBorderWidth();
        var opacity = KmuLunaSettings.getPoliticalMapSidebarBackgroundOpacity();
        // Logged before the draw, with the resolved footprint / screen / opacity, so a panel gated
        // in but never seen is diagnosed from the numbers rather than another run.
        logViewStateOnChange("showing; " + CampaignMapView.describeViewState() + "; screen="
                + settings.getScreenWidth() + "x" + settings.getScreenHeight()
                + " box=" + formatRect(placement.box()) + " opacity=" + opacity);
        drawSidebar(placement, selectedTabIndex(), borderWidth, opacity);
    }

    // The active layer's position in the registry order the tabs are laid out in, so the selected
    // tab the strip lights matches the active pick.
    private static int selectedTabIndex() {
        return MapLayerRegistry.getLayers().indexOf(MapLayerRegistry.getActiveLayer());
    }

    private void drawSidebar(SidebarPlacement placement, int selectedIndex, float borderWidth,
            float opacity) {
        // Belt-and-suspenders around the raw GL: the map chrome and tooltips draw after this
        // pass, so any enable / colour / blend state the panel touches must be restored. The whole
        // draw - the panel's frame and header plus the body controls below - shares this one save,
        // since the panel widget leaves GL state to its consumer.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        var accent = Misc.getBasePlayerColor();
        // Hover reads the cursor in UI coords - the same space the tab rects live in - so the tab
        // under the pointer lights without an input event.
        var hoveredIndex = TabPanel.findTabIndexAt(placement.panel(), UiCursor.getUiX(),
                UiCursor.getUiY());
        // The reusable panel paints its own chrome: the bordered frame (black fill, accent stroke)
        // and the vanilla-styled tab header, all faded by the one opacity. The body controls are
        // KMU's and drawn below.
        TabPanel.render(placement.panel(), borderWidth, PANEL_FILL, accent, selectedIndex,
                hoveredIndex, VanillaTabColors.mapTabs(), LiveSidebarPlacement.TAB_FONT,
                SidebarLayout.TAB_FONT_SIZE, opacity);
        drawBodyControls(placement.bodyControls(), accent, opacity);
        GL11.glPopAttrib();
    }

    // Draws each body control with its KMLib widget in the lit state its spec carries, then the
    // control's label(s) in white over it. The kind names the widget; the meaning stays with the
    // tab that supplied the spec, so this draws any tab's body without learning what it does.
    private static void drawBodyControls(List<SidebarControl> controls, Color accent,
            float opacity) {
        for (var control : controls) {
            switch (control.spec().kind()) {
                case CHECKBOX -> drawCheckbox(control, accent, opacity);
                case RADIO -> drawRadio(control, accent, opacity);
                case TOGGLE -> drawToggle(control, accent, opacity);
                case LABEL -> drawLabelRow(control, opacity);
            }
        }
    }

    // A tick box lit when the spec's cell is selected, then its label to the right at the same gap
    // the layout reserved, so the label sits exactly in the space snapped for it.
    private static void drawCheckbox(SidebarControl control, Color accent, float opacity) {
        var spec = control.spec();
        var bounds = control.bounds();
        Checkbox.render(bounds, isLit(spec), accent, Misc.getBrightPlayerColor(), opacity);
        var box = Checkbox.computeTickBox(bounds);
        var labelX = box.x() + box.width() + SidebarLayout.CHECKBOX_LABEL_GAP;
        drawBodyLabel(spec.labels().get(0), labelX, centerY(bounds),
                LazyFont.TextAnchor.CENTER_LEFT, opacity);
    }

    // The segments framed and the active one washed, each option label centred in its segment, and
    // the trailing caption (e.g. "Names") after the row at the layout's reserved gap. The segments
    // flow the way the spec's alignment sets - a horizontal strip or a vertical stack - so the wash
    // and dividers follow the same flow the layout split the row into.
    private static void drawRadio(SidebarControl control, Color accent, float opacity) {
        var spec = control.spec();
        var bounds = control.bounds();
        var labels = spec.labels();
        RadioRow.render(bounds, labels.size(), spec.selectedIndex(), spec.alignment(), accent,
                accent, opacity);
        var segments = control.segments();
        for (var index = 0; index < segments.size() && index < labels.size(); index++) {
            var segment = segments.get(index);
            drawBodyLabel(labels.get(index), segment.x() + segment.width() / 2f, centerY(segment),
                    LazyFont.TextAnchor.CENTER, opacity);
        }
        if (KmlibStrings.hasText(spec.trailingLabel())) {
            var trailingX = bounds.x() + bounds.width() + SidebarLayout.TRAILING_LABEL_GAP;
            drawBodyLabel(spec.trailingLabel(), trailingX, centerY(bounds),
                    LazyFont.TextAnchor.CENTER_LEFT, opacity);
        }
    }

    // A single button washed when the spec's cell is lit, its label centred in it - the lit state
    // is the on/off signal, so the label carries no On/Off word.
    private static void drawToggle(SidebarControl control, Color accent, float opacity) {
        var spec = control.spec();
        var bounds = control.bounds();
        ToggleButton.render(bounds, isLit(spec), accent, accent, opacity);
        drawBodyLabel(spec.labels().get(0), bounds.x() + bounds.width() / 2f, centerY(bounds),
                LazyFont.TextAnchor.CENTER, opacity);
    }

    // A caption row: only its text, left-aligned at the row's left edge and vertically centred, with
    // no widget chrome - it heads the controls below it and is never clicked.
    private static void drawLabelRow(SidebarControl control, float opacity) {
        var bounds = control.bounds();
        drawBodyLabel(control.spec().labels().get(0), bounds.x(), centerY(bounds),
                LazyFont.TextAnchor.CENTER_LEFT, opacity);
    }

    // A single-cell control (checkbox, toggle) is lit when its one cell (index 0) is the selected
    // one; NO_SELECTION means off.
    private static boolean isLit(SidebarControlSpec spec) {
        return spec.selectedIndex() != SidebarControlSpec.NO_SELECTION;
    }

    // Draws one body label in white, faded by opacity, at the given anchor. Skipped silently when
    // the body font cannot load, in which case the control draws its chrome without text.
    private static void drawBodyLabel(String text, float x, float y, LazyFont.TextAnchor anchor,
            float opacity) {
        var drawable = resolveBodyText(text);
        if (drawable == null) {
            return;
        }
        drawable.setAnchor(anchor);
        drawable.setBaseColor(Colors.scaleAlpha(Misc.getTextColor(), opacity));
        drawable.draw(x, y);
    }

    private static float centerY(Rectangle bounds) {
        return bounds.y() + bounds.height() / 2f;
    }

    // Mints a body drawable once per (size, text) and reuses it for the run; the base colour is
    // re-set before each draw, so one buffer serves every frame. Null when the font face cannot
    // load, in which case the control draws without that text.
    private static DrawableString resolveBodyText(String text) {
        var key = SidebarLayout.BODY_FONT_SIZE + "|" + text;
        var cached = BODY_TEXT_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        var font = LazyFontCache.loadByBasename(BODY_FONT);
        if (font == null) {
            return null;
        }
        var drawable = font.createText(text, Misc.getTextColor(), (float) SidebarLayout.BODY_FONT_SIZE);
        BODY_TEXT_CACHE.put(key, drawable);
        return drawable;
    }

    // Logs the composed view-state line once per change; the dedupe keeps a steady state to one
    // line while every real transition prints a fresh one.
    private void logViewStateOnChange(String line) {
        if (line.equals(lastLoggedLine)) {
            return;
        }
        lastLoggedLine = line;
        LOG.debug("Political map layer sidebar " + line);
    }

    private static String formatRect(Rectangle rect) {
        return "[" + rect.x() + "," + rect.y() + " " + rect.width() + "x" + rect.height() + "]";
    }
}
