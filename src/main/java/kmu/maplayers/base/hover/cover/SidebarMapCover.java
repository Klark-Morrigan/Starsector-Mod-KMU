package kmu.maplayers.base.hover.cover;

import kmlib.starsector.ui.input.CursorPosition;
import kmlib.starsector.ui.input.VanillaCursorPosition;

import kmu.maplayers.base.sidebar.runtime.SidebarHosts;

/**
 * The cover a map-layer sidebar lays over the map: the panel is drawn on top of it, so a cursor on
 * the panel is not hovering the cells beneath.
 *
 * <p>Screen-blind, because the pass that asks has no way to be anything else: it is reached through
 * a hook that names no screen, and the layers draw on the sector map and on the intel screen's map
 * visor through one terrain pass, so the bar the cursor is over is not always the on-map one. The
 * roster answers for every host at once, which is also what keeps a screen added later from needing
 * a line here.
 *
 * <p>Both reads arrive as ports rather than as the statics behind them, so the one rule this class
 * owns - which side of the panel the pointer is on - can be stated without a display or a live
 * roster. The no-arg constructor is the pairing a running game gets.
 */
public final class SidebarMapCover implements MapCover {

    private final CursorPosition cursor;

    private final SidebarPresence sidebarPresence;

    /** Reads the live roster and the live mouse - the pairing outside a test. */
    public SidebarMapCover() {
        this(SidebarHosts::isPointOverAnySidebar, new VanillaCursorPosition());
    }

    SidebarMapCover(SidebarPresence sidebarPresence, CursorPosition cursor) {
        this.cursor = cursor;
        this.sidebarPresence = sidebarPresence;
    }

    @Override
    public boolean isCoveringCursor() {
        // Arithmetic over a box this mod already laid out, which is what puts it between the
        // console's settled flag and the chrome's walk of the live widget tree.
        return sidebarPresence.isAnySidebarOverPoint(cursor.getUiX(), cursor.getUiY());
    }
}
