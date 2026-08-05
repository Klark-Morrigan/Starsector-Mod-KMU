package kmu.maplayers.base.tooltip;

/**
 * The shared holder carrying how much detail hover boxes state: written by the pass that reads the
 * toggle key, read by the pass that draws the box.
 *
 * <p>The two sides are unrelated listeners - a key is claimed in the input pass, which gets no GL
 * context, and a box is drawn in a UI pass, which gets no events - so neither can own the mode.
 * It sits here, in one place both resolve, so they can never disagree on which box is due.
 *
 * <p>It is a live view preference rather than save state - the same transience as the hover itself -
 * so it is a plain singleton reset to {@link HoverTooltipDetailMode#NORMAL} each session rather than
 * an object registered with the save.
 *
 * <p>It sits beside the tooltips rather than beside the hover state because it says nothing about
 * what is under the cursor: it selects which box a tooltip draws, which is a tooltip fact.
 */
public final class HoverTooltipDetailModeState {
    
    // The one shared holder the input pass toggles and the tooltip dispatcher reads.
    private static final HoverTooltipDetailModeState INSTANCE = new HoverTooltipDetailModeState();

    // Volatile so a reader on another thread sees the mode the last toggle set rather than a stale
    // one; the flip itself is safe unsynchronised because only the input pass ever writes it.
    private volatile HoverTooltipDetailMode mode = HoverTooltipDetailMode.NORMAL;

    // Reached through getInstance(); the holder stands on its own instance, so the constructor is
    // package-visible rather than sealed to the singleton.
    HoverTooltipDetailModeState() {
    }

    /**
     * @return the one shared holder the toggle writes and the tooltip dispatcher reads, since
     *         neither pass owns the other
     */
    public static HoverTooltipDetailModeState getInstance() {
        return INSTANCE;
    }

    /**
     * @return how much detail the box under the cursor is due to state, as of the last toggle
     */
    public HoverTooltipDetailMode getMode() {
        return mode;
    }

    /**
     * Flips the mode between the normal box and its richer counterpart. The mode stands until
     * flipped again, so the choice carries across hovers, layer switches, and map open and close.
     */
    public void toggleMode() {
        mode = mode == HoverTooltipDetailMode.NORMAL
                ? HoverTooltipDetailMode.EXPANDED
                : HoverTooltipDetailMode.NORMAL;
    }
}
