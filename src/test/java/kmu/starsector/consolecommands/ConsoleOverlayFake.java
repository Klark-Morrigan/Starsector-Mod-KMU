package kmu.starsector.consolecommands;

/**
 * A console overlay that is open or closed because a test said so, standing in for the live read
 * of Console Commands wherever the question is only "and what does the caller do then?".
 *
 * <p>Public because the callers that stand down for a console live in other packages: the answer
 * decides whether they draw and route at all, and that is the behaviour their own tests pin.
 */
public final class ConsoleOverlayFake implements ConsoleOverlay {

    private boolean isOpen;

    @Override
    public boolean isOpen() {
        return isOpen;
    }

    /**
     * Puts a console up, as if the player had just summoned one.
     */
    public void openConsole() {
        isOpen = true;
    }

    /**
     * Takes the console down again, as if the player had dismissed it.
     */
    public void closeConsole() {
        isOpen = false;
    }
}
