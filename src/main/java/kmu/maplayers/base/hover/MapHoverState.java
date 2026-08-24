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
 *
 * <p>A published hover lasts exactly as long as the passes keep publishing it, which is what
 * {@link #expireHoverIfNoPassPublished()} is for. Every guard that parks a hover lives inside the
 * map pass, so a frame with no map pass at all - the player back in the campaign world with no map
 * on screen - has nothing to park it: the last hover a map ever resolved would stand for the rest
 * of the session, and the tooltip, which draws from a per-frame listener rather than from the map,
 * would go on naming that system anywhere on screen. Expiry is what a reader outside the passes
 * relies on, and it costs a frame's grace: a hover published on one frame survives to the next,
 * which is a frame nobody sees.
 */
public final class MapHoverState {
    // The one shared holder the render pass writes and the highlight and tooltip read.
    private static final MapHoverState INSTANCE = new MapHoverState();

    // Volatile so a reader on another thread sees a published hover whole rather than half-written;
    // the value itself is immutable, so publishing is the single write of this reference.
    private volatile MapHover hover = MapHover.NONE;

    // Whether a map pass has published since the last frame closed. Volatile for the reason above:
    // the pass that sets it and the frame tick that reads it are not the same caller.
    private volatile boolean hasPassPublishedSinceLastFrame;

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
        hasPassPublishedSinceLastFrame = true;
    }

    /**
     * Parks the hover, so nothing downstream keeps highlighting a cell the cursor has left - or one
     * from a map the player has since closed.
     */
    public void clearHover() {
        hover = MapHover.NONE;
    }

    /**
     * Parks a hover no map pass has published since the last frame closed, and opens the next
     * frame's window. Called once per frame, from outside the map passes.
     *
     * <p>What it answers for is the frames those passes never run on: every other park is written
     * inside a pass, so a hover outlives the map that resolved it the moment there is no map to
     * draw. This is stated over publication rather than over what is on screen, so it needs to know
     * nothing about screens, hosts or permissions - a pass that resolved the cursor is the whole of
     * what keeps a hover alive, and a frame without one lets it go.
     *
     * <p>A park is not a publication. The passes park through {@link #clearHover()} on their own
     * guards, and a frame carrying only those is a frame that resolved nothing - so this expires on
     * it, which costs nothing: the hover it expires is already parked.
     */
    public void expireHoverIfNoPassPublished() {

        if (!hasPassPublishedSinceLastFrame) {
            clearHover();
        }
        hasPassPublishedSinceLastFrame = false;
    }
}
