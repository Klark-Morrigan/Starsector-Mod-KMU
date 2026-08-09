package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.input.InputEventAPI;

import kmlib.starsector.ui.input.ParkedPointerEvent;
import kmlib.starsector.ui.widgets.tabs.TabPanelPlacement;

import java.util.List;

/**
 * Feeds pointer and key input to one {@link SidebarHost}'s sidebar panel as a campaign input listener: it
 * gates on the host, resolves the placement the renderer drew, and routes each event - a key press to the
 * host (which jumps to the layer it is bound to, if any) only while the panel is presenting the tabs that
 * key would switch between, so a docked or animating panel's hotkeys stay inert, and a pointer event to the
 * host's reusable KMLib {@link kmlib.starsector.ui.input.TabPanelController}, which routes a header tab
 * press, the notch toggle, the thumb drag, the wheel scroll, and body control hits. One instance per host,
 * so the sector map and the intel screen each route to their own panel.
 *
 * <p>A render pass cannot consume input, so this listener runs in {@code processCampaignInputPreCore}, which
 * fires before the screen's own widgets each frame it is open; consuming there stops a click reaching the
 * screen. It only acts while the host says the sidebar is live, so its keys and clicks are inert everywhere
 * else.
 */
public final class SidebarInput implements CampaignInputListener {

    // Run ahead of the core screen and of other mods' listeners, so a tab click or notch press is consumed
    // before anything else claims it.
    private static final int INPUT_PRIORITY = 1000;

    // Whether the frame's event list takes a replacement at all. The engine hands this listener the list
    // it reads back afterwards, but nothing in the API promises it may be written to, so a refusal is
    // settled once and not met again every frame the pointer sits on the panel.
    private boolean isEventListWritable = true;

    // The screen this listener routes input to: its gate, placement, controller, and key role.
    private final SidebarHost host;

    public SidebarInput(SidebarHost host) {
        this.host = host;
    }

    @Override
    public int getListenerInputPriority() {
        return INPUT_PRIORITY;
    }

    @Override
    public void processCampaignInputPreCore(List<InputEventAPI> events) {
        // The sidebar only shows while the host says it is live; off it, its keys and clicks must not fire,
        // so leave every event untouched. A drag left dangling by the overlay closing mid-drag ends here, so
        // a stale grab cannot hijack the next session.
        if (!host.isOverlayShowing()) {
            host.getController().cancelDrag();
            return;
        }
        // The placement the renderer drew this frame; null when there is nothing on screen, in which case
        // there is nothing to hit-test. Key presses still route, since the host needs no placement to jump
        // to a layer.
        var placement = host.resolvePlacement();
        for (var index = 0; index < events.size(); index++) {

            var event = events.get(index);
            if (event.isConsumed()) {
                continue;
            }
            if (event.isKeyDownEvent()) {
                if (arePanelTabsLive(placement)) {
                    host.handleKeyPress(event);
                }
            } else if (event.isMouseEvent() && placement != null) {
                host.getController().handlePointer(event, placement);
                parkClaimedMove(events, index, event);
            }
        }
    }

    @Override
    public void processCampaignInputPreFleetControl(List<InputEventAPI> events) {
        // The sidebar takes no part in fleet control.
    }

    @Override
    public void processCampaignInputPostCore(List<InputEventAPI> events) {
        // Nothing runs after the core screen for the sidebar; all its input is claimed pre-core.
    }

    // Hands the screen underneath a parked stand-in for a pointer move the panel has claimed, in place of
    // the claim swallowing it whole.
    //
    // The screen has to hear that the pointer moved, or a vanilla control hovered a moment before the
    // pointer crossed onto this panel goes on drawing itself lit: it drops its hover when it is told the
    // pointer is somewhere else, and a claimed event tells it nothing at all. Passing the real event
    // through instead would trade that for the mirror of it, the position being over whatever sits behind
    // the panel, which would then light up beneath it. Parked, no widget contains the position, so every
    // control lets its hover go and none takes one.
    //
    // Only moves, and only claimed ones: a press or a wheel this panel took is an act it has claimed
    // outright, and one it did not claim is already the screen's to read as it stands.
    private void parkClaimedMove(List<InputEventAPI> events, int index, InputEventAPI event) {

        if (!isEventListWritable
                || !event.isMouseMoveEvent()
                || !event.isConsumed()) {
            return;
        }
        try {
            events.set(index, new ParkedPointerEvent(event));
        } catch (UnsupportedOperationException listRefusesWrites) {
            // A list that will not take a replacement answers that once, not once per frame: the sidebar
            // still claims what it claims and the screen behind keeps its stale hover, which is the state
            // this listener is trying to improve on rather than one it can force.
            isEventListWritable = false;
        }
    }

    // Whether the panel is offering the tabs a bound key would switch between. Asked of the panel about the
    // placement it drew, so a tab with no body - which has no fold, and no handle to undo one with - keeps
    // its keys whatever fold the controller is carrying from some other tab.
    //
    // With nothing drawn at all there is no placement to ask about, and the fold is all that is left. A key
    // press needs no placement anyway: jumping to a layer does not depend on where the box landed, so a
    // frame that drew nothing still answers its hotkeys.
    private boolean arePanelTabsLive(TabPanelPlacement placement) {
        return placement == null
            ? host.getController().isFullyExpanded()
            : host.getController().isPresentingTabsOf(placement);
    }
}
