package kmu.starsector.consolecommands;

import kmu.maplayers.base.hover.cover.MapCover;

/**
 * The cover a text-entry console lays over the map: it takes the whole screen, so while one is up
 * nothing the cursor rests on is the map.
 *
 * <p>Separate from the sidebar's cover even though the sidebar stands down for a console too: with
 * the panel hidden its cover answers false, so a hover asking only about the panel reads straight
 * through to the cells under the console and lights them.
 *
 * <p>The reading is asked of the {@link ConsoleOverlay} role rather than of the console mod, and
 * inherits that role's fail-open answer whole. The home and the factory are the mod's all the
 * same: the only live answer comes from Console Commands, and the cover joins the map's covers
 * only where {@link ConsoleCommandsPresence} finds that mod installed - so this class sits with
 * the integration it exists for, beside the adapter that answers it.
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
