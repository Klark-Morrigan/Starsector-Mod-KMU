package kmu.desktop.ui.swing;

import kmu.desktop.ui.SavedValues;

import javax.swing.JFrame;
import javax.swing.JSplitPane;

/**
 * How big a window was and where its divider sat, kept across runs.
 *
 * <p>Its own class because it is remembered like a knob but is not one: nothing an
 * application draws is drawn differently for it, so it belongs neither with the settings a
 * window draws with nor in the panel that edits them.
 *
 * <p>Remembered under the same node the rows are, so a window and its knobs are forgotten
 * together or not at all. Which file that is belongs to {@link SavedValues}, since a package
 * that remembers things in two places is a package that can be half-cleared.
 */
public final class WindowLayout {

    private static final int WINDOW_WIDTH = 1500;

    private static final int WINDOW_HEIGHT = 1000;

    private static final int CONTROL_WIDTH = 300;

    // Keys the remembered layout is stored under. Named rather than inlined because the
    // save and the restore have to agree, and a typo in one of them fails silently.
    private static final String WINDOW_WIDTH_KEY = "windowWidth";

    private static final String WINDOW_HEIGHT_KEY = "windowHeight";

    private static final String CONTROL_WIDTH_KEY = "controlWidth";

    private WindowLayout() {
    }

    // Window size and divider, kept across runs. A geometry knob is only worth anything at a
    // particular zoom and a particular amount of screen, and having to re-establish both
    // before every session is enough friction to stop someone checking a shape they would
    // otherwise have checked.
    public static void restoreLayout(JFrame frame, JSplitPane split) {

        var saved = SavedValues.findSavedValues();

        frame.setSize(
            saved.getInt(WINDOW_WIDTH_KEY, WINDOW_WIDTH),
            saved.getInt(WINDOW_HEIGHT_KEY, WINDOW_HEIGHT));

        frame.setLocationRelativeTo(null);

        // After the size, because a divider is positioned within the split's current width
        // and setting it first would place it against the default.
        split.setDividerLocation(frame.getWidth()
            - saved.getInt(CONTROL_WIDTH_KEY, CONTROL_WIDTH));
    }

    /**
     * Writes down the window's size and the width of its control column.
     *
     * @param frame the window
     * @param split the divider between the map and the controls
     */
    public static void saveLayout(JFrame frame, JSplitPane split) {

        var saved = SavedValues.findSavedValues();

        saved.putInt(WINDOW_WIDTH_KEY, frame.getWidth());
        saved.putInt(WINDOW_HEIGHT_KEY, frame.getHeight());

        // Stored as the control column's width rather than the divider's position, so
        // reopening at a different window size keeps the knobs the size they were set to
        // instead of the map the size it happened to be.
        saved.putInt(CONTROL_WIDTH_KEY, frame.getWidth() - split.getDividerLocation());
    }
}
