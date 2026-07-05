package kmu.politicalmap.ui;

import kmlib.math.geometry.Rectangle;

/**
 * The laid-out rectangles of one sidebar frame: the outer {@code box} and its clickable
 * {@code tab}. Both are in UI coordinates, so the renderer draws them and the click
 * hit-test reads them without conversion.
 */
public record SidebarPlacement(Rectangle box, Rectangle tab) {
}
