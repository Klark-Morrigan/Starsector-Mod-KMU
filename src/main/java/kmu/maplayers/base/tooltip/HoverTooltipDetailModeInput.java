package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.input.InputEventAPI;

import org.lwjgl.input.Keyboard;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Claims the key that flips how much detail hover boxes state, as a campaign input listener in the
 * pre-core pass - the pass that runs before the screen's own widgets each frame, where consuming an
 * event still stops it reaching the screen underneath.
 *
 * <p>It is a listener of its own rather than part of the box's render pass because a render pass is
 * handed no events and so can consume none: reading the toggle and drawing the result are two passes,
 * which is why the mode they agree on lives in {@link HoverTooltipDetailModeState} rather than in
 * either of them.
 *
 * <p>One instance covers the whole campaign UI rather than one per screen, because the mode is one
 * global fact and the gate below is already host-blind - the box itself draws on the sector map and
 * on the intel screen's map visor alike.
 *
 * <p>The gate is {@link HoverTooltipGates}, the same seam {@link MapLayerCellTooltip} draws behind,
 * which is what keeps the key honest: it is claimed when and only when a box could be drawn, so it
 * is never swallowed while hover tooltips are switched off or no map is up, and vanilla keeps it
 * everywhere else. While the gate holds the mode flips whatever is hovered - even over nothing at
 * all - so the choice is never decided by what happened to be under the cursor at the moment of the
 * press, and the next hover that does offer a richer box shows it.
 */
public final class HoverTooltipDetailModeInput implements CampaignInputListener {

    // Ahead of the core screen, but below the sidebar's own listener, so a sidebar tab hotkey keeps
    // the first claim on any key it is bound to and this only ever sees what the sidebar left.
    private static final int INPUT_PRIORITY = 900;

    // Whether a map is on screen at all this frame - either host, either look. Supplied rather than
    // read here for the reason the dispatcher takes it supplied: the live read walks the running
    // game's widget tree on the intel side, which no test can stand up.
    private final BooleanSupplier isAnyMapShowing;

    /**
     * @param isAnyMapShowing whether a map is on screen at all - either host, either look -
     *                        host-blind because this listener is called for the whole campaign UI
     *                        and is never told which screen is up
     */
    public HoverTooltipDetailModeInput(BooleanSupplier isAnyMapShowing) {
        this.isAnyMapShowing = isAnyMapShowing;
    }

    @Override
    public int getListenerInputPriority() {
        return INPUT_PRIORITY;
    }

    @Override
    public void processCampaignInputPreCore(List<InputEventAPI> events) {
        // The one seam the drawing pass reads too, rather than a second copy of its conditions: off
        // it no box can be showing, so there is nothing for the key to switch and it must fall
        // through untouched.
        if (!HoverTooltipGates.canAnyBoxDraw(isAnyMapShowing)) {
            return;
        }
        for (var event : events) {
            // Something with the first claim already acted on this one; taking it again would flip
            // the mode on a press that was meant for a sidebar tab.
            if (event.isConsumed()) {
                continue;
            }
            if (!isDetailModeToggleKey(event)) {
                continue;
            }
            HoverTooltipDetailModeState.getInstance().toggleMode();
            // Consumed only where it acted, so nothing else claims the key while the map is open
            // and the rest of the game keeps it.
            event.consume();
        }
    }

    @Override
    public void processCampaignInputPreFleetControl(List<InputEventAPI> events) {
        // The detail mode takes no part in fleet control.
    }

    @Override
    public void processCampaignInputPostCore(List<InputEventAPI> events) {
        // Nothing runs after the core screen for the toggle; its key is claimed pre-core, which is
        // where consuming still stops the screen underneath from seeing it.
    }

    // Whether this event is the toggle being pressed. Key-down only: the matching key-up arrives as
    // its own event, and acting on both would flip the mode twice per press and leave it where it
    // started.
    static boolean isDetailModeToggleKey(InputEventAPI event) {
        return event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_F1;
    }
}
