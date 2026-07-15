package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.input.InputEventAPI;

import kmlib.starsector.ui.map.CampaignMapView;
import kmlib.starsector.ui.widgets.tabs.TabPanelHotkeys;
import kmlib.starsector.ui.widgets.tabs.TabStrip;

import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.sidebar.LiveSidebarPlacement;
import kmu.maplayers.base.sidebar.SidebarPanelController;
import kmu.settings.KmuLunaSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * The map sidebar's input listener shell: it gates when the panel is live, resolves the placement the
 * renderer drew, and feeds pointer events to the reusable KMLib {@link
 * kmlib.starsector.ui.input.TabPanelController} - which routes a header tab press to that tab's own action
 * and drives the thumb drag, the wheel scroll, and body control hits - while owning the one thing KMLib
 * cannot: the hotkeys (keycodes from settings, jump straight to a layer). What a tab click means (select
 * that layer) now rides on the tabs control's action, built in {@link LiveSidebarPlacement}, not here.
 *
 * <p>A render pass cannot consume input, so this listener runs in {@code processCampaignInputPreCore},
 * which fires before the map's own widgets each frame the map is open; consuming there stops a click
 * reaching the map. It only acts on the sector map with the starscape filter off, where the bar shows, so
 * its keys and clicks are inert everywhere else.
 */
public final class MapLayerSidebarInput implements CampaignInputListener {
    // Run ahead of the core map and of other mods' listeners, so a tab click or hotkey is consumed before
    // anything else claims it.
    private static final int INPUT_PRIORITY = 1000;

    @Override
    public int getListenerInputPriority() {
        return INPUT_PRIORITY;
    }

    @Override
    public void processCampaignInputPreCore(List<InputEventAPI> events) {
        // The bar only shows on the sector map with the starscape filter off; off it, its keys and clicks
        // must not fire, so leave every event untouched. A drag left dangling by the overlay closing
        // mid-drag ends here, so a stale grab cannot hijack the next map session.
        if (!CampaignMapView.isSectorMapWithStarscapeOff()) {
            SidebarPanelController.INSTANCE.cancelDrag();
            return;
        }
        // The placement the renderer drew this frame; null when the tab font could not load, in which case
        // the bar is not on screen, so there is nothing to hit-test. Hotkeys still work, since they need no
        // placement to jump to a layer.
        var placement = LiveSidebarPlacement.resolveCurrentPlacement();
        for (var event : events) {
            if (event.isConsumed()) {
                continue;
            }
            if (event.isKeyDownEvent()) {
                switchToBoundLayer(event);
            } else if (event.isMouseEvent() && placement != null) {
                SidebarPanelController.INSTANCE.handlePointer(event, placement);
            }
        }
    }

    @Override
    public void processCampaignInputPreFleetControl(List<InputEventAPI> events) {
        // The bar takes no part in fleet control.
    }

    @Override
    public void processCampaignInputPostCore(List<InputEventAPI> events) {
        // Nothing runs after the core map for the bar; all its input is claimed pre-core.
    }

    // Switches to the layer whose bound key was pressed and consumes the event, so the key does not also
    // trigger a campaign binding sharing it. The pure key-to-tab mapping (skipping unbound layers) is the
    // reusable panel binder; KMU owns only where the keycodes come from (the layer settings) and what
    // selecting means (the layer registry).
    private static void switchToBoundLayer(InputEventAPI event) {
        var layers = MapLayerRegistry.getLayers();
        var tabIndex = TabPanelHotkeys.findTabForKey(event.getEventValue(), layerKeycodes(layers));
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
                    layer.getShortcutSettingKey(), layer.getDefaultShortcutKeycode()));
        }
        return keycodes;
    }
}
