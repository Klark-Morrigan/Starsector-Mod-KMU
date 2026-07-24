package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.input.InputEventAPI;

import kmlib.starsector.ui.input.TabPanelController;
import kmlib.starsector.ui.map.CampaignMapView;
import kmlib.starsector.ui.render.gl.BoxEdge;
import kmlib.starsector.ui.widgets.tabs.TabPanelHotkeys;
import kmlib.starsector.ui.widgets.tabs.TabPanelPlacement;
import kmlib.starsector.ui.widgets.tabs.TabStrip;

import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.sidebar.LiveSidebarPlacement;
import kmu.settings.KmuLunaSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The sector map's binding for the political-map sidebar: it shows while the sector map is up with the
 * starscape filter off, hangs the panel from the screen top-left, and jumps to a layer on its bound shortcut
 * key. This is the on-map sibling of {@link IntelSidebarHost}; the two differ only in gate, anchor,
 * controller, and keyboard role, and both feed the shared {@link SidebarRenderer} / {@link SidebarInput}.
 *
 * <p>Its panel opens expanded, since the on-map sidebar is the player's primary way in to the political map
 * and has the screen width to sit open. The bound layer shortcuts are the on-map host's alone - the intel
 * host has none - so the key-to-layer jump lives here, not in the shared input listener.
 */
public final class MapSidebarHost implements SidebarHost {
    /** The one on-map host; the render and input listeners registered for the sector map reference it. */
    public static final MapSidebarHost INSTANCE = new MapSidebarHost();

    // The on-map panel's scroll and collapse state, opening expanded. Held here so the map panel keeps its
    // own state, separate from the intel panel's.
    private final TabPanelController controller = new TabPanelController();

    private MapSidebarHost() {
    }

    @Override
    public boolean isOverlayShowing() {
        return CampaignMapView.isSectorMapWithStarscapeOff();
    }

    @Override
    public TabPanelPlacement resolvePlacement() {
        return LiveSidebarPlacement.resolveMapPlacement(controller);
    }

    @Override
    public Set<BoxEdge> resolveBorderEdges(TabPanelPlacement placement) {
        // The on-map sidebar floats free on the screen, touching no other panel's edge, so it frames all
        // four sides.
        return BoxEdge.ALL;
    }

    @Override
    public TabPanelController getController() {
        return controller;
    }

    @Override
    public void handleKeyPress(InputEventAPI event) {
        switchToBoundLayer(event);
    }

    @Override
    public String describeViewState() {
        return CampaignMapView.describeViewState();
    }

    // Switches to the layer whose bound key was pressed and consumes the event, so the key does not also
    // trigger a campaign binding sharing it. The pure key-to-tab mapping (skipping unbound layers) is the
    // reusable panel binder; KMU owns only where the keycodes come from (the layer settings) and what
    // selecting means (the layer registry).
    private static void switchToBoundLayer(InputEventAPI event) {
        var layers = MapLayerRegistry.getLayers();
        var tabIndex = TabPanelHotkeys.findTabForKey(
                event.getEventValue(),
                layerKeycodes(layers));
        if (tabIndex == TabStrip.NO_TAB) {
            return;
        }
        MapLayerRegistry.selectLayer(layers.get(tabIndex));
        event.consume();
    }

    // Each layer's bound keycode in registry order, so a matched index maps back to its layer. A cleared
    // shortcut reads as 0 (LWJGL's KEY_NONE); the binder treats a non-positive keycode as unbound and never
    // matches it.
    private static List<Integer> layerKeycodes(List<MapLayer> layers) {
        var keycodes = new ArrayList<Integer>(layers.size());
        for (var layer : layers) {
            keycodes.add(KmuLunaSettings.getPoliticalMapLayerShortcut(
                    layer.getShortcutSettingKey(),
                    layer.getDefaultShortcutKeycode()));
        }
        return keycodes;
    }
}
