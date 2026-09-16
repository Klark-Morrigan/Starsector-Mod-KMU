package kmu.desktop.ui.swing;

import kmu.desktop.ui.SavedValues;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.Scrollable;

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

    // How far one turn of the wheel moves the column. A row is taller than a line of text, so
    // a wheel notch that moved a line would take four of them to clear one knob.
    private static final int SCROLL_UNIT_INCREMENT = 16;

    private WindowLayout() {
    }

    /**
     * A column of controls that scrolls down and never sideways.
     *
     * <p><b>The column is given the sidebar's width rather than asking for its own.</b> Left to
     * ask, a stack reports the widest thing in it - and a slider alone wants 200px, so two
     * sharing a line want 408 whatever the sidebar is. The viewport then grows a bar along the
     * bottom, and every control is a drag away from being read.
     *
     * <p>Sideways is the wrong axis for a column of settings anyway. Scrolling down looks for a
     * knob that exists; scrolling across looks at the right-hand end of knobs whose left-hand
     * end is the part that names them. So the width is made to fit and the bar is refused
     * outright, which is also what makes a knob's width mean something: a track is as wide as
     * the sidebar, and a pair of them half that, however the divider is dragged.
     *
     * <p>Anchored to the top, because a column shorter than its viewport - every section folded
     * - hands the spare height to whichever rows will take it, and a stretched dropdown reads as
     * a broken control.
     *
     * @param column the controls, stacked
     * @return the scroller to put in the window
     */
    public static JScrollPane buildControlScroller(JComponent column) {

        var anchored = new TrackingColumn();

        anchored.add(column, BorderLayout.NORTH);

        var scroller = new JScrollPane(anchored);

        scroller.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroller.getVerticalScrollBar().setUnitIncrement(SCROLL_UNIT_INCREMENT);

        return scroller;
    }

    // A panel that takes its width from the viewport instead of from what is inside it.
    //
    // Only the width. The height is still the column's own, which is what leaves the vertical
    // bar to do its job - tracking that too would squash every row into one screen.
    private static final class TrackingColumn extends JPanel implements Scrollable {

        private TrackingColumn() {
            super(new BorderLayout());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return SCROLL_UNIT_INCREMENT;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return visible.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
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
