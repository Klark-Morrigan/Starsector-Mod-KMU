package kmu.maplayers.base.sidebar;

import com.fs.starfarer.api.Global;

import kmlib.math.geometry.BoxEdge;
import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.controls.ControlSpec;
import kmlib.starsector.ui.font.LazyFontCache;
import kmlib.starsector.ui.font.LazyFontMeasurer;
import kmlib.starsector.ui.font.LineWidthMeasurer;
import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.starsector.ui.input.TabPanelController;
import kmlib.starsector.ui.layout.Padding;
import kmlib.starsector.ui.layout.TabPanelLayout;
import kmlib.starsector.ui.widgets.BoxBorder;
import kmlib.starsector.ui.widgets.tabs.TabPanelPlacement;
import kmlib.starsector.ui.widgets.tabs.TabPanelViewState;
import kmlib.starsector.ui.widgets.tabs.TabStyle;

import kmu.maplayers.base.layer.ActiveLayerSelection;
import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.settings.KmuLunaSettings;
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
 * measurer; the face lives here as the single source both this measurement and the renderer's tab
 * paint read, so a snapped tab width matches the text drawn into it.
 */
public final class LiveSidebarPlacement {
    /**
     * The tabs read in the sector map's own orbitron face - the AA orbitron atlas vanilla uses for
     * its map tabs, scaled to the tab size. Public so the renderer paints the tabs in the same face
     * this measured them in.
     */
    public static final StarsectorFont TAB_FONT = StarsectorFont.VANILLA_ORBITRON_20AA;

    // The two screens size their tab bands differently because they sit in different company. The on-map
    // sidebar floats free beside the vanilla Sector/System tabs and matches their weight, while the intel
    // sidebar overlays the visor under the map toggles and reads tighter so it crowds the preview less.
    // Both heights are content-space: each panel strokes its own top border above the band, so the drawn
    // strip stands the configured border width taller than the number here.
    // Package-private, as the intel anchor math beside them is, so the divergence the two screens depend on
    // is checkable without standing up a live sector.
    static final TabStyle MAP_TAB_STYLE = new TabStyle(19f);
    static final TabStyle INTEL_TAB_STYLE = new TabStyle(17f);

    private LiveSidebarPlacement() {
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
                MAP_TAB_STYLE,
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
                INTEL_TAB_STYLE,
                controller,
                selection,
                borderedEdges);
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
                new BoxBorder(KmuLunaSettings.getPoliticalMapSidebarBorderWidth(), borderedEdges),
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
                KmuLunaSettings.getPoliticalMapSidebarPaddingTop(),
                0,
                KmuLunaSettings.getPoliticalMapSidebarPaddingBottom(),
                KmuLunaSettings.getPoliticalMapSidebarPaddingLeft());
    }

    // The intel-screen anchor from the live screen height and the player's top padding; the anchor math
    // (flush-left, hung from the visor top minus the padding, capped to the visor bottom) is computeIntelPadding's.
    private static Padding buildIntelPadding(Rectangle mapVisorRect) {
        return computeIntelPadding(
                mapVisorRect,
                Global.getSettings().getScreenHeight(),
                KmuLunaSettings.getPoliticalMapIntelSidebarPaddingTop());
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
        var keycode = KmuLunaSettings.getPoliticalMapLayerShortcut(
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
