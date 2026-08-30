package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.campaign.listeners.CampaignInputListener;
import com.fs.starfarer.api.input.InputEventAPI;

import kmu.maplayers.base.hover.MapHoverPermission;

import org.lwjgl.input.Keyboard;

import java.util.List;

/**
 * Claims the key that advances how much detail hover boxes state, as a campaign input listener in
 * the pre-core pass - the pass that runs before the screen's own widgets each frame, where consuming
 * an event still stops it reaching the screen underneath.
 *
 * <p>It is a listener of its own rather than part of the box's render pass because a render pass is
 * handed no events and so can consume none: reading the toggle and drawing the result are two passes,
 * which is why the level they agree on lives in {@link HoverTooltipDetailModeState} rather than in
 * either of them.
 *
 * <p>One instance covers the whole campaign UI rather than one per screen, because the level is one
 * global fact and the gate below is already host-blind - the box itself draws on the sector map and
 * on the intel screen's map visor alike.
 *
 * <p>The gate is {@link HoverTooltipGates}, the same seam {@link MapLayerCellTooltip} draws behind,
 * which is what keeps the key honest: it is claimed when and only when a box could be drawn, so it
 * is never swallowed while hover tooltips are switched off or no map is up, and vanilla keeps it
 * everywhere else.
 *
 * <p>Behind that gate the press is claimed only where it would do something the player can see - the
 * box under the cursor has a second amount of detail to state ({@link HoveredBox}). A level that
 * advanced over anything at all would be the more forgiving rule if the level were per hover, but it
 * is not: it is one shared fact that holds across hovers and layer switches, so a press swallowed
 * over a system with nothing to expand would silently decide how the next system that <em>does</em>
 * differ opens. The player would meet a box at a level they never chose, having pressed the key
 * somewhere it appeared to do nothing.
 */
public final class HoverTooltipDetailModeInput implements CampaignInputListener {

    // The key the level is advanced with. Named once here rather than at each use, since the box that
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

    // Which frames the cursor can be located against. Held as the shared type rather than as a
    // boolean, for the reason the dispatcher holds it that way: the key must be claimed on exactly
    // the frames the box can draw on, and two compositions of the same screen reads would be two
    // chances to disagree about which those are. Handed in because the live reads walk the running
    // game's widget tree on the intel side, which nothing outside a running game answers.
    private final MapHoverPermission hoverPermission;

    /**
     * @param hoverPermission which frames the cursor can be located against - host-blind because
     *                        this listener is called for the whole campaign UI and is never told
     *                        which screen is up
     */
    public HoverTooltipDetailModeInput(MapHoverPermission hoverPermission) {
        this.hoverPermission = hoverPermission;
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
        if (!HoverTooltipGates.canAnyBoxDraw(hoverPermission)) {
            return;
        }
        for (var event : events) {
            // Something with the first claim already acted on this one; taking it again would advance
            // the level on a press that was meant for a sidebar tab.
            if (event.isConsumed()) {
                continue;
            }
            if (!isDetailModeToggleKey(event)) {
                continue;
            }
            // Nothing under the cursor has a second amount of detail to state, so there is nothing
            // for the key to switch. Left alone rather than advanced invisibly: the level is shared
            // and holds across hovers, so a press swallowed here would open the next system that does
            // differ at a level the player never chose - the one place a press with no visible result
            // is not harmless.
            if (!isAnyBoxOfferingExpansion()) {
                continue;
            }
            HoverTooltipDetailModeState.getInstance().advanceLevel();
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
    // its own event, and acting on both would advance the level twice per press - the player would
    // skip a depth they never saw.
    static boolean isDetailModeToggleKey(InputEventAPI event) {
        return event.isKeyDownEvent() && event.getEventValue() == TOGGLE_KEY;
    }

    // Whether the box under the cursor would show the player anything more at another level.
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
