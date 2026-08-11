package kmu.maplayers.base.hover.cover;

import kmu.starsector.consolecommands.ConsoleOverlay;

/**
 * The cover a text-entry console lays over the map: it takes the whole screen, so while one is up
 * nothing the cursor rests on is the map.
 *
 * <p>Separate from the sidebar's cover even though the sidebar stands down for a console too: with
 * the panel hidden its cover answers false, so a hover asking only about the panel reads straight
 * through to the cells under the console and lights them.
 *
 * <p>Asked of the {@link ConsoleOverlay} role rather than of the console mod, so this cover depends
 * on the question and not on an optional mod, and inherits that role's fail-open answer whole.
 */
public final class ConsoleMapCover implements MapCover {

    private final ConsoleOverlay consoleOverlay;

    /**
     * @param consoleOverlay the console state to read
     */
    public ConsoleMapCover(ConsoleOverlay consoleOverlay) {
        this.consoleOverlay = consoleOverlay;
    }

    @Override
    public boolean isCoveringCursor() {
        // No cursor test: a console covers the screen, so where the pointer is does not come into
        // it. That is also what makes this the cheapest of the covers - a settled flag, no geometry.
        return consoleOverlay.isOpen();
    }
}
