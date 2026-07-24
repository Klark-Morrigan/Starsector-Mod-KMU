package kmu.maplayers.base.sidebar;

import com.fs.starfarer.api.Global;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.controls.ControlSpec;
import kmlib.starsector.ui.font.LazyFontCache;
import kmlib.starsector.ui.font.LazyFontMeasurer;
import kmlib.starsector.ui.font.LineWidthMeasurer;
import kmlib.starsector.ui.input.TabPanelController;
import kmlib.starsector.ui.layout.Padding;
import kmlib.starsector.ui.layout.TabPanelLayout;
import kmlib.starsector.ui.widgets.tabs.TabPanelPlacement;

import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.settings.KmuLunaSettings;
import kmu.util.KmuStrings;

import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

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
 * <p>The layer selector is one {@link ControlSpec.Tabs} control whose action selects the layer at
 * the clicked index, so the layer switch rides on the control itself and the input listener needs no tab
 * callback. Layout snaps each tab to its measured label, so the placement needs the tab font's width
 * measurer; the font basename lives here as the single source both this measurement and the renderer's
 * tab paint read, so a snapped tab width matches the text drawn into it.
 */
public final class LiveSidebarPlacement {
    /**
     * The tabs read in the sector map's own orbitron face - the AA orbitron atlas vanilla uses for
     * its map tabs, scaled to the tab size. A {@code graphics/fonts} basename the font cache
     * resolves to a loadable path; public so the renderer paints the tabs in the same face this
     * measured them in.
     */
    public static final String TAB_FONT = "orbitron20aa";

    private LiveSidebarPlacement() {
    }

    /**
     * Lays the on-map sidebar out for the current screen, the player's padding, and the active layer's
     * body: it hangs from the screen top-left and grows rightward to fit its content.
     *
     * @param controller the on-map panel's scroll and collapse state
     * @return the placement to draw and hit-test, or {@code null} when the tab font cannot load (see
     *         {@link #resolvePlacement})
     */
    public static TabPanelPlacement resolveMapPlacement(TabPanelController controller) {
        return resolvePlacement(buildMapPadding(), controller);
    }

    /**
     * Lays the intel-screen sidebar out over the lit visor: it anchors to the visor's top-left corner
     * (offset by the player's intel padding) and overlays the visor with the same body the on-map sidebar
     * lays out.
     *
     * @param visorRect  the lit visor's screen rectangle, the corner the panel anchors to
     * @param controller the intel panel's own scroll and collapse state, separate from the on-map panel's
     * @return the placement to draw and hit-test, or {@code null} when the tab font cannot load (see
     *         {@link #resolvePlacement})
     */
    public static TabPanelPlacement resolveIntelPlacement(Rectangle visorRect,
            TabPanelController controller) {
        return resolvePlacement(buildIntelPadding(visorRect), controller);
    }

    // Lays the panel out for the given anchor and controller - the one path both host entry points share,
    // so the map and intel panels are the same layout differing only in where they anchor. Returns null
    // when the tab font cannot load - the layout snaps tabs to measured text and cannot run without it -
    // so the caller draws nothing and consumes nothing that frame.
    private static TabPanelPlacement resolvePlacement(Padding padding, TabPanelController controller) {
        var measurer = loadTabMeasurer();
        if (measurer == null) {
            return null;
        }
        var settings = Global.getSettings();
        var layers = MapLayerRegistry.getLayers();
        var activeLayer = MapLayerRegistry.getActiveLayer();
        var placement = TabPanelLayout.computePlacement(
                settings.getScreenHeight(),
                padding,
                KmuLunaSettings.getPoliticalMapSidebarBorderWidth(),
                buildTabsSpec(layers, activeLayer),
                activeLayer.getBodyControls(),
                measurer,
                controller.getScrollState().getOffset(),
                // The live collapse fraction, so the body lays out at its interpolated width and the notch
                // rides the shrinking edge; the render pass advances it each frame and both passes resolve
                // against the same value, so the drawn fold and the hit-tested notch line up.
                controller.getCollapseFraction());
        // Settle the stored scroll request into the list's real range now the layout has resolved the
        // overflow, so a wheel past the bottom or a list that shrank does not leave it drifting. Both
        // the render and input passes call this each frame, so the stored offset stays bounded.
        controller.getScrollState().clampTo(placement.body().scrollOverflow());
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

    // The intel-screen anchor, expressed as screen padding so the top-left-anchored layout lands the
    // panel over the lit visor: the box hangs from the visor's top-left corner, offset in by the player's
    // intel padding, and its body caps to the visor's bottom edge. In UI coordinates (origin bottom-left)
    // the visor's top edge is its y plus its height, so the box top sits topPadding below that and its
    // left leftPadding in from the visor's left. The right margin is unused - the sidebar grows rightward
    // across the visor.
    private static Padding buildIntelPadding(Rectangle visorRect) {
        var screenHeight = Global.getSettings().getScreenHeight();
        var topPadding = KmuLunaSettings.getPoliticalMapIntelSidebarPaddingTop();
        var leftPadding = KmuLunaSettings.getPoliticalMapIntelSidebarPaddingLeft();
        return new Padding(
                Math.round(screenHeight - (visorRect.y() + visorRect.height())) + topPadding,
                0,
                Math.round(visorRect.y()),
                Math.round(visorRect.x()) + leftPadding);
    }

    // Builds the layer selector as one tabs control: each layer's label and current shortcut key in
    // registry order, the active layer lit, and an action that selects the layer at the clicked index.
    // Baking the switch into the action means the placement's tabs, the renderer's lit index, and the
    // click all index the same registry row, and no separate tab callback is threaded through the input.
    private static ControlSpec.Tabs buildTabsSpec(List<MapLayer> layers, MapLayer activeLayer) {
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
                cell -> MapLayerRegistry.selectLayer(layers.get(cell)));
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
        var font = LazyFontCache.loadByBasename(TAB_FONT);
        if (font == null) {
            return null;
        }
        return new LazyFontMeasurer(font);
    }
}
