package kmu.maplayers.base.sidebar;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.controls.Control;
import kmlib.starsector.ui.widgets.TabPanelPlacement;
import kmlib.starsector.ui.widgets.VanillaTab;

import java.util.List;

/**
 * One laid-out sidebar frame: the reusable {@link TabPanelPlacement} carrying the panel's outer
 * box, tab row, and framed body rectangle, paired with the {@link Control}s KMU laid inside
 * that body. All rectangles are in UI coordinates, so the renderer draws them and the input listener
 * hit-tests them without conversion.
 *
 * <p>The panel geometry is the KMLib chrome and the controls are KMU's, kept side by side so a
 * caller reaches the frame through {@link #panel()} (for {@link kmlib.starsector.ui.widgets.TabPanel}
 * render and hit-test) and the controls through {@link #bodyControls()}. The {@link #box()},
 * {@link #tabs()}, and {@link #body()} shortcuts read straight off the panel, so a bodyless tab (an
 * empty {@code bodyControls} and a zero-size body) reserves no dead click zone beneath the tab row.
 *
 * <p>When the body is capped (its natural height would run past the bottom margin), its one scrolling
 * list gives up the difference and scrolls within {@link #flexViewport()}: the renderer clips the list
 * draw to that viewport and the input listener confines its clicks to it, while {@link #scrollOffset()}
 * (already baked into the list control's laid-out bounds) and {@link #scrollOverflow()} drive the
 * scrollbar. An uncapped body carries a zero viewport and zero overflow, so the whole strip pins as
 * before and no scrollbar shows.
 *
 * @param panel          the frame, tab row, and body rectangle from the reusable panel
 * @param bodyControls   the controls KMU laid inside the body, top to bottom (empty for no body)
 * @param flexViewport   the clip rectangle for the scrolling list, zero-size when nothing scrolls
 * @param scrollOffset   the applied scroll offset in pixels, baked into the list control's bounds
 * @param scrollOverflow how far the list overruns its viewport, zero when it fits
 */
public record SidebarPlacement(TabPanelPlacement panel, List<Control> bodyControls,
        Rectangle flexViewport, float scrollOffset, float scrollOverflow) {
    /**
     * @return the panel's full footprint, border included
     */
    public Rectangle box() {
        return panel.box();
    }

    /**
     * @return the laid-out tabs, in registry order, so a hit index maps back to its layer
     */
    public List<VanillaTab> tabs() {
        return panel.tabs();
    }

    /**
     * @return the framed body rectangle, zero-size when the active tab opens no body
     */
    public Rectangle body() {
        return panel.body();
    }

    /**
     * @return whether the scrolling list overruns its viewport, so the renderer draws a scrollbar and
     *         the input listener scrolls on a wheel event
     */
    public boolean isScrollbarNeeded() {
        return scrollOverflow > 0f;
    }
}
