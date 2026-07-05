package kmu.politicalmap.ui;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.layout.BoxPlacement;

import kmu.settings.SidebarAnchorChoice;

/**
 * Sizes the overlay sidebar box and splits off its tab strip. The box footprint and the
 * tab-versus-body split are this feature's own; anchoring the box on screen is generic, so
 * that math lives in {@link BoxPlacement} and this only feeds it the sidebar's dimensions.
 *
 * <p>The box keeps a uniform margin from whichever screen edges its anchor pulls it toward;
 * the tab is the top strip - for now the only interactive control - and the empty body sits
 * beneath it.
 */
public final class SidebarLayout {
    // Box footprint in UI units. Small on purpose: it lives in the sector map's margin, so
    // it must not cover the systems the player is reading. The body is empty for now.
    static final float BOX_WIDTH = 180f;
    static final float TAB_HEIGHT = 26f;
    static final float BODY_HEIGHT = 90f;
    static final float BOX_HEIGHT = TAB_HEIGHT + BODY_HEIGHT;
    // Gap kept between the box and each screen edge its anchor pulls it toward.
    static final float EDGE_MARGIN = 12f;

    private SidebarLayout() {
    }

    /**
     * Lays the box and its tab out for the current screen and anchor.
     *
     * @param screenWidth  the UI-coordinate screen width
     * @param screenHeight the UI-coordinate screen height
     * @param anchor       the corner or edge midpoint to pin the box to
     * @return the box rectangle and its top-strip tab rectangle, in UI coordinates
     */
    public static SidebarPlacement computePlacement(float screenWidth, float screenHeight,
            SidebarAnchorChoice anchor) {
        var box = BoxPlacement.placeBox(screenWidth, screenHeight, BOX_WIDTH, BOX_HEIGHT,
                EDGE_MARGIN, anchor.getScreenAnchor());
        // The tab is the top strip; UI y grows upward, so the strip sits above the body.
        var tab = new Rectangle(box.x(), box.y() + BODY_HEIGHT, BOX_WIDTH, TAB_HEIGHT);
        return new SidebarPlacement(box, tab);
    }
}
