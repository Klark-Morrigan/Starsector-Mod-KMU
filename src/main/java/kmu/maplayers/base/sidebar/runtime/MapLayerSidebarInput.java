package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.input.InputEventAPI;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.controls.Control;
import kmlib.starsector.ui.controls.ControlKind;
import kmlib.starsector.ui.map.CampaignMapView;
import kmlib.starsector.ui.widgets.PanelPlacement;
import kmlib.starsector.ui.widgets.RadioRow;
import kmlib.starsector.ui.widgets.TabPanel;
import kmlib.starsector.ui.widgets.TabPanelHotkeys;
import kmlib.starsector.ui.widgets.TabStrip;

import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.sidebar.LiveSidebarPlacement;
import kmu.maplayers.base.sidebar.SidebarScrollState;
import kmu.maplayers.base.sidebar.SidebarScrollbar;
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

    // Pixels one wheel notch scrolls the bloc list. Only the wheel's sign is read (like the vanilla
    // scroll lists), so each notch moves this fixed step regardless of the raw wheel magnitude - about
    // two list rows, a comfortable step without overshooting a short list.
    private static final float SCROLL_STEP_PX = 40f;

    // A scrollbar-thumb drag in progress, and the pointer's offset from the thumb centre when it was
    // grabbed. The drag spans frames (press, moves, release), so it lives as state between events: while
    // set, every mouse move maps the pointer to a scroll position; the grab offset holds the thumb under
    // the cursor so it does not jump when grabbed off-centre.
    private static boolean isDraggingThumb;
    private static float thumbGrabOffsetY;

    @Override
    public int getListenerInputPriority() {
        return INPUT_PRIORITY;
    }

    @Override
    public void processCampaignInputPreCore(List<InputEventAPI> events) {
        // The bar only shows on the sector map with the starscape filter off; off it, its
        // keys and clicks must not fire, so leave every event untouched. A drag left dangling by the
        // overlay closing mid-drag ends here, so a stale grab cannot hijack the next map session.
        if (!CampaignMapView.isSectorMapWithStarscapeOff()) {
            isDraggingThumb = false;
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
    private static void handlePointerOverBar(InputEventAPI event, PanelPlacement placement) {
        // A thumb drag in progress owns the event wherever the pointer is - even past the panel edge -
        // so the list keeps following the cursor until the release, rather than dropping the drag the
        // moment the pointer leaves the narrow scrollbar column.
        if (isDraggingThumb) {
            continueThumbDrag(event, placement);
            return;
        }
        if (!TabPanel.containsPoint(placement.panel(), event.getX(), event.getY())) {
            return;
        }
        // A wheel over the panel scrolls its bloc list rather than zooming the map behind it; a press on
        // the scrollbar's grab column starts a drag; any other left press fires the control under it.
        // Either way the event is consumed below, so the map never also acts on a pointer event the panel
        // handled.
        if (event.isMouseScrollEvent()) {
            scrollListUnderPointer(event, placement);
        } else if (event.isLMBDownEvent()) {
            if (!beginThumbDragIfPressed(event, placement)) {
                actOnLeftPress(placement, event.getX(), event.getY());
            }
        }
        event.consume();
    }

    // Starts a scrollbar drag when a left press lands on the grab column, reporting whether it did. The
    // grab column is the gutter right of the list, wider than the thin track so it need not be hit
    // exactly; a press on the thumb records its offset from the thumb centre so the thumb stays under the
    // cursor, while a press on the bare track jumps the thumb to the pointer at once. Only fires while
    // the list overflows - there is no scrollbar otherwise.
    private static boolean beginThumbDragIfPressed(InputEventAPI event, PanelPlacement placement) {
        if (!placement.isScrollbarNeeded()) {
            return false;
        }
        var track = SidebarScrollbar.computeTrack(placement);
        if (!SidebarScrollbar.computeGrabColumn(placement, track)
                .containsPoint(event.getX(), event.getY())) {
            return false;
        }
        isDraggingThumb = true;
        var thumb = SidebarScrollbar.computeThumb(placement, track);
        thumbGrabOffsetY = thumb.containsPoint(event.getX(), event.getY())
                ? event.getY() - (thumb.y() + thumb.height() / 2f)
                : 0f;
        updateDragOffset(placement, event.getY());
        return true;
    }

    // Follows an in-progress drag: the release ends it, and until then every move maps the pointer to a
    // scroll position. Consumes the event so the map neither pans nor acts while the thumb is held.
    private static void continueThumbDrag(InputEventAPI event, PanelPlacement placement) {
        if (event.isLMBUpEvent()) {
            isDraggingThumb = false;
            event.consume();
            return;
        }
        if (placement.isScrollbarNeeded()) {
            updateDragOffset(placement, event.getY());
        }
        event.consume();
    }

    // Maps the dragged pointer to an absolute scroll offset along the track and stores it, holding the
    // thumb the grab offset below the cursor so it tracks the drag rather than snapping its centre to the
    // pointer.
    private static void updateDragOffset(PanelPlacement placement, float pointerY) {
        var track = SidebarScrollbar.computeTrack(placement);
        SidebarScrollState.setOffset(
                SidebarScrollbar.resolveOffsetForPointer(placement, track, pointerY - thumbGrabOffsetY));
    }

    // Scrolls the bloc list when the wheel turns over its scroll region and it has somewhere to scroll.
    // Only the wheel's sign is read (like the vanilla scroll lists): a wheel up scrolls toward the list
    // top, so it decreases the offset, and a wheel down increases it, each by one fixed step. Off the
    // scroll region (over the pinned header, or a list that fits) the wheel does nothing, though the
    // caller still consumes it so the map does not zoom under the panel.
    private static void scrollListUnderPointer(InputEventAPI event, PanelPlacement placement) {
        if (!placement.isScrollbarNeeded()
                || !placement.flexViewport().containsPoint(event.getX(), event.getY())) {
            return;
        }
        SidebarScrollState.scrollBy(-Math.signum((float) event.getEventValue()) * SCROLL_STEP_PX);
    }

    // Routes a left press inside the box to what sits under it: a tab selects its layer, otherwise a
    // body control fires its action. A press on the border or blank body falls through to neither and
    // only consumes (handled by the caller), so empty chrome swallows the click without acting.
    private static void actOnLeftPress(PanelPlacement placement, float pointX, float pointY) {
        var tabIndex = TabPanel.findTabIndexAt(placement.panel(), pointX, pointY);
        if (tabIndex != TabStrip.NO_TAB) {
            MapLayerRegistry.selectLayer(MapLayerRegistry.getLayers().get(tabIndex));
            return;
        }
        for (var control : placement.bodyControls()) {
            if (activateControlIfHit(control, placement.flexViewport(), pointX, pointY)) {
                return;
            }
        }
    }

    // Fires the control's action if the press lands on an actionable cell, and reports whether it
    // did. A radio hits by segment over the segments the layout already laid (so the hit rects are
    // the drawn ones regardless of the flow direction). A radio whose reselect behaviour swallows a
    // re-pick (a standard option pair) treats a press on the already-lit segment as inert - re-
    // picking changes nothing - so findHitElement folds that rule in and fires nothing. A radio that
    // fires on a re-pick (the deselectable view selector and filter picker, the re-firing sort
    // selector) instead reads the raw hit so a press on the lit segment reaches its action - to clear
    // the selection or to flip its sub-state. A single-cell checkbox or toggle hits anywhere on its
    // row, reported as cell 0, and flips on every press. A caption label is not a hit target and is
    // skipped. The action's meaning stays with the tab that supplied it - this only maps the click to
    // a cell.
    static boolean activateControlIfHit(Control control, Rectangle flexViewport, float pointX,
            float pointY) {
        // The scrolling list only counts inside its viewport: a row scrolled up under the pinned header
        // (or down under the footer) is clipped from view, so its segment - still laid out at its
        // scrolled position - must not be clickable through the header or footer that hides it. Only the
        // one scrolling control is clipped; every other control ignores the viewport.
        if (control.spec().scrolls() && !flexViewport.containsPoint(pointX, pointY)) {
            return false;
        }
        // A caption row and a divider are drawn but not clickable, so a press over either hits nothing
        // and falls through to let the loop try the controls below - never consuming a click as if it
        // acted. The divider matters here because it spans the whole body width: without this guard a
        // press anywhere on its row would be swallowed on its inert action.
        if (control.spec().kind() == ControlKind.LABEL
                || control.spec().kind() == ControlKind.DIVIDER) {
            return false;
        }
        // A radio hits by segment over the segments the layout laid - the icon-list picker included,
        // since it is a vertical radio that shares the same segment hit-test. The reselect behaviour
        // then selects raw-hit (a re-pick fires) vs already-lit handling (a re-pick is swallowed).
        if (control.spec().kind() == ControlKind.RADIO) {
            var segmentIndex = control.spec().reselect().firesOnReselect()
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
