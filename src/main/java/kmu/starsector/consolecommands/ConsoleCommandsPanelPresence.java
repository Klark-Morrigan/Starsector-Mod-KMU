package kmu.starsector.consolecommands;

import org.lazywizard.console.overlay.v2.panels.ConsoleOverlayPanel;

/**
 * Reads Console Commands' v2 overlay: the panel registers itself in a static holder when it is
 * constructed and clears it when it closes, so a non-null instance is exactly "a console is open".
 *
 * <p>Console Commands gates its own summon key on this same read, which makes it the most stable
 * thing the mod exposes to anyone outside it - the accessor cannot move without breaking the
 * console's own key handling.
 *
 * <p>The only class in KMU that names a Console Commands type. Keeping it alone here is what lets
 * {@link ConsoleCommandsOverlay} defer loading it until the mod is known to be installed;
 * everything else in this package compiles and runs against {@link ConsoleOverlayPresence}.
 *
 * <p>The legacy console needs no counterpart: it runs its own blocking loop while it is open, so
 * campaign listeners and render passes do not fire under it at all and there is nothing to stand
 * down.
 */
final class ConsoleCommandsPanelPresence implements ConsoleOverlayPresence {

    @Override
    public boolean isOverlayUp() {
        return ConsoleOverlayPanel.getInstance() != null;
    }
}
