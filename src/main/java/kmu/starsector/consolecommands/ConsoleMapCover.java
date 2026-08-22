package kmu.starsector.consolecommands;

import kmlib.starsector.consolecommands.ConsoleCommandsOverlay;
import kmlib.starsector.consolecommands.ConsoleCommandsPresence;
import kmlib.starsector.consolecommands.ConsoleOverlay;

import kmu.maplayers.base.hover.cover.MapCover;

/**
 * The cover a text-entry console lays over the map: it takes the whole screen, so while one is up
 * nothing the cursor rests on is the map.
 */
public final class ConsoleMapCover implements MapCover {

    private final ConsoleOverlay consoleOverlay;

    /**
     * @param consoleOverlay the console state to read
     */
    public ConsoleMapCover(ConsoleOverlay consoleOverlay) {
        this.consoleOverlay = consoleOverlay;
    }

    /**
     * @return the cover over the one live console read a running game has
     */
    public static ConsoleMapCover createForLiveScreen() {
        return new ConsoleMapCover(ConsoleCommandsOverlay.INSTANCE);
    }

    @Override
    public boolean isCoveringCursor() {
        // No cursor test: a console covers the screen, so where the pointer is does not come into
        // it. That is also what makes this the cheapest of the covers - a settled flag, no geometry.
        return consoleOverlay.isOpen();
    }
}
