package kmu.politicalmap.ui;

import kmlib.math.geometry.Rectangle;

import java.util.List;

/**
 * The laid-out rectangles of one layer-bar frame: the outer {@code box} spanning the whole
 * footprint, the {@code caption} strip beneath the tabs, and one {@link LayerTab} per
 * registered layer. All are in UI coordinates, so the renderer draws them and the input
 * listener hit-tests them without conversion.
 */
public record SidebarPlacement(Rectangle box, Rectangle caption, List<LayerTab> tabs) {
}
