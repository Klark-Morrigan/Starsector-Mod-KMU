package kmu.politicalmap.ui;

import kmlib.math.geometry.Rectangle;

import java.util.List;

/**
 * One laid-out body control: the {@link SidebarControlSpec} it came from, the {@code bounds} of its
 * row, and - for a radio - the per-option {@code segments} within that row (empty for a single-hit
 * control). The renderer draws the widget its kind names within these rectangles and the input
 * listener hit-tests the same rectangles, so the control the player sees is the one the click
 * resolves to.
 */
public record SidebarControl(SidebarControlSpec spec, Rectangle bounds, List<Rectangle> segments) {
}
