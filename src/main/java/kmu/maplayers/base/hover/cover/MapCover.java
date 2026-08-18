package kmu.maplayers.base.hover.cover;

/**
 * One reason the cell under the cursor is not what the player is pointing at, answered for this
 * frame.
 *
 * <p>A hover resolves a cell from map geometry, which has no notion of what is composited on top of
 * the map or of where on screen the map even is - so without these it lights a cell and floats a box
 * under a panel, a console, or the map's own chrome, and does it over every pixel of a screen
 * showing no map at all.
 *
 * <p>Named for the ordinary case, which is something drawn over the map. A cover that instead
 * answers "the cursor is outside the only surface worth pointing at" reaches the same conclusion by
 * the other route, and the composition has no reason to tell the two apart.
 *
 * <p>A role rather than a list of reads, because the covers have nothing in common but their
 * answer: one is a flag published by an optional mod, one is arithmetic over a box this mod laid
 * out, one is a walk of the live widget tree. Stated as a role, each carries its own cost and its
 * own failure rule, and {@link MapCoverReader} composes them without learning either.
 *
 * <p><b>A cover fails open unless it says otherwise</b> - whatever it cannot establish reads as "not
 * covering", because a read taken to refine a hover must not be able to switch off a hover that was
 * legitimate before the read existed. That reasoning is what carries the rule, so it stops carrying
 * it exactly where there is no such hover to protect: a cover confining the cursor to one surface on
 * frames where nothing else is pointable has to fail closed instead, or an unreadable screen returns
 * the leak the cover was added to close. Such a cover states that on itself, and it is the exception
 * rather than a second convention.
 */
public interface MapCover {

    /**
     * @return whether this cover stands between the cursor and the map right now. What an
     *         unestablished reading answers is the cover's own to state - {@code false} for the
     *         fail-open default, and {@code true} for one documenting itself as failing closed
     */
    boolean isCoveringCursor();
}
