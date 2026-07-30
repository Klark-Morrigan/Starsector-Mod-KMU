package kmu.maplayers.base.hover;

/**
 * The shared seam joining the one pass that can work out what the cursor is over to the passes that
 * need the answer.
 *
 * <p>Only the map's own render pass can resolve a hover: inverting a cursor pixel back to a world
 * point needs the map widget's GL matrices, which exist for the instant that pass runs and nowhere
 * else. The highlight draws in the same pass, but the tooltip draws later, in the UI pass composited
 * over the map - a separate listener the map render knows nothing about. Neither owns the other, so
 * the frame's answer is published here for whoever needs it: a shared holder standing between a
 * single writer and its unrelated readers, rather than one of them owning the other.
 *
 * <p>Ordering falls out of the frame: the map's terrain pass runs before the UI passes, so a hover
 * published during the render is already current by the time anything downstream reads it.
 */
public final class MapHoverState {
    // The one shared holder the render pass writes and the highlight and tooltip read.
    private static final MapHoverState INSTANCE = new MapHoverState();

    // Volatile so a reader on another thread sees a published hover whole rather than half-written;
    // the value itself is immutable, so publishing is the single write of this reference.
    private volatile MapHover hover = MapHover.NONE;

    // Reached through getInstance(); the holder stands on its own instance, so the constructor is
    // package-visible rather than sealed to the singleton.
    MapHoverState() {
    }

    /**
     * @return the one shared holder the map render publishes to and the highlight and tooltip read,
     *         since neither owns the other
     */
    public static MapHoverState getInstance() {
        return INSTANCE;
    }

    /**
     * @return what the cursor is over as of the last map render, or {@link MapHover#NONE}
     *         when it is over no cell or the map is not being drawn
     */
    public MapHover getHover() {
        return hover;
    }

    /**
     * Publishes what the cursor is over this frame.
     *
     * @param hover the resolved hover, or {@link MapHover#NONE} to park it
     */
    public void publishHover(MapHover hover) {
        this.hover = hover;
    }

    /**
     * Parks the hover, so nothing downstream keeps highlighting a cell the cursor has left - or one
     * from a map the player has since closed.
     */
    public void clearHover() {
        hover = MapHover.NONE;
    }
}
