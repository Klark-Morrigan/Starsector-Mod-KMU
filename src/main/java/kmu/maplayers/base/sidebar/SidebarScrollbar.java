package kmu.maplayers.base.sidebar;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.widgets.PanelPlacement;
import kmlib.starsector.ui.widgets.Scrollbar;

/**
 * Resolves the sidebar's scrollbar geometry from a laid-out {@link PanelPlacement}: the track in the
 * body's right gutter, the thumb sized within it, the grab column a drag reads, and the scroll offset a
 * pointer maps to. It adapts the placement (body, viewport, offset, overflow) onto the pure KMLib {@link
 * Scrollbar} math, owning only the KMU pixel widths and the "content height = viewport + overflow"
 * mapping. The renderer and the input listener both read it, so what is drawn and what a drag hit-tests
 * are one geometry.
 *
 * <p>Pure UI-coordinate geometry (it reads the placement and returns rectangles/offsets, drawing and
 * hit-testing nothing), so the drag interaction stays in the input listener and only the placement-to-
 * scrollbar mapping lives here.
 */
public final class SidebarScrollbar {
    // The track's width and the gap holding it off the border, in the body's right gutter. Kept narrow
    // so the track never crowds the list's trailing values, which the list's viewport clips clear of it.
    private static final float TRACK_WIDTH = 3f;
    private static final float RIGHT_MARGIN = 3f;

    private SidebarScrollbar() {
    }

    /**
     * @param placement the laid-out sidebar
     * @return the scrollbar track in the body's right gutter, spanning the scroll viewport
     */
    public static Rectangle computeTrack(PanelPlacement placement) {
        return Scrollbar.computeRightGutterTrack(placement.body(), placement.flexViewport(),
                TRACK_WIDTH, RIGHT_MARGIN);
    }

    /**
     * @param placement the laid-out sidebar
     * @param track     the track from {@link #computeTrack}
     * @return the thumb sized and positioned within the track for how far the list is scrolled
     */
    public static Rectangle computeThumb(PanelPlacement placement, Rectangle track) {
        return Scrollbar.computeThumb(track, resolveContentHeight(placement),
                placement.flexViewport().height(), placement.scrollOffset());
    }

    /**
     * The gutter column a scrollbar drag grabs by: the strip right of the list, at the track's height.
     * It is wider than the thin track so the player need not hit the track exactly, and it sits right of
     * the list column so a press here grabs the scrollbar rather than selecting a list row (which lies to
     * its left).
     *
     * @param placement the laid-out sidebar
     * @param track     the track from {@link #computeTrack}
     * @return the grab column, in UI coordinates
     */
    public static Rectangle computeGrabColumn(PanelPlacement placement, Rectangle track) {
        var viewport = placement.flexViewport();
        var listRight = viewport.x() + viewport.width();
        var bodyRight = placement.body().x() + placement.body().width();
        return new Rectangle(listRight, track.y(), bodyRight - listRight, track.height());
    }

    /**
     * The scroll offset the pointer at {@code pointerY} maps to along the track, for a drag or a track
     * press. Delegates the track-to-offset math to {@link Scrollbar}, supplying the placement's content
     * and viewport heights.
     *
     * @param placement the laid-out sidebar
     * @param track     the track from {@link #computeTrack}
     * @param pointerY  the pointer's y, in UI coordinates
     * @return the scroll offset, 0..overflow
     */
    public static float resolveOffsetForPointer(PanelPlacement placement, Rectangle track,
            float pointerY) {
        return Scrollbar.resolveOffsetForPointer(track, resolveContentHeight(placement),
                placement.flexViewport().height(), pointerY);
    }

    // The scrolled content's full height: the visible viewport plus how far the list overruns it - the
    // pair the thumb size and the pointer mapping both derive from.
    private static float resolveContentHeight(PanelPlacement placement) {
        return placement.flexViewport().height() + placement.scrollOverflow();
    }
}
