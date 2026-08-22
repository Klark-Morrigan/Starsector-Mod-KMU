package kmu.maplayers.base.hover.cover;

import kmlib.starsector.ui.input.CursorPosition;
import kmlib.starsector.ui.input.VanillaCursorPosition;
import kmlib.starsector.ui.map.probes.MapSurfaceArea;
import kmlib.starsector.ui.map.probes.MapSurfaceBounds;

import java.util.function.Supplier;

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
 *
 * <p>Both reads arrive as ports rather than as the statics behind them, so the failing-open rule
 * above can be stated without a widget tree to make unreadable. The no-arg constructor is the
 * pairing a running game gets.
 */
public final class VanillaChromeMapCover implements MapCover {

    private final CursorPosition cursor;

    // Where the map is actually visible this frame, or null when no map tab is up or the rule no
    // longer fits the build. Asked afresh each frame rather than held: the surface moves with the
    // screen it was measured on, and the read behind this memoises that for itself.
    private final Supplier<MapSurfaceArea> resolveSurfaceArea;

    /** Reads the live widget tree and the live mouse - the pairing a running game gets. */
    public VanillaChromeMapCover() {
        this(MapSurfaceBounds::resolveSurfaceArea, new VanillaCursorPosition());
    }

    VanillaChromeMapCover(Supplier<MapSurfaceArea> resolveSurfaceArea, CursorPosition cursor) {
        this.cursor = cursor;
        this.resolveSurfaceArea = resolveSurfaceArea;
    }

    @Override
    public boolean isCoveringCursor() {

        // Costs a read into the live widget tree, so it is asked only once the three covers that
        // read a flag or a box this mod laid out have declined.
        var surfaceArea = resolveSurfaceArea.get();

        return surfaceArea != null
            && !surfaceArea.containsPoint(cursor.getUiX(), cursor.getUiY());
    }
}
