package kmu.starsector.consolecommands;

/**
 * Answers "is a text-entry console overlay up right now?" for anything that has to stand down
 * while one is. A console takes the keyboard for the length of a command, so an overlay drawn
 * after the whole core UI covers it, and an input listener running ahead of the core swallows the
 * keystrokes the console exists to receive.
 *
 * <p>A role rather than a call into Console Commands, because that mod is optional: the caller
 * depends on the question, and whether anything can answer it is settled behind this interface.
 * The answer also decides whether a caller draws or routes at all, which is behaviour worth
 * settling without a game running.
 */
public interface ConsoleOverlay {

    /**
     * Whether a console is taking text entry this frame.
     *
     * <p>Fails open: every way the answer can go missing - no console mod installed, its state
     * unreadable, the read throwing - reports {@code false}. A caller then behaves exactly as it
     * did before this question existed, which is a known annoyance, rather than standing down
     * everywhere on a read that broke.
     *
     * @return whether a console overlay is open, and {@code false} whenever that cannot be
     *         established
     */
    boolean isOpen();
}
