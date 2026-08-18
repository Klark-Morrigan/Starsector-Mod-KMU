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
 * everywhere else.
 *
 * <p>Behind that gate the press is claimed only where it would do something the player can see - the
 * box under the cursor has a second amount of detail to state ({@link HoveredBox}). A mode that flipped
 * over anything at all would be the more forgiving rule if the mode were per hover, but it is not: it
 * is one shared fact that holds across hovers and layer switches, so a press swallowed over a system
 * with nothing to expand would silently decide how the next system that <em>does</em> differ opens.
 * The player would meet a box in a state they never chose, having pressed the key somewhere it
 * appeared to do nothing.
 */
public final class HoverTooltipDetailModeInput implements CampaignInputListener {

    // The key the mode is flipped with. Named once here rather than at each use, since the box that
    // tells the player about it has to name the key that is actually claimed - a second literal is
    // one edit away from advertising a key this listener no longer takes.
    private static final int TOGGLE_KEY = Keyboard.KEY_F1;

    // Ahead of the core screen, but below the sidebar's own listener, so a sidebar tab hotkey keeps
    // the first claim on any key it is bound to and this only ever sees what the sidebar left.
    private static final int INPUT_PRIORITY = 900;

    /**
     * The toggle's key as it is printed on the player's keyboard, for a box stating what pressing it
     * would do. Read off the key this listener claims rather than spelled beside it, so the two cannot
     * come to name different keys.
     */
    public static final String TOGGLE_KEY_NAME = Keyboard.getKeyName(TOGGLE_KEY);

    // Whether the cursor can be located against the frame now running. Supplied rather than read
    // here for the reason the dispatcher takes it supplied: the live read walks the running game's
    // widget tree on the intel side, which no test can stand up.
    private final BooleanSupplier isCursorLocatable;

    /**
     * @param isCursorLocatable whether the cursor can be located against the frame now running -
     *                          host-blind because this listener is called for the whole campaign UI
     *                          and is never told which screen is up
     */
    public HoverTooltipDetailModeInput(BooleanSupplier isCursorLocatable) {
        this.isCursorLocatable = isCursorLocatable;
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
        if (!HoverTooltipGates.canAnyBoxDraw(isCursorLocatable)) {
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
            // Nothing under the cursor has a second amount of detail to state, so there is nothing
            // for the key to switch. Left alone rather than flipped invisibly: the mode is shared and
            // holds across hovers, so a press swallowed here would open the next system that does
            // differ in a state the player never chose - the one place a press with no visible result
            // is not harmless.
            if (!isAnyBoxOfferingExpansion()) {
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
        return event.isKeyDownEvent() && event.getEventValue() == TOGGLE_KEY;
    }

    // Whether the box under the cursor would show the player anything more under the other mode.
    // Asked of the same chain the drawing pass resolves its box through, so the key is claimed on
    // exactly the frames a box would answer it - and of the box itself, since only the layer knows
    // whether its counterpart has anything to add for the system being hovered.
    private static boolean isAnyBoxOfferingExpansion() {
        return HoveredBox
            .resolveHoveredBox()
            .filter(HoveredBox::isOfferingExpansion)
            .isPresent();
    }
}
