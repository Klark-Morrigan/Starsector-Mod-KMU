package kmu.maplayers.base.sidebar;

import com.fs.starfarer.api.Global;

import kmlib.math.geometry.BoxEdge;
import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.controls.ControlSpec;
import kmlib.starsector.ui.font.LazyFontCache;
import kmlib.starsector.ui.font.LazyFontMeasurer;
import kmlib.starsector.ui.font.LineWidthMeasurer;
import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.starsector.ui.font.TextFace;
import kmlib.starsector.ui.input.TabPanelController;
import kmlib.starsector.ui.layout.Padding;
import kmlib.starsector.ui.layout.TabPanelLayout;
import kmlib.starsector.ui.layout.TabsControlLayout;
import kmlib.starsector.ui.widgets.BoxBorder;
import kmlib.starsector.ui.widgets.tabs.HotkeyStyle;
import kmlib.starsector.ui.widgets.tabs.TabPanelPlacement;
import kmlib.starsector.ui.widgets.tabs.TabPanelViewState;
import kmlib.starsector.ui.widgets.tabs.TabStyle;
import kmlib.starsector.ui.widgets.tabs.VanillaTabColours;

import kmu.maplayers.base.layer.ActiveLayerSelection;
import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.settings.KmuMapLayerSettings;
import kmu.util.KmuStrings;

