package kmu.mods.console;

import kmlib.mods.console.ConsoleCommandsOverlay;

import kmu.maplayers.base.hover.cover.MapCover;

/**
 * The cover a text-entry console lays over the map: it takes the whole screen, so while one is up
 * nothing the cursor rests on is the map.
 */
public final class ConsoleMapCover implements MapCover {

    private final ConsoleCommandsOverlay consoleOverlay;

    /**
     * @param consoleOverlay the console state to read
     */
    public ConsoleMapCover(ConsoleCommandsOverlay consoleOverlay) {
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
        // it - a settled flag, no geometry. Where that ranks it is MapCoverReader's to state.
        return consoleOverlay.isOpen();
    }
}
