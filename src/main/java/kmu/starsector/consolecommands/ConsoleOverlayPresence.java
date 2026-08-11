package kmu.starsector.consolecommands;

/**
 * The bare read of a console's own state: is its overlay live right now? No gate, no failure
 * handling, no caching - just the one question, asked of whatever mod owns the console.
 *
 * <p>Separate from {@link ConsoleCommandsOverlay} so that the class naming a
 * {@code org.lazywizard.console} type is a class of its own, which the classloader resolves only
 * when the gate above it has already found the mod installed. A direct reference held anywhere on
 * that gate's own class would be resolved with it, and an install without Console Commands would
 * then take a missing-class error on a per-frame path.
 *
 * <p>Being a role rather than a static call is also what keeps a second console source - a rival
 * mod, or a replacement for the overlay this one reads - an implementation to add here rather
 * than a branch to thread through the gate.
 */
interface ConsoleOverlayPresence {

    /**
     * @return whether this console's overlay is live right now
     */
    boolean isOverlayUp();
}