import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Resolves a sidebar's placement from the live screen, settings, and active layer - the one placement
 * the host's renderer draws and its input listener hit-tests, so the two agree on exactly what is on
 * screen. Both passes resolve through here each frame rather than each computing its own, so the tabs a
 * click resolves against are the tabs that were drawn; without a single source the two could drift (a
 * settings change landing between the render and the input pass would move the drawn box out from under
 * the hit-test). The two entry points differ in where the panel anchors and how tall it stands its tabs:
 * {@link #resolveMapPlacement} hangs it from the screen top-left for the on-map sidebar, and {@link
 * #resolveIntelPlacement} anchors it to the visor's top-left, overlaying the intel screen's map preview.
 * Both lay out the same body; each takes the host's own {@link TabPanelController}, so the map and intel
 * panels keep separate scroll and collapse state while sharing one layout, and each injects its own
 * {@link TabStyle}, so the two size their tab bands to the chrome they sit beside.
 *
 * <p>The layer selector is one {@link ControlSpec.Tabs} control whose action selects the layer at
 * the clicked index, so the layer switch rides on the control itself and the input listener needs no tab
 * callback. Layout snaps each tab to its measured label, so the placement needs the tab font's width
 * measurer; the face travels inside the same {@link TabStyle} the renderer paints from, so a snapped tab
 * width matches the text drawn into it.
 */
public final class LiveSidebarPlacement {
    
    // The tabs read in the sector map's own orbitron face - the AA orbitron atlas vanilla uses for its
    // map tabs, scaled to the tab size. The measurer loads it and both tab styles below carry it, so a
    // snapped tab width matches the text drawn into it.
    private static final StarsectorFont TAB_FONT = StarsectorFont.VANILLA_ORBITRON_20AA;

    // The two screens size their tab bands differently because they sit in different company. The on-map
    // sidebar floats free beside the vanilla Sector/System tabs and matches their weight, while the intel
    // sidebar overlays the visor under the map toggles and reads tighter so it crowds the preview less.
    // Both heights are content-space: each panel strokes its own top border above the band, so the drawn
    // strip stands the configured border width taller than the number here.
    // Package-private, as the intel anchor math beside them is, so the divergence the two screens depend on
    // is checkable without standing up a live sector - the styles built from them resolve live colours and
    // so cannot be reached without a sector.
    static final float MAP_HEADER_BAND_HEIGHT = 19f;
    static final float INTEL_HEADER_BAND_HEIGHT = 17f;

    private LiveSidebarPlacement() {
    }

    /**
     * The on-map sidebar's tab look: the map band height over the vanilla map-tab colour scheme and the
     * orbitron face. Public because the render pass paints its tabs from the same value the layout
     * measured them against, so a drawn tab cannot part from the band it was snapped into. Resolves the
     * live palette on each call, so the scheme tracks a player-faction recolour.
     *
     * @return the on-map sidebar's tab style
     */
    public static TabStyle buildMapTabStyle() {
        return buildTabStyle(MAP_HEADER_BAND_HEIGHT);
    }

    /**
     * Lays the on-map sidebar out for the current screen, the player's padding, and the active layer's
     * body: it hangs from the screen top-left and grows rightward to fit its content.
     *
     * @param controller    the on-map panel's scroll and collapse state
     * @param selection     the on-map screen's own active-layer pick, read for the lit tab and body and
     *                      written when a tab is clicked
     * @param borderedEdges which frame edges the host reserves and strokes; the on-map sidebar frames all
     *                      four, while a host drawn flush against a neighbour drops the shared edges so the
     *                      box collapses the strip they would occupy
     * @return the placement to draw and hit-test, or {@code null} when the tab font cannot load (see
     *         {@link #resolvePlacement})
     */
    public static TabPanelPlacement resolveMapPlacement(
            TabPanelController controller,
            ActiveLayerSelection selection,
            Set<BoxEdge> borderedEdges) {
        return resolvePlacement(
            buildMapPadding(),
            buildMapTabStyle(),
            controller,
            selection,
            borderedEdges);
    }

    /**
     * Lays the intel-screen sidebar out over the lit visor: it sits flush against the visor's left edge
     * and hangs from the visor top (pushed down by the player's top padding to clear the vanilla map
     * toggles), overlaying the visor with the same body the on-map sidebar lays out.
     *
     * @param mapVisorRect  the lit visor's screen rectangle, the corner the panel anchors to
     * @param controller    the intel panel's own scroll and collapse state, separate from the on-map panel's
     * @param selection     the intel screen's own active-layer pick, separate from the on-map screen's, so a
     *                      switch on one screen does not move the other's tab
     * @param borderedEdges which frame edges the intel host reserves and strokes; it drops the edges it
     *                      shares with the visor so the box collapses the strip they would occupy and sits
     *                      flush
     * @return the placement to draw and hit-test, or {@code null} when the tab font cannot load (see
     *         {@link #resolvePlacement})
     */
    public static TabPanelPlacement resolveIntelPlacement(
            Rectangle mapVisorRect,
            TabPanelController controller,
            ActiveLayerSelection selection,
            Set<BoxEdge> borderedEdges) {
        return resolvePlacement(
            buildIntelPadding(mapVisorRect),
            buildIntelTabStyle(),
            controller,
            selection,
            borderedEdges);
    }

    // The intel sidebar's tab look: the tighter intel band over the same colour scheme and face the map
    // strip reads in, the two screens differing today only in how tall they stand the band.
    // Package-private beside the intel anchor math it belongs with - only this class lays the intel panel
    // out, and the render pass paints both screens' tabs in the map scheme.
    static TabStyle buildIntelTabStyle() {
        return buildTabStyle(INTEL_HEADER_BAND_HEIGHT);
    }

    // The intel-screen anchor, expressed as screen padding so the top-left-anchored layout lands the panel
    // over the lit visor: the box sits flush against the visor's left edge and hangs from the visor top,
    // pushed down by topPadding so it clears the vanilla starscape / fuel-range toggles at the top of the
    // intel map, and its body caps to the visor's bottom edge so the sidebar never runs past the visor (a
    // longer list scrolls within). In UI coordinates (origin bottom-left) the visor's top edge is its y plus
    // its height. The right margin is unused - the sidebar grows rightward across the visor.
    static Padding computeIntelPadding(Rectangle mapVisorRect, float screenHeight, int topPadding) {
        return new Padding(
            Math.round(screenHeight - (mapVisorRect.y() + mapVisorRect.height())) + topPadding,
            0,
            Math.round(mapVisorRect.y()),
            Math.round(mapVisorRect.x()));
    }

    // Lays the panel out for the given anchor, tab style, and controller - the one path both host entry
    // points share, so the map and intel panels are the same layout differing only in where they anchor and
    // how tall they stand their tab band. Returns null when the tab font cannot load - the layout snaps tabs
    // to measured text and cannot run without it - so the caller draws nothing and consumes nothing that
    // frame.
    private static TabPanelPlacement resolvePlacement(
            Padding padding,
            TabStyle tabStyle,
            TabPanelController controller,
            ActiveLayerSelection selection,
            Set<BoxEdge> borderedEdges) {

        var measurer = loadTabMeasurer();
        if (measurer == null) {
            return null;
        }
        var settings = Global.getSettings();
        var layers = MapLayerRegistry.getLayers();

        // The active layer comes from the calling screen's own selection, not one shared value, so the lit
        // tab and the body are this screen's pick and a switch here never moves the other screen's tab.
        var activeLayer = selection.getActiveLayer();

        var placement = TabPanelLayout.computePlacement(
            settings.getScreenHeight(),
            padding,
            // The player's border width over the edges the host frames; a dropped edge collapses its
            // reserved inset so the box sits flush against the neighbour the host meant to blend into.
            new BoxBorder(KmuMapLayerSettings.getMapSidebarBorderWidth(), borderedEdges),
            tabStyle,
            buildTabsSpec(layers, activeLayer, selection),
            activeLayer.getBodyControls(),
            measurer,
            // The live scroll and fold, so the body lays out at its interpolated width and the notch
            // rides the shrinking edge; the render pass advances the fold each frame and both passes
            // resolve against the same value, so the drawn fold and the hit-tested notch line up.
            new TabPanelViewState(
                controller.getScrollState().getOffset(),
                controller.getCollapseFraction()));

        // Settle the stored scroll request into the list's real range now the layout has resolved the
        // overflow, so a wheel past the bottom or a list that shrank does not leave it drifting. Both
        // the render and input passes call this each frame, so the stored offset stays bounded.
        controller
            .getScrollState()
            .clampTo(placement.body().scrollOverflow());

        return placement;
    }

    // The on-map anchor: hang from the screen top-left by the player's padding. The right margin is
    // unused - the sidebar grows rightward to fit the widest content.
    private static Padding buildMapPadding() {
        return new Padding(
            KmuMapLayerSettings.getMapSidebarPaddingTop(),
            0,
            KmuMapLayerSettings.getMapSidebarPaddingBottom(),
            KmuMapLayerSettings.getMapSidebarPaddingLeft());
    }

    // The intel-screen anchor from the live screen height and the player's top padding; the anchor math
    // (flush-left, hung from the visor top minus the padding, capped to the visor bottom) is computeIntelPadding's.
    private static Padding buildIntelPadding(Rectangle mapVisorRect) {
        return computeIntelPadding(
            mapVisorRect,
            Global.getSettings().getScreenHeight(),
            KmuMapLayerSettings.getMapIntelSidebarPaddingTop());
    }

    // A tab style at the given band height, over the shared paint: the vanilla map-tab colour scheme,
    // resolved live so it tracks a player-faction recolour, the underlined-key hotkey convention, and the
    // orbitron face at the layout's tab size - the same face the measurer snaps tabs with. One helper
    // rather than each screen naming the paint, so the two can only differ in the height they are asked
    // for.
    // The underlined key is the sector map's own convention: our strip sits one tab-height below the
    // vanilla Sector/System tabs, which draw a line under the bracketed letter, so a bare key reads as a
    // mismatch against the row above it. The line costs no width - it is a quad under a glyph, not part
    // of the measured display string - so no tab moves for it.
    private static TabStyle buildTabStyle(float headerBandHeight) {
        return new TabStyle(
            headerBandHeight,
            VanillaTabColours.mapTabs(),
            HotkeyStyle.createUnderlined(),
            new TextFace(TAB_FONT, TabsControlLayout.TAB_FONT_SIZE));
    }

    // Builds the layer selector as one tabs control: each layer's label and current shortcut key in
    // registry order, the active layer lit, and an action that selects the layer at the clicked index.
    // Baking the switch into the action means the placement's tabs, the renderer's lit index, and the
    // click all index the same registry row, and no separate tab callback is threaded through the input.
    private static ControlSpec.Tabs buildTabsSpec(
            List<MapLayer> layers,
            MapLayer activeLayer,
            ActiveLayerSelection selection) {

        var labels = new ArrayList<String>(layers.size());
        var shortcuts = new ArrayList<String>(layers.size());
        for (var layer : layers) {
            labels.add(KmuStrings.get(layer.getTabLabelKey()));
            shortcuts.add(resolveShortcutName(layer));
        }
        return new ControlSpec.Tabs(
            labels,
            shortcuts,
            layers.indexOf(activeLayer),
            cell -> selection.selectLayer(layers.get(cell)));
    }

    // The display name of a layer's shortcut key, or null when it has none - an unbound keycode (0,
    // cleared with Escape; LWJGL still names it "NONE") or a code LWJGL cannot name. A null/blank
    // shortcut leaves the tab label alone.
    private static String resolveShortcutName(MapLayer layer) {

        var keycode = KmuMapLayerSettings.getMapLayerShortcut(
            layer.getShortcutSettingKey(),
            layer.getDefaultShortcutKeycode());

        if (keycode <= 0) {
            return null;
        }
        return Keyboard.getKeyName(keycode);
    }

    // The tab font wrapped as a width measurer, or null when it cannot load. One measurer serves
    // both the tab and body snapping in the layout: body labels drawn in the narrower insignia face
    // fit inside boxes snapped to this face, so the body reads correctly and only the tabs, drawn in
    // this same face, need it to match exactly.
    private static LineWidthMeasurer loadTabMeasurer() {
        var font = LazyFontCache.loadByFace(TAB_FONT);
        if (font == null) {
            return null;
        }
        return new LazyFontMeasurer(font);
    }
}
