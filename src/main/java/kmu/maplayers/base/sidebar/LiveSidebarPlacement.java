package kmu.maplayers.base.sidebar;

import kmlib.math.geometry.BoxEdge;
import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.controls.ControlSpec;
import kmlib.starsector.ui.font.LazyFontCache;
import kmlib.starsector.ui.font.LazyFontMeasurer;
import kmlib.starsector.ui.font.LineWidthMeasurer;
import kmlib.starsector.ui.layout.Padding;
import kmlib.starsector.ui.layout.TabPanelLayout;
import kmlib.starsector.ui.screen.VanillaScreen;
import kmlib.starsector.ui.widgets.BoxBorder;
import kmlib.starsector.ui.widgets.PanelChrome;
import kmlib.starsector.ui.widgets.scroll.ScrollbarThickness;
import kmlib.starsector.ui.widgets.tabs.TabPanelPlacement;
import kmlib.starsector.ui.widgets.tabs.TabPanelViewState;
import kmlib.starsector.ui.widgets.tabs.style.TabStyle;

import kmu.maplayers.base.layer.ActiveLayerSelection;
import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.settings.KmuMapLayerSettings;
import kmu.util.KmuValues;

import org.lwjgl.input.Keyboard;

import java.util.List;
import java.util.Set;

/**
 * Resolves a sidebar's placement from the live screen, settings, and active layer - the one placement
 * the host's renderer draws and its input listener hit-tests, so the two agree on exactly what is on
 * screen. Both passes resolve through here each frame rather than each computing its own, so the tabs a
 * click resolves against are the tabs that were drawn; without a single source the two could drift (a
 * settings change landing between the render and the input pass would move the drawn box out from under
 * the hit-test). The two entry points differ only in where the panel anchors:
 * {@link #resolveMapPlacement} hangs it from the screen top-left for the on-map sidebar, and {@link
 * #resolveIntelPlacement} anchors it to the visor's top-left, overlaying the intel screen's map preview.
 * Both lay out the same body; each takes the host's own {@link TabPanelController}, so the map and intel
 * panels keep separate scroll and collapse state while sharing one layout.
 *
 * <p>How a panel looks is the host's, injected here as a {@link TabStyle} rather than chosen from the
 * screen being laid out: the band a strip stands in is part of a look, so a layout that named the two
 * screens' bands would hold half of each host's look with the other half elsewhere. Taking it as an
 * argument is also what leaves this class with no map-versus-intel branch beyond the anchor.
 *
 * <p>The layer selector is one {@link ControlSpec.Tabs} control whose action selects the layer at
 * the clicked index, so the layer switch rides on the control itself and the input listener needs no tab
 * callback. Layout snaps each tab to its measured label, so the placement needs the tab font's width
 * measurer; the face travels inside the same {@link TabStyle} the renderer paints from, so a snapped tab
 * width matches the text drawn into it.
 */
public final class LiveSidebarPlacement {

    private LiveSidebarPlacement() {
    }

    /**
     * Lays the on-map sidebar out for the current screen, the player's padding, and the active layer's
     * body: it hangs from the screen top-left and grows rightward to fit its content.
     *
     * @param panel the on-map host's own panel: its look, its scroll and fold, its active-layer pick, and
     *              the frame edges it reserves - the on-map sidebar frames all four
     * @return the placement to draw and hit-test, or {@code null} when the tab font cannot load (see
     *         {@link #resolvePlacement})
     */
    public static TabPanelPlacement resolveMapPlacement(SidebarHostPanel panel) {
        return resolvePlacement(buildMapPadding(), panel);
    }

    /**
     * Lays the intel-screen sidebar out over the lit visor: it sits flush against the visor's left edge
     * and hangs from the visor top (pushed down by the player's top padding to clear the vanilla map
     * toggles), overlaying the visor with the same body the on-map sidebar lays out.
     *
     * @param mapVisorRect the lit visor's screen rectangle, the corner the panel anchors to
     * @param panel        the intel host's own panel, separate from the on-map host's throughout - its own
     *                     scroll and fold, its own active-layer pick, so a switch on one screen never moves
     *                     the other's tab - and the frame edges it reserves, which drop the ones it shares
     *                     with the visor so the box sits flush
     * @return the placement to draw and hit-test, or {@code null} when the tab font cannot load (see
     *         {@link #resolvePlacement})
     */
    public static TabPanelPlacement resolveIntelPlacement(
            Rectangle mapVisorRect,
            SidebarHostPanel panel) {
        return resolvePlacement(buildIntelPadding(mapVisorRect), panel);
    }

    // The intel-screen anchor, expressed as screen padding so the top-left-anchored layout lands the panel
    // over the lit visor: the box sits flush against the visor's left edge and hangs from the visor top,
    // pushed down by topPadding so it clears the vanilla Starscape / fuel-range toggles at the top of the
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

    // The row's labels, one per layer in registry order. A blank answer still contributes an entry, so a
    // layer with nothing to say costs its tab its letters and not its place in the row.
    //
    // Taken through the text read that settles absent and blank to the same empty string, because the
    // answer is an arbitrary mod's: the row refuses a null label outright, so one layer returning nothing
    // would take the whole strip down instead of costing itself its letters - the same price the keycode
    // is bounded for, one field over.
    static List<String> resolveTabLabels(List<MapLayer> layers) {
        return layers.stream()
            .map(layer -> KmuValues.getTextOrEmpty(layer.resolveTabLabelText()))
            .toList();
    }

