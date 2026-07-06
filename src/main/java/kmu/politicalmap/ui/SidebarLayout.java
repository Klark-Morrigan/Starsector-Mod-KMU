package kmu.politicalmap.ui;

import kmlib.math.geometry.Rectangle;

import kmu.politicalmap.layer.PoliticalMapLayer;
import kmu.settings.SidebarAnchorChoice;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays out the political-map layer bar: a row of equal-width tabs, one per registered layer,
 * with a caption strip beneath for the active layer's note. The bar reads as the map's own
 * Sector/System tab row rather than a boxed panel, so it sits in the map margin without
 * looking like a dialog.
 *
 * <p>The API exposes no handle to the sector-map panel, so the bar cannot attach to it and
 * anchors to the screen instead. To keep it from covering the map's own fixed controls, it is
 * placed within the screen region inset by the two chrome bands: the top band holding the
 * Sector/System tabs and the centered location label, and the bottom band holding the filter
 * checkboxes (the Starscape toggle among them - covering it would trap the player) and the
 * core nav bar. Anchoring inside that region keeps every anchor clear of those controls; the
 * sides and centre of the map carry no fixed chrome, so left and right need only the edge
 * margin.
 *
 * <p>Tabs are equal width so the row stays even as layers are added; the caption spans the
 * full row so a longer note than one tab still fits. UI coordinates throughout (origin
 * bottom-left), so the tab row caps the footprint and the caption hangs beneath it.
 */
public final class SidebarLayout {
    // Per-tab footprint in UI units. Wide enough for a layer name plus its "[N]" hint at the
    // label font; the row is this times the tab count.
    static final float TAB_WIDTH = 108f;
    static final float TAB_HEIGHT = 24f;
    // Caption strip below the tabs, tall enough for one line of note text.
    static final float CAPTION_HEIGHT = 22f;
    // Gap kept between the bar and each screen edge its anchor pulls it toward.
    static final float EDGE_MARGIN = 12f;

    // Top screen band the bar stays clear of: the map's tab strip (~19 UI units tall, ~17
    // below the screen top) and the location label share it, so ~46 clears them with a gap.
    static final float TOP_CHROME_BAND = 46f;
    // Bottom screen band the bar stays clear of: the full-width map filter row (the Starscape
    // toggle lives here) sits above the core nav bar, so ~100 clears both with a gap.
    static final float BOTTOM_CHROME_BAND = 100f;

    private SidebarLayout() {
    }

    /**
     * Lays the tab row and caption out for the current screen, anchor, and registered layers,
     * keeping the whole bar clear of the map's top and bottom chrome bands.
     *
     * @param screenWidth  the UI-coordinate screen width
     * @param screenHeight the UI-coordinate screen height
     * @param anchor       the corner or edge midpoint to pin the bar to
     * @param layers       the registered layers, in tab order left to right
     * @return the bar's footprint, its caption strip, and one tab per layer, in UI coords
     */
    public static SidebarPlacement computePlacement(float screenWidth, float screenHeight,
            SidebarAnchorChoice anchor, List<PoliticalMapLayer> layers) {
        var stripWidth = TAB_WIDTH * layers.size();
        var stripHeight = TAB_HEIGHT + CAPTION_HEIGHT;
        var box = placeWithinSafeRegion(screenWidth, screenHeight, stripWidth, stripHeight,
                anchor);
        // UI y grows upward: the tab row caps the footprint, the caption sits beneath it.
        var tabRowY = box.y() + CAPTION_HEIGHT;
        var tabs = new ArrayList<LayerTab>(layers.size());
        for (var index = 0; index < layers.size(); index++) {
            var bounds = new Rectangle(box.x() + index * TAB_WIDTH, tabRowY, TAB_WIDTH,
                    TAB_HEIGHT);
            tabs.add(new LayerTab(layers.get(index), bounds));
        }
        var caption = new Rectangle(box.x(), box.y(), stripWidth, CAPTION_HEIGHT);
        return new SidebarPlacement(box, caption, List.copyOf(tabs));
    }

    // Places the bar's footprint at the anchor within the screen region inset by the edge
    // margin on the sides and by the chrome bands top and bottom, so it slides across only the
    // free space that carries no map control. Clamps the free space at zero so a very small
    // screen parks the bar at the region's low corner rather than pushing it off-screen.
    private static Rectangle placeWithinSafeRegion(float screenWidth, float screenHeight,
            float stripWidth, float stripHeight, SidebarAnchorChoice anchor) {
        var usableLeft = EDGE_MARGIN;
        var usableBottom = EDGE_MARGIN + BOTTOM_CHROME_BAND;
        var usableRight = screenWidth - EDGE_MARGIN;
        var usableTop = screenHeight - EDGE_MARGIN - TOP_CHROME_BAND;
        var freeWidth = Math.max(0f, usableRight - usableLeft - stripWidth);
        var freeHeight = Math.max(0f, usableTop - usableBottom - stripHeight);
        var screenAnchor = anchor.getScreenAnchor();
        var boxX = usableLeft + screenAnchor.getHorizontalFraction() * freeWidth;
        var boxY = usableBottom + screenAnchor.getVerticalFraction() * freeHeight;
        return new Rectangle(boxX, boxY, stripWidth, stripHeight);
    }
}
