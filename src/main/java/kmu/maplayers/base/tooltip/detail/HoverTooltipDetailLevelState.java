package kmu.maplayers.base.tooltip.detail;

/**
 * The shared holder carrying how much detail hover boxes state: written by the pass that reads the
 * cycle key, read by the pass that draws the box.
 *
 * <p>The two sides are unrelated listeners - a key is claimed in the input pass, which gets no GL
 * context, and a box is drawn in a UI pass, which gets no events - so neither can own the level.
 * It sits here, in one place both resolve, so they can never disagree on how deep the box is due
 * to read.
 *
 * <p>It is a live view preference rather than save state - the same transience as the hover itself -
 * so it is a plain singleton never registered with the save, and nothing serialises it. Being a
 * singleton it outlives the save that set it, which is what
 * {@link #discardLevelFromPreviousSave()} answers for on load.
 *
 * <p>It sits beside the tooltips rather than beside the hover state because it says nothing about
 * what is under the cursor: it states how deep a tooltip's box reads, which is a tooltip fact.
 */
public final class HoverTooltipDetailLevelState {

    // The one shared holder the input pass advances and the tooltip dispatcher reads.
    private static final HoverTooltipDetailLevelState INSTANCE = new HoverTooltipDetailLevelState();

    // Volatile so a reader on another thread sees the level the last press set rather than a stale
    // one; the advance itself is safe unsynchronised because only the input pass ever writes it.
    private volatile HoverTooltipDetailLevel level = HoverTooltipDetailLevel.FACTIONS;

    // Reached through getInstance(); the holder stands on its own instance, so the constructor is
    // package-visible rather than sealed to the singleton.
    HoverTooltipDetailLevelState() {
    }

    /**
     * @return the one shared holder the cycle key writes and the tooltip dispatcher reads, since
     *         neither pass owns the other
     */
    public static HoverTooltipDetailLevelState getInstance() {
        return INSTANCE;
    }

    /**
     * @return how deep the box under the cursor is due to read, as of the last press
     */
    public HoverTooltipDetailLevel getLevel() {
        return level;
    }

    /**
     * Drops back to the factions level, so the level the last save was left at does not decide what
     * the one being loaded opens on.
     *
     * <p>The holder outlives any one save - it is a process-lifetime singleton, and the game keeps
     * the process alive from one save straight into the next - so without this the level would carry
     * over silently, the one piece of this feature a player could not account for.
     */
    public void discardLevelFromPreviousSave() {
        level = HoverTooltipDetailLevel.FACTIONS;
    }

    /**
     * Moves to {@code level}. It stands until moved again, so the choice carries across hovers,
     * layer switches, and map open and close.
     *
     * <p>Told where to go rather than stepping the cycle itself, because where a press lands turns
     * on how deep the box under the cursor actually goes - a fact this holder has no way to read.
     * Stepping here, it would advance past the tiers a shallow box holds and leave the player
     * pressing through levels that redraw the same thing.
     *
     * @param level how deep the box under the cursor is to read from now on
     */
    public void moveToLevel(HoverTooltipDetailLevel level) {
        this.level = level;
    }
}