    // The row's shortcut hints, one per layer whether or not it has a key to print. Unlike the labels
    // above, a hint may be absent outright - the control reads a null entry as a tab with no hint - so
    // the collector has to be one that admits nulls.
    static List<String> resolveTabShortcuts(List<MapLayer> layers) {
        return layers.stream()
            .map(LiveSidebarPlacement::resolveShortcutName)
            .toList();
    }

    // Builds the layer selector as one tabs control: each layer's label and current shortcut key in
    // registry order, the active layer lit, and an action that selects the layer at the clicked index.
    // Baking the switch into the action means the placement's tabs, the renderer's lit index, and the
    // click all index the same registry row, and no separate tab callback is threaded through the input.
    //
    // Both halves of the row come off the layers themselves, so the assembly stands up without a settings
    // file or a live registry behind it and what it says can be asked directly. Both are also one entry
    // per layer in registry order, since the control pairs a label to its hint by index - which is why
    // neither half drops a layer that answers nothing, and why the lit index is a position in that same
    // row rather than a search of it.
    static ControlSpec.Tabs buildTabsSpec(
            List<MapLayer> layers,
            MapLayer activeLayer,
            ActiveLayerSelection selection) {

        return new ControlSpec.Tabs(
            resolveTabLabels(layers),
            resolveTabShortcuts(layers),
            resolveLitTabIndex(layers, activeLayer),
            cell -> selection.selectLayer(layers.get(cell)));
    }

    // Lays the panel out for the given anchor and host panel - the one path both host entry points share,
    // so the map and intel panels are the same layout differing only in where they anchor and how tall they
    // stand their tab band. Returns null when the tab font cannot load - the layout snaps tabs to measured
    // text and cannot run without it - so the caller draws nothing and consumes nothing that frame.
    private static TabPanelPlacement resolvePlacement(Padding padding, SidebarHostPanel panel) {

        var tabStyle = panel.tabStyle();
        var measurer = loadTabMeasurer(tabStyle);
        if (measurer == null) {
            return null;
        }
        var layers = MapLayerRegistry.getLayers();

        // The active layer comes from the calling screen's own selection, not one shared value, so the lit
        // tab and the body are this screen's pick and a switch here never moves the other screen's tab.
        var activeLayer = panel.selection().getActiveLayer();
        var controller = panel.controller();

        var placement = TabPanelLayout.computePlacement(
            // The height alone: the panel hangs from a top edge stated as padding, so the layout
            // measures down from the screen's top and never asks where its corner is.
            VanillaScreen.resolveUiHeight(),
            padding,
            buildChrome(panel.borderedEdges()),
            tabStyle,
            buildTabsSpec(layers, activeLayer, panel.selection()),
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

    // What the panel spends on chrome rather than on content, read per frame so a settings change shows on
    // the next one: the player's border width over the edges the host frames - a dropped edge collapses its
    // reserved inset so the box sits flush against the neighbour the host meant to blend into - and the bar
    // thickness the body reserves its gutter for.
    private static PanelChrome buildChrome(Set<BoxEdge> borderedEdges) {
        return new PanelChrome(
            new BoxBorder(KmuMapLayerSettings.getMapSidebarBorderWidth(), borderedEdges),
            // TODO: feed the player's scrollbar thickness here once the setting exists; the default keeps
            // the bar the width it has always drawn at until then.
            ScrollbarThickness.DEFAULT);
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
            VanillaScreen.resolveUiHeight(),
            KmuMapLayerSettings.getMapIntelSidebarPaddingTop());
    }

    // Which tab is lit: the active layer's row in the registry, or no tab at all when it holds none. A
    // pick can outlive its layer - the mod that registered it is gone from the load order, and the screen
    // is still holding the layer it last chose - and a row with nothing to light is the honest answer for
    // that frame, the save migrations being what settles the pick itself.
    //
    // Stated rather than left to indexOf, which answers -1 for the same case and happens to agree with
    // NO_SELECTION. That agreement is a coincidence of two unrelated conventions sharing a number, and it
    // is doing real work here, so it is spelled rather than relied on.
    private static int resolveLitTabIndex(List<MapLayer> layers, MapLayer activeLayer) {

        var index = layers.indexOf(activeLayer);
        return index < 0 ? ControlSpec.NO_SELECTION : index;
    }

    // The display name of the key a layer answers to, or null when it has none: an unbound keycode,
    // which LWJGL would otherwise name "NONE", or a code it holds no name for.
    //
    // The upper bound is the price of taking an arbitrary mod's number: LWJGL indexes its name table by
    // keycode with no range check of its own, so a layer answering past its end would take the whole tab
    // row down rather than cost itself a hint.
    private static String resolveShortcutName(MapLayer layer) {

        var keycode = layer.resolveShortcutKeycode();

        if (keycode <= 0 || keycode >= Keyboard.KEYBOARD_SIZE) {
            return null;
        }
        return Keyboard.getKeyName(keycode);
    }

    // The tab font wrapped as a width measurer, or null when it cannot load. Taken off the injected style
    // rather than named here, so the face the tabs are snapped to is by construction the face they are
    // painted in. One measurer serves both the tab and body snapping in the layout: body labels drawn in
    // the narrower insignia face fit inside boxes snapped to this face, so the body reads correctly and
    // only the tabs, drawn in this same face, need it to match exactly.
    private static LineWidthMeasurer loadTabMeasurer(TabStyle tabStyle) {
        var font = LazyFontCache.loadByFace(tabStyle.face().font());
        if (font == null) {
            return null;
        }
        return new LazyFontMeasurer(font);
    }
}
