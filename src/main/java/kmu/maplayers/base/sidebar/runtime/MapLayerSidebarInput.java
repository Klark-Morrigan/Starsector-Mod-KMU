package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.input.InputEventAPI;

import kmlib.starsector.ui.controls.Control;
import kmlib.starsector.ui.controls.ControlKind;
import kmlib.starsector.ui.map.CampaignMapView;
import kmlib.starsector.ui.widgets.RadioRow;
import kmlib.starsector.ui.widgets.TabPanel;
import kmlib.starsector.ui.widgets.TabPanelHotkeys;
import kmlib.starsector.ui.widgets.TabStrip;

import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.sidebar.LiveSidebarPlacement;
import kmu.maplayers.base.sidebar.SidebarPlacement;
import kmu.settings.KmuLunaSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the layer bar's input: a click on a tab switches layers, a click on a body control fires
 * that control's action, a bound key jumps straight to its layer, and every pointer event over the
 * whole drawn footprint is swallowed so the map behind it does not also act on the click. The
 * renderer paints the bar but a render pass cannot consume input; this listener runs in
 * {@code processCampaignInputPreCore}, which fires before the map's own widgets each frame the map
 * is open, so consuming here stops the click reaching the map - without it a tab click also hit the
 * map's Sector/System tabs and stars behind the bar stayed clickable.
 *
 * <p>It hit-tests the same {@link LiveSidebarPlacement} the renderer draws, so the rectangles it
 * reads are the rectangles the player sees. It stays agnostic to what a body control means: a click
 * resolves to a control and a cell, and the control's own {@link
 * kmlib.starsector.ui.controls.ControlAction} - supplied by the tab that built it - does the
 * rest, so this dispatches a faction toggle or a future alliances control the same way. It only
 * acts on the sector map with the starscape filter off, where the bar shows, so its keys and clicks
 * are inert everywhere else.
 */
public final class MapLayerSidebarInput implements CampaignInputListener {
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
        // The placement the renderer drew this frame; null when the tab font could not load, in
        // which case the bar is not on screen, so there is nothing to hit-test. Hotkeys still work,
        // since they need no placement to jump to a layer.
        var placement = LiveSidebarPlacement.resolveCurrentPlacement();
        for (var event : events) {
            if (event.isConsumed()) {
                continue;
            }
            if (event.isKeyDownEvent()) {
                switchToBoundLayer(event);
            } else if (event.isMouseEvent() && placement != null) {
                handlePointerOverBar(event, placement);
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

    // Switches to the layer whose bound key was pressed and consumes the event, so the key
    // does not also trigger a campaign binding sharing it. The pure key-to-tab mapping (skipping
    // unbound layers) is the reusable panel binder; KMU owns only where the keycodes come from
    // (the layer settings) and what selecting means (the layer registry).
    private static void switchToBoundLayer(InputEventAPI event) {
        var layers = MapLayerRegistry.getLayers();
        var tabIndex = TabPanelHotkeys.findTabForKey(event.getEventValue(), layerKeycodes(layers));
        if (tabIndex == TabStrip.NO_TAB) {
            return;
        }
        MapLayerRegistry.selectLayer(layers.get(tabIndex));
        event.consume();
    }

    // Each layer's bound keycode in registry order, so a matched index maps back to its layer. A
    // cleared shortcut reads as 0 (LWJGL's KEY_NONE); the binder treats a non-positive keycode as
    // unbound and never matches it.
    private static List<Integer> layerKeycodes(List<MapLayer> layers) {
        var keycodes = new ArrayList<Integer>(layers.size());
        for (var layer : layers) {
            keycodes.add(KmuLunaSettings.getPoliticalMapLayerShortcut(
                    layer.getShortcutSettingKey(), layer.getDefaultShortcutKeycode()));
        }
        return keycodes;
    }

    // Consumes any pointer event over the drawn footprint, acting on a left press that lands on a
    // tab or a body control. The footprint is the placement box - which shrinks to just the bordered
    // tab row when the active tab opens no body - so a No Layer bar reserves no dead zone over blank
    // map, and consuming the whole box keeps the map from hovering a star or reading the bar as empty
    // margin under the cursor.
    private static void handlePointerOverBar(InputEventAPI event, SidebarPlacement placement) {
        if (!TabPanel.containsPoint(placement.panel(), event.getX(), event.getY())) {
            return;
        }
        if (event.isLMBDownEvent()) {
            actOnLeftPress(placement, event.getX(), event.getY());
        }
        event.consume();
    }

    // Routes a left press inside the box to what sits under it: a tab selects its layer, otherwise a
    // body control fires its action. A press on the border or blank body falls through to neither and
    // only consumes (handled by the caller), so empty chrome swallows the click without acting.
    private static void actOnLeftPress(SidebarPlacement placement, float pointX, float pointY) {
        var tabIndex = TabPanel.findTabIndexAt(placement.panel(), pointX, pointY);
        if (tabIndex != TabStrip.NO_TAB) {
            MapLayerRegistry.selectLayer(MapLayerRegistry.getLayers().get(tabIndex));
            return;
        }
        for (var control : placement.bodyControls()) {
            if (activateControlIfHit(control, pointX, pointY)) {
                return;
            }
        }
    }

    // Fires the control's action if the press lands on an actionable cell, and reports whether it
    // did. A radio hits by segment over the segments the layout already laid (so the hit rects are
    // the drawn ones regardless of the flow direction). A standard radio treats a press on the
    // already-lit segment as inert - re-picking the selected option changes nothing - so
    // findHitElement folds that rule in and fires nothing. A deselectable radio (the view selector)
    // instead reads the raw hit so a press on the lit segment reaches its action and turns the
    // control off. A single-cell checkbox or toggle hits anywhere on its row, reported as cell 0,
    // and flips on every press. A caption label is not a hit target and is skipped. The action's
    // meaning stays with the tab that supplied it - this only maps the click to a cell.
    static boolean activateControlIfHit(Control control, float pointX, float pointY) {
        // A caption row is drawn but not clickable, so a press over it hits nothing and falls through
        // to let the loop try the controls below - never consuming a click as if it acted.
        if (control.spec().kind() == ControlKind.LABEL) {
            return false;
        }
        // A radio hits by segment over the segments the layout laid - the icon-list picker included,
        // since it is a deselectable vertical radio and shares the same segment hit-test. canDeselect
        // then selects raw-hit vs already-lit handling.
        if (control.spec().kind() == ControlKind.RADIO) {
            var segmentIndex = control.spec().canDeselect()
                    ? RadioRow.findSegmentIndexAt(control.segments(), pointX, pointY)
                    : RadioRow.findHitElement(control.segments(), control.spec().selectedIndex(),
                            pointX, pointY);
            if (segmentIndex == RadioRow.NO_SEGMENT) {
                return false;
            }
            control.spec().action().activateCell(segmentIndex);
            return true;
        }
        if (!control.bounds().containsPoint(pointX, pointY)) {
            return false;
        }
        control.spec().action().activateCell(0);
        return true;
    }
}
