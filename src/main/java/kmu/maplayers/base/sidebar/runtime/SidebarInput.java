package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.input.InputEventAPI;

import java.util.List;

/**
 * Feeds pointer and key input to one {@link SidebarHost}'s sidebar panel as a campaign input listener: it
 * gates on the host, resolves the placement the renderer drew, and routes each event - a key press to the
 * host (which jumps to a layer, or ignores it), and a pointer event to the host's reusable KMLib {@link
 * kmlib.starsector.ui.input.TabPanelController}, which routes a header tab press, the notch toggle, the
 * thumb drag, the wheel scroll, and body control hits. One instance per host, so the sector map and the
 * intel screen each route to their own panel.
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
        for (var event : events) {
            if (event.isConsumed()) {
                continue;
            }
            if (event.isKeyDownEvent()) {
                host.handleKeyPress(event);
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
}
