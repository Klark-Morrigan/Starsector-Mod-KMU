package kmu.maplayers.base.sidebar;

import com.fs.starfarer.api.Global;

import kmlib.starsector.ui.controls.ControlSpec;
import kmlib.starsector.ui.font.LazyFontCache;
import kmlib.starsector.ui.font.LazyFontMeasurer;
import kmlib.starsector.ui.font.LineWidthMeasurer;
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
 * Resolves the sidebar's placement from the live screen, settings, and active layer - the one
 * placement the renderer draws and the input listener hit-tests, so the two agree on exactly what
 * is on screen. Both passes call this each frame rather than each computing its own, so the tabs a
 * click resolves against are the tabs that were drawn; without a single source the two could drift
 * (a settings change landing between the render and the input pass would move the drawn box out
 * from under the hit-test).
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
     * Lays the sidebar out for the current screen, padding, border, registered layers, and the
     * active layer's body controls.
     *
     * @return the placement to draw and hit-test, or {@code null} when the tab font cannot load -
     *         the layout snaps tabs to measured text and cannot run without it, so the caller draws
     *         nothing and consumes nothing that frame
     */
    public static TabPanelPlacement resolveCurrentPlacement() {
        var measurer = loadTabMeasurer();
        if (measurer == null) {
            return null;
        }
        var settings = Global.getSettings();
        var layers = MapLayerRegistry.getLayers();
        var activeLayer = MapLayerRegistry.getActiveLayer();
        // The right margin is unused - the sidebar grows rightward to fit the widest content.
        var padding = new Padding(
                KmuLunaSettings.getPoliticalMapSidebarPaddingTop(),
                0,
                KmuLunaSettings.getPoliticalMapSidebarPaddingBottom(),
                KmuLunaSettings.getPoliticalMapSidebarPaddingLeft());
        var placement = TabPanelLayout.computePlacement(
                settings.getScreenHeight(),
                padding,
                KmuLunaSettings.getPoliticalMapSidebarBorderWidth(),
                buildTabsSpec(layers, activeLayer),
                activeLayer.getBodyControls(),
                measurer,
                SidebarPanelController.INSTANCE.getScrollState().getOffset(),
                // The sidebar stays fully expanded until the collapse handle is wired to it; 0 = no
                // collapse.
                0f);
        // Settle the stored scroll request into the list's real range now the layout has resolved the
        // overflow, so a wheel past the bottom or a list that shrank does not leave it drifting. Both
        // the render and input passes call this each frame, so the stored offset stays bounded.
        SidebarPanelController.INSTANCE
                .getScrollState()
                .clampTo(placement.body().scrollOverflow());
        return placement;
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
