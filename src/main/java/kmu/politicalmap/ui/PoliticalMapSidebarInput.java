package kmu.politicalmap.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.input.InputEventAPI;

import kmlib.starsector.ui.map.CampaignMapView;

import kmu.politicalmap.layer.PoliticalMapLayer;
import kmu.politicalmap.layer.PoliticalMapLayers;
import kmu.settings.KmuLunaSettings;

import java.util.List;

/**
 * Owns the layer bar's input: a click on a tab switches layers, a bound key jumps straight to
 * its layer, and every pointer event over the bar is swallowed so the map behind it does not
 * also act on the click. The renderer paints the bar but a render pass cannot consume input;
 * this listener runs in {@code processCampaignInputPreCore}, which fires before the map's own
 * widgets each frame the map is open, so consuming here stops the click reaching the map -
 * without it a tab click also hit the map's Sector/System tabs and stars behind the bar
 * stayed clickable.
 *
 * <p>It reads the same {@link SidebarLayout} placement the renderer draws, so the rectangle it
 * hit-tests is the rectangle the player sees. It only acts on the sector map with the
 * starscape filter off, where the bar shows, so its keys and clicks are inert everywhere else.
 */
public final class PoliticalMapSidebarInput implements CampaignInputListener {
    // Run ahead of the core map and of other mods' listeners, so a tab click or hotkey is
    // consumed before anything else claims it.
    private static final int INPUT_PRIORITY = 1000;

    @Override
    public int getListenerInputPriority() {
        return INPUT_PRIORITY;
    }

    @Override
    public void processCampaignInputPreCore(List<InputEventAPI> events) {
        // The bar only shows on the sector map with the starscape filter off; off it, its
        // keys and clicks must not fire, so leave every event untouched.
        if (!CampaignMapView.isSectorMapWithStarscapeOff()) {
            return;
        }
        var placement = computeCurrentPlacement();
        var activeLayer = PoliticalMapLayers.getActiveLayer();
        for (var event : events) {
            if (event.isConsumed()) {
                continue;
            }
            if (event.isKeyDownEvent()) {
                switchToBoundLayer(event);
            } else if (event.isMouseEvent()) {
                handlePointerOverBar(event, placement, activeLayer);
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

    // Lays the bar out for the live screen and anchor - the same call the renderer makes, so
    // the hit-tested rectangles match the drawn ones exactly.
    private static SidebarPlacement computeCurrentPlacement() {
        var settings = Global.getSettings();
        return SidebarLayout.computePlacement(settings.getScreenWidth(),
                settings.getScreenHeight(), KmuLunaSettings.getPoliticalMapSidebarAnchor(),
                PoliticalMapLayers.getLayers());
    }

    // Switches to the layer whose bound key was pressed and consumes the event, so the key
    // does not also trigger a campaign binding sharing it.
    private static void switchToBoundLayer(InputEventAPI event) {
        for (var layer : PoliticalMapLayers.getLayers()) {
            var keycode = KmuLunaSettings.getPoliticalMapLayerShortcut(
                    layer.getShortcutSettingKey(), layer.getDefaultShortcutKeycode());
            // A cleared shortcut is stored as keycode 0 (LWJGL's KEY_NONE); skip it so an
            // unbound layer never claims a keypress - otherwise a stray 0-valued event could.
            if (keycode <= 0) {
                continue;
            }
            if (event.getEventValue() == keycode) {
                PoliticalMapLayers.selectLayer(layer);
                event.consume();
                return;
            }
        }
    }

    // Consumes any pointer event over the drawn bar, switching layers on a left press that
    // lands on a tab. Consuming the whole footprint - not just the click - keeps the map from
    // hovering a star or reading the bar as empty margin under the cursor.
    private static void handlePointerOverBar(InputEventAPI event, SidebarPlacement placement,
            PoliticalMapLayer activeLayer) {
        if (!isOverDrawnBar(event, placement, activeLayer)) {
            return;
        }
        if (event.isLMBDownEvent()) {
            for (var tab : placement.tabs()) {
                if (tab.bounds().containsPoint(event.getX(), event.getY())) {
                    PoliticalMapLayers.selectLayer(tab.layer());
                    break;
                }
            }
        }
        event.consume();
    }

    // Whether the cursor is over what the bar actually draws: any tab, or the caption strip
    // only while the active layer shows a caption. The reserved caption space is not counted
    // when nothing is drawn there, so it is not a dead zone that eats clicks over blank map.
    private static boolean isOverDrawnBar(InputEventAPI event, SidebarPlacement placement,
            PoliticalMapLayer activeLayer) {
        for (var tab : placement.tabs()) {
            if (tab.bounds().containsPoint(event.getX(), event.getY())) {
                return true;
            }
        }
        return activeLayer.getCaptionKey() != null
                && placement.caption().containsPoint(event.getX(), event.getY());
    }
}
