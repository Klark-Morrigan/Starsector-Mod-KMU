package kmu.maplayers.base.sidebar;

/**
 * Holds the on-map sidebar's scroll position: how far, in pixels, the capped body's one scrolling list
 * is scrolled from its top. It is ephemeral UI state - a plain JVM static, not saved to the game - so a
 * fresh game starts at the top and the position is not worth serialising; the player re-finds their spot
 * in a moment. One position serves the whole sidebar, since only its one flex list scrolls.
 *
 * <p>The stored value is a raw request, not the drawn offset: {@link #scrollBy} nudges it and the layout
 * ({@link kmlib.starsector.ui.layout.CappedStripLayout}) is the clamp authority that confines it to the
 * list's real overflow when it lays the list out. {@link #clampTo}, called each frame with that overflow
 * once the placement is resolved, settles the stored value back into range, so a wheel past the bottom or
 * a list that shrank cannot leave the stored request drifting far outside what can be scrolled.
 */
public final class SidebarScrollState {
    // The requested scroll offset in pixels from the list's top; settled into the list's real range each
    // frame by clampTo, so it never drifts unboundedly past what the list can scroll.
    private static float offset;

    private SidebarScrollState() {
    }

    /**
     * @return the current scroll offset request in pixels, which the layout clamps to the list's overflow
     *         when it places the list
     */
    public static float getOffset() {
        return offset;
    }

    /**
     * Nudges the scroll offset by {@code delta} pixels (positive scrolls the list toward its bottom). Not
     * clamped here - {@link #clampTo} settles it into range once the frame's layout knows the overflow.
     *
     * @param delta the pixels to add to the offset
     */
    public static void scrollBy(float delta) {
        offset += delta;
    }

    /**
     * Confines the stored offset to {@code [0, overflow]}, called each frame once the layout has resolved
     * how far the list actually overruns its viewport, so the stored request tracks what can be scrolled
     * rather than drifting past it.
     *
     * @param overflow how far the list overruns its viewport, 0 when it fits
     */
    public static void clampTo(float overflow) {
        offset = Math.max(0f, Math.min(offset, overflow));
    }

    /**
     * Resets the scroll offset to the list's top. Used to isolate the shared static between tests; the
     * live sidebar leaves the position where the player left it across map opens.
     */
    public static void reset() {
        offset = 0f;
    }
}
