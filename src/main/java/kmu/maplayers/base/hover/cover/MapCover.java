package kmu.maplayers.base.hover.cover;

/**
 * One thing that can be drawn over the map, answering whether it stands between the cursor and the
 * cells beneath it this frame.
 *
 * <p>A hover resolves a cell from map geometry, which has no notion of anything composited on top,
 * so every cover is something the hover would otherwise read straight through - lighting a cell and
 * floating a box under a panel, a console, or the map's own chrome.
 *
 * <p>A role rather than a list of reads, because the covers have nothing in common but their
 * answer: one is a flag published by an optional mod, one is arithmetic over a box this mod laid
 * out, one is a walk of the live widget tree. Stated as a role, each carries its own cost and its
 * own failure rule, and {@link MapCoverReader} composes them without learning either.
 *
 * <p>Every cover fails open - whatever it cannot establish reads as "not covering". A read taken to
 * refine the hover must not be able to switch it off.
 */
public interface MapCover {

    /**
     * @return whether this cover is over the cursor right now, and {@code false} whenever that
     *         cannot be established
     */
    boolean isCoveringCursor();
}
