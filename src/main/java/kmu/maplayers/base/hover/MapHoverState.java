package kmu.maplayers.base.hover;

import kmu.maplayers.base.installation.MapLayerInstallations;

/**
 * One sector's seam joining the pass that can work out what the cursor is over to the passes that
 * need the answer.
 *
 * <p>Only the map's own render pass can resolve a hover: inverting a cursor pixel back to a world
 * point needs the map widget's GL matrices, which exist for the instant that pass runs and nowhere
 * else. The highlight draws in the same pass, but the tooltip draws later, in the UI pass composited
 * over the map - a separate listener the map render knows nothing about. Neither owns the other, so
 * the frame's answer is published here for whoever needs it: a holder standing between a single
 * writer and its unrelated readers, rather than one of them owning the other.
 *
 * <p>Ordering falls out of the frame: the map's terrain pass runs before the UI passes, so a hover
 * published during the render is already current by the time anything downstream reads it.
 *
 * <p>A published hover lasts exactly as long as the passes keep publishing it, and no longer - see
 * {@link #expireHoverIfNoPassPublished()}, which is what a reader outside those passes relies on.
 *
 * <p>One per sector, held by that sector's installed map machinery, because a hover names a system
 * by bare id and nothing forbids two sectors from generating a system under the same one: a cursor
 * read on one map would light a cell on the other and name it in the other's box. The hover goes
 * with the installation when it is released, so a sector begins with nothing hovered rather than
 * with the cell the sector before it was left resting on.
 */
public final class MapHoverState {

    // Volatile so a reader on another thread sees a published hover whole rather than half-written;
    // the value itself is immutable, so publishing is the single write of this reference.
    private volatile MapHover hover = MapHover.NONE;

    // Whether a map pass has published since the last frame closed. Volatile for the reason above:
    // the pass that sets it and the frame tick that reads it are not the same caller.
    private volatile boolean hasPassPublishedSinceLastFrame;

    /**
     * @return the running sector's hover holder, for a caller vanilla drives without naming a sector
     *         and that has no other handle to resolve one from. Every pass the map itself drives now
     *         has one - a render surface resolves by the location its terrain sits in, and the box
     *         the tooltip draws by the sector it already reads - so this stands only for a seam that
     *         turns up holding neither
     */
    public static MapHoverState resolveLiveSectorHoverState() {
        return MapLayerInstallations
            .resolveInstallationForLiveSector()
            .resolveHoverState();
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
     * <p>What it answers for is the frames those passes never run on. Every other park is written
     * inside a pass, so with no map being drawn nothing parks anything: the last cell any map
     * resolved would stand for the rest of the session, and the tooltip - which draws from a
     * campaign-wide listener rather than from the map - would go on naming that system anywhere the
     * pointer went. Stated over publication rather than over what is on screen, so it needs to know
     * nothing about screens, hosts or permissions: a pass that resolved the cursor is the whole of
     * what keeps a hover alive, and a frame without one lets it go. It costs a frame's grace, a
     * hover published on one frame surviving to the next, which is a frame nobody sees.
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
