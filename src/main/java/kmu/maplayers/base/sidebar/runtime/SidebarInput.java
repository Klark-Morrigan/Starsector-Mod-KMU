package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.input.InputEventAPI;

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
 *
 * <p>How the panel claims what it takes is the controller's business rather than this listener's: a press
 * is consumed, while a move parks the pointer instead, so the map underneath hears that the pointer left
 * the control it had lit. This listener only decides which events reach the controller at all.
 *
 * <p>Every key here is a layer's. Nothing about the bar's own chrome is bound to one - the dialog the bar
 * is arranged in is reached from the bar itself, so a key for it would be a second way in to something
 * already on screen.
 */
public final class SidebarInput implements CampaignInputListener {

    // Run ahead of the core screen and of other mods' listeners, so a tab click or notch press is consumed
    // before anything else claims it.
    private static final int INPUT_PRIORITY = 1000;

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
        // The placement the renderer drew this frame - read from the draw rather than laid out again, so a
        // click answers to the box on screen. Null when there is nothing on screen, in which case there is
        // nothing to hit-test. Key presses still route, since the host needs no placement to jump to a layer.
        var placement = host.getDrawnPlacement();
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
