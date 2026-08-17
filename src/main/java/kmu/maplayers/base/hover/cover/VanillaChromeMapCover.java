package kmu.maplayers.base.hover.cover;

import kmlib.starsector.ui.input.UiCursor;
import kmlib.starsector.ui.map.probes.MapSurfaceBounds;

/**
 * The cover the map's own chrome lays over it - the tab strip above it, the control bar below or
 * across it - so a cursor on the chrome is not on the map.
 *
 * <p>Stated about the map surface rather than about the chrome: the chrome is several small widgets
 * whose identities are a fact about one game build, while the surface is one widget, so the rule is
 * "on the map means inside it and clear of everything drawn with it". Screen-blind for the same
 * reason the sidebar's cover is - the map the cursor is over is whichever the library reports on
 * screen.
 *
 * <p>Fails open, and this is the cover where that matters most. An unreadable widget tree, a screen
 * showing no map at all, or a game build this rule no longer fits reads as "the cursor is on the
 * map", which merely restores the un-suppressed behaviour rather than silencing every hover on the
 * map. The absence is not silent: the surface read warns once when it cannot answer for a map tab
 * it did reach.
 */
public final class VanillaChromeMapCover implements MapCover {

    @Override
    public boolean isCoveringCursor() {

        // The dearest of the covers, costing a read into the live widget tree, so it is asked last
        // and only once the cheaper two have declined.
        var surfaceArea = MapSurfaceBounds.resolveSurfaceArea();
        
        return surfaceArea != null
            && !surfaceArea.containsPoint(UiCursor.getUiX(), UiCursor.getUiY());
    }
}
