package kmu.maplayers.base.geometry.ui;

import java.util.prefs.Preferences;

import javax.swing.JFrame;
import javax.swing.JSplitPane;

/**
 * How big the window was and where its divider sat, kept across runs.
 *
 * <p>Its own class because it is remembered like a knob but is not one: nothing about the
 * map is drawn differently for it, so it belongs neither in the settings the viewer draws
 * with nor in the panel that edits them.
 */
public final class ViewerWindowLayout {

    private static final int WINDOW_WIDTH = 1500;

    private static final int WINDOW_HEIGHT = 1000;

    private static final int CONTROL_WIDTH = 300;

    // Keys the remembered layout is stored under. Named rather than inlined because the
    // save and the restore have to agree, and a typo in one of them fails silently.
    private static final String WINDOW_WIDTH_KEY = "windowWidth";

    private static final String WINDOW_HEIGHT_KEY = "windowHeight";

    private static final String CONTROL_WIDTH_KEY = "controlWidth";

    private ViewerWindowLayout() {
    }

    // Window size and divider, kept across runs. A geometry knob is only worth anything at a
    // particular zoom and a particular amount of screen, and having to re-establish both
    // before every session is enough friction to stop someone checking a shape they would
    // otherwise have checked.
    public static void restoreLayout(JFrame frame, JSplitPane split) {

        var saved = Preferences.userNodeForPackage(ViewerSettings.class);

        frame.setSize(
            saved.getInt(WINDOW_WIDTH_KEY, WINDOW_WIDTH),
            saved.getInt(WINDOW_HEIGHT_KEY, WINDOW_HEIGHT));

        frame.setLocationRelativeTo(null);

        // After the size, because a divider is positioned within the split's current width
        // and setting it first would place it against the default.
        split.setDividerLocation(frame.getWidth()
            - saved.getInt(CONTROL_WIDTH_KEY, CONTROL_WIDTH));
    }

    public static void saveLayout(JFrame frame, JSplitPane split) {

        var saved = Preferences.userNodeForPackage(ViewerSettings.class);

        saved.putInt(WINDOW_WIDTH_KEY, frame.getWidth());
        saved.putInt(WINDOW_HEIGHT_KEY, frame.getHeight());

        // Stored as the control column's width rather than the divider's position, so
        // reopening at a different window size keeps the knobs the size they were set to
        // instead of the map the size it happened to be.
        saved.putInt(CONTROL_WIDTH_KEY, frame.getWidth() - split.getDividerLocation());
    }
}
