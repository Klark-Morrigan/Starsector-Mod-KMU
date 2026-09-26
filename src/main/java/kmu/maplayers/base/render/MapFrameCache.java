package kmu.maplayers.base.render;

import kmu.maplayers.base.hover.MapHoverTargets;
import kmu.maplayers.base.layer.ScreenMemoryScope;

/**
 * What a painting layer draws from, kept fresh by the frame sequence: brought up to date once per
 * frame, read by each pass's cursor read, and released with the sector it was built for.
 *
 * <p>One type for the three because they are one holder's lifetime. The draw lists the refresh
 * rebuilds are the shapes the cursor read tests against and the buffers the release frees, so a
 * layer handing the sequence three separate parts could hand it a refresh over one set of lists and
 * a hover test over another.
 *
 * @param <S> what a frame is painted under - the answer the layer's stand-down read found, handed
 *            back so the refresh builds under the same answer the frame decided to paint
 */
public interface MapFrameCache<S> {

    /**
     * Brings the draw lists in line with what this frame paints, rebuilding only what went stale.
     * Called once per frame, and only on a frame that did not stand down.
     *
     * @param subject     what this frame is painted under
     * @param memoryScope the screen being painted for, whose panel holds every preference the lists
     *                    are built under - taken off the same reading of the showing screen the
     *                    subject was, so the two cannot name different screens
     */
    void refreshDrawLists(S subject, ScreenMemoryScope memoryScope);

    /**
     * @return the shapes the cursor is resolved against on this frame's passes, or null while nothing
     *         has been built - which the cursor read parks on rather than resolving into
     */
    MapHoverTargets resolveHoverTargets();

    /**
     * Releases everything built for this cache's sector, when the machinery holding it goes. A layer
     * whose draw lists own GL buffers frees them here, or a sector removed mid-session leaks every
     * buffer it had built.
     */
    void disposeCachedState();
}
