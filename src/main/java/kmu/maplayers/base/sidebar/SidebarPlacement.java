package kmu.maplayers.base.sidebar;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.widgets.VanillaTab;

import java.util.List;

/**
 * The laid-out rectangles of one sidebar frame: the outer {@code box} spanning the whole
 * footprint (border and all), the {@code body} panel beneath the tabs, one {@link VanillaTab} per
 * registered layer, and the {@link SidebarControl}s inside the body. All are in UI coordinates, so
 * the renderer draws them and the input listener hit-tests them without conversion.
 *
 * <p>{@code tabs} carries only geometry and display content, not the layer each selects: the tabs
 * are laid out in registry order, so a hit index maps back to its layer through that same order.
 * {@code body} is a zero-size rectangle and {@code bodyControls} is empty when the active tab
 * opens no body, so a bodyless tab reserves no dead click zone beneath the tab row.
 */
public record SidebarPlacement(Rectangle box, Rectangle body, List<VanillaTab> tabs,
        List<SidebarControl> bodyControls) {
}
