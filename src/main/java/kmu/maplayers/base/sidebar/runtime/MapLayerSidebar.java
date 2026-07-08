package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.CampaignUIRenderingListener;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.util.Misc;

import kmlib.color.Colors;
import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.font.LazyFontCache;
import kmlib.starsector.ui.font.LazyFontMeasurer;
import kmlib.starsector.ui.font.LineWidthMeasurer;
import kmlib.starsector.ui.input.UiCursor;
import kmlib.starsector.ui.map.CampaignMapView;
import kmlib.starsector.ui.widgets.BorderedBox;
import kmlib.starsector.ui.widgets.Checkbox;
import kmlib.starsector.ui.widgets.RadioRow;
import kmlib.starsector.ui.widgets.ToggleButton;
import kmlib.starsector.ui.widgets.VanillaTabColors;
import kmlib.starsector.ui.widgets.VanillaTabContent;
import kmlib.starsector.ui.widgets.VanillaTabStrip;
import kmlib.text.KmlibStrings;

import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.sidebar.SidebarControl;
import kmu.maplayers.base.sidebar.SidebarControlSpec;
import kmu.maplayers.base.sidebar.SidebarLayout;
import kmu.maplayers.base.sidebar.SidebarPlacement;
import kmu.settings.KmuLunaSettings;
import kmu.util.KmuStrings;

