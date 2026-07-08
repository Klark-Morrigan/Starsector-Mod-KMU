package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.input.InputEventAPI;

import kmlib.math.geometry.Rectangles;
import kmlib.starsector.ui.map.CampaignMapView;
import kmlib.starsector.ui.widgets.VanillaTabStrip;

import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.sidebar.LiveSidebarPlacement;
import kmu.maplayers.base.sidebar.SidebarControl;
import kmu.maplayers.base.sidebar.SidebarControlKind;
import kmu.maplayers.base.sidebar.SidebarPlacement;
import kmu.settings.KmuLunaSettings;

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
 * kmu.maplayers.base.sidebar.SidebarControlAction} - supplied by the tab that built it - does the
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
    // does not also trigger a campaign binding sharing it.
    private static void switchToBoundLayer(InputEventAPI event) {
        for (var layer : MapLayerRegistry.getLayers()) {
            var keycode = KmuLunaSettings.getPoliticalMapLayerShortcut(
                    layer.getShortcutSettingKey(), layer.getDefaultShortcutKeycode());
            // A cleared shortcut is stored as keycode 0 (LWJGL's KEY_NONE); skip it so an
            // unbound layer never claims a keypress - otherwise a stray 0-valued event could.
            if (keycode <= 0) {
                continue;
            }
            if (event.getEventValue() == keycode) {
                MapLayerRegistry.selectLayer(layer);
                event.consume();
                return;
            }
        }
    }

    // Consumes any pointer event over the drawn footprint, acting on a left press that lands on a
    // tab or a body control. The footprint is the placement box - which shrinks to just the bordered
    // tab row when the active tab opens no body - so a No Layer bar reserves no dead zone over blank
    // map, and consuming the whole box keeps the map from hovering a star or reading the bar as empty
    // margin under the cursor.
    private static void handlePointerOverBar(InputEventAPI event, SidebarPlacement placement) {
        if (!placement.box().containsPoint(event.getX(), event.getY())) {
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
        var tabIndex = VanillaTabStrip.findTabIndexAt(placement.tabs(), pointX, pointY);
        if (tabIndex != Rectangles.NONE) {
            MapLayerRegistry.selectLayer(MapLayerRegistry.getLayers().get(tabIndex));
            return;
        }
        for (var control : placement.bodyControls()) {
            if (activateControlIfHit(control, pointX, pointY)) {
                return;
            }
        }
    }

    // Fires the control's action if the press lands on it, and reports whether it did. A radio hits
    // by segment (its cells abut and fill the row, so the segment index is the clicked option); a
    // single-cell checkbox or toggle hits anywhere on its row, reported as cell 0. The action's
    // meaning stays with the tab that supplied it - this only maps the click to a cell.
    private static boolean activateControlIfHit(SidebarControl control, float pointX, float pointY) {
        if (control.spec().kind() == SidebarControlKind.RADIO) {
            var segmentIndex = Rectangles.findIndexContaining(control.segments(), pointX, pointY);
            if (segmentIndex == Rectangles.NONE) {
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