import org.apache.log4j.Logger;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lazywizard.lazylib.ui.LazyFont.DrawableString;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
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
 * <p>The panel is composed from the reusable KMLib raw-GL widgets (a {@link BorderedBox} frame, a
 * {@link VanillaTabStrip} header, and the {@link Checkbox}/{@link RadioRow}/{@link ToggleButton}
 * body controls), so this class owns only the wiring: which layers become tabs, which tab is
 * selected, where the cursor hovers, and drawing each body control in the live state its spec
 * carries. It stays agnostic to what any control means - a control's lit state rides along in its
 * {@link SidebarControlSpec}, so this draws a faction toggle or a future alliances control the same
 * way without knowing either.
 *
 * <p>The sector map is a vanilla core-UI tab with no seam to attach a mod panel, so the sidebar is
 * drawn in UI coordinates through {@link CampaignUIRenderingListener} - specifically the
 * above-tooltips pass, the only one composited after the opaque core-UI map, so nothing the map
 * draws occludes it. It shows only where the overlay belongs
 * ({@link CampaignMapView#isSectorMapWithStarscapeOff()}). The panel reads the same
 * {@link SidebarLayout} placement the input listener hit-tests, so what is drawn and what is
 * clickable line up. Every draw scales its alpha by one opacity, so lowering it fades the whole
 * panel - border, tab chrome, labels, and body controls alike - not just the text.
 */
public final class MapLayerSidebar implements CampaignUIRenderingListener {
    private static final Logger LOG = Global.getLogger(MapLayerSidebar.class);

    // The tabs read in the sector map's own orbitron face - the AA orbitron atlas vanilla uses for
    // its map tabs, scaled to the tab size - while the body keeps the insignia body face. Both are
    // graphics/fonts basenames the font cache resolves to a loadable path.
    private static final String TAB_FONT = "orbitron20aa";
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
        if (!CampaignMapView.isSectorMapWithStarscapeOff()) {
            logViewStateOnChange("hidden; " + CampaignMapView.describeViewState());
            return;
        }
        // The layout snaps tabs to their measured text, so it needs the tab font; without it the
        // panel cannot lay out and simply stays absent, logged once like any other hidden reason.
        var measurer = loadTabMeasurer();
        if (measurer == null) {
            logViewStateOnChange("hidden; tab font '" + TAB_FONT + "' unavailable");
            return;
        }
        var settings = Global.getSettings();
        var layers = MapLayerRegistry.getLayers();
        var activeLayer = MapLayerRegistry.getActiveLayer();
        var borderWidth = KmuLunaSettings.getPoliticalMapSidebarBorderWidth();
        var placement = SidebarLayout.computePlacement(settings.getScreenHeight(),
                KmuLunaSettings.getPoliticalMapSidebarPaddingTop(),
                KmuLunaSettings.getPoliticalMapSidebarPaddingLeft(), borderWidth,
                buildTabContents(layers), activeLayer.getBodyControls(), measurer);
        var opacity = KmuLunaSettings.getPoliticalMapSidebarBackgroundOpacity();
        // Logged before the draw, with the resolved footprint / screen / opacity, so a panel gated
        // in but never seen is diagnosed from the numbers rather than another run.
        logViewStateOnChange("showing; " + CampaignMapView.describeViewState() + "; screen="
                + settings.getScreenWidth() + "x" + settings.getScreenHeight()
                + " box=" + formatRect(placement.box()) + " opacity=" + opacity);
        drawSidebar(placement, layers.indexOf(activeLayer), borderWidth, opacity);
    }

    private void drawSidebar(SidebarPlacement placement, int selectedIndex, float borderWidth,
            float opacity) {
        // Belt-and-suspenders around the raw GL: the map chrome and tooltips draw after this
        // pass, so any enable / colour / blend state the panel touches must be restored.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT);
        var accent = Misc.getBasePlayerColor();
        // The bordered frame under everything: a black fill over the whole footprint and, when a
        // width is set, an accent stroke on its edge, both faded by the one opacity.
        BorderedBox.render(placement.box(), borderWidth, PANEL_FILL, accent, opacity);
        // Hover reads the cursor in UI coords - the same space the tab rects live in - so the tab
        // under the pointer lights without an input event.
        var hoveredIndex = VanillaTabStrip.findTabIndexAt(placement.tabs(), UiCursor.getUiX(),
                UiCursor.getUiY());
        VanillaTabStrip.render(placement.tabs(), selectedIndex, hoveredIndex,
                VanillaTabColors.mapTabs(), TAB_FONT, SidebarLayout.TAB_FONT_SIZE, opacity);
        drawBodyControls(placement.bodyControls(), accent, opacity);
        GL11.glPopAttrib();
    }

    // Turns each layer into a tab's content: its label and the display name of its current
    // shortcut key, which the strip paints in gold. Registry order, so the selected/hovered index
    // and the input listener's hit-test all index the same row.
    private static List<VanillaTabContent> buildTabContents(List<MapLayer> layers) {
        var contents = new ArrayList<VanillaTabContent>(layers.size());
        for (var layer : layers) {
            contents.add(new VanillaTabContent(KmuStrings.get(layer.getTabLabelKey()),
                    resolveShortcutName(layer)));
        }
        return contents;
    }

    // The display name of a layer's shortcut key, or null when it has none - an unbound keycode (0,
    // cleared with Escape; LWJGL still names it "NONE") or a code LWJGL cannot name. A null/blank
    // shortcut leaves the tab label alone.
    private static String resolveShortcutName(MapLayer layer) {
        var keycode = KmuLunaSettings.getPoliticalMapLayerShortcut(
                layer.getShortcutSettingKey(), layer.getDefaultShortcutKeycode());
        if (keycode <= 0) {
            return null;
        }
        return Keyboard.getKeyName(keycode);
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
    // the trailing caption (e.g. "Names") after the row at the layout's reserved gap.
    private static void drawRadio(SidebarControl control, Color accent, float opacity) {
        var spec = control.spec();
        var bounds = control.bounds();
        var labels = spec.labels();
        RadioRow.render(bounds, labels.size(), spec.selectedIndex(), accent, accent, opacity);
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

    // The tab font wrapped as a width measurer, or null when it cannot load. One measurer serves
    // both the tab and body snapping in the layout: body labels drawn in the narrower insignia face
    // fit inside boxes snapped to this face, so the body reads correctly and only the tabs, drawn
    // in this same face, need it to match exactly.
    private static LineWidthMeasurer loadTabMeasurer() {
        var font = LazyFontCache.loadByBasename(TAB_FONT);
        if (font == null) {
            return null;
        }
        return new LazyFontMeasurer(font);
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
