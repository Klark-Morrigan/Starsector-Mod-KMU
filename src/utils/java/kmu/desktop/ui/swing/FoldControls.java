package kmu.desktop.ui.swing;

import java.awt.Component;

import javax.swing.JButton;
import javax.swing.JComponent;

/**
 * The control that folds something away, and the one statement of what one looks like.
 *
 * <p>Two things in the panel fold: a section, and a branch of a block of switches. They are
 * different structures - one hides a run of controls under a heading, the other hides the rows
 * indented beneath a row - but a reader does not care about that distinction, and a fold that
 * looked or read differently in the two places would suggest one.
 *
 * <p>Which way round the marks read is the part worth holding in one place. "+" for folded and
 * "-" for unfolded is a convention rather than a deduction, and two copies of it are free to
 * end up opposite - at which point half the panel says "press me to open" and the other half
 * says "this is open".
 *
 * <p>What one LOOKS like is not decided here. A fold and a reset are the same small square
 * button to the eye, and {@link ControlRows#buildMarkButton} is where that is stated once; what
 * belongs to a fold is the marks it carries, that it is chrome rather than a setting, and that
 * it stays live when the settings around it do not.
 */
public final class FoldControls {

    private static final String FOLDED_LABEL = "+";
    private static final String UNFOLDED_LABEL = "-";

    private static final String FOLD_TOOLTIP = "Fold this away";

    // What marks a control as a fold rather than a setting, for the one walk that has to tell
    // them apart: greying a section's body must not take the folds inside it with the switches.
    //
    // A mark rather than a test on JButton, because the panel's other buttons are settings -
    // every reset is one - and a rule written by type would either spare those too or spare
    // neither. Qualified, since a client property is a slot on the component shared with the
    // look-and-feel's own.
    private static final String FOLD_MARKER = "kmu.desktop.ui.swing.isFold";

    private FoldControls() {
    }

    /**
     * @return how wide a fold control is, so that a row without one can leave the same gap and
     *         keep its label in line with the rows that have one
     */
    public static int measureFoldWidth() {
        return ControlRows.measureMarkWidth();
    }

    /**
     * Builds one, already showing the state it is in.
     *
     * <p>Not focusable, because it is chrome: tabbing through a panel should walk the settings,
     * and a fold changes nothing about what the map is drawn with.
     *
     * @param isUnfolded whether what it folds is currently showing
     * @return the control
     */
    public static JButton buildFoldButton(boolean isUnfolded) {

        var fold = ControlRows.buildMarkButton(
            isUnfolded ? UNFOLDED_LABEL : FOLDED_LABEL, FOLD_TOOLTIP);

        fold.putClientProperty(FOLD_MARKER, Boolean.TRUE);
        fold.setFocusable(false);

        return fold;
    }

    /**
     * Whether a control is one of these.
     *
     * <p>Asked by whatever greys a run of controls. A fold decides nothing about the map - it
     * says whether the rows beneath it are on screen - so it stays live however the settings
     * around it stand: a reader has to be able to open a branch and read what is in it without
     * switching an overlay on to do it, which is the opposite of what the panel is for.
     *
     * @param control the control, which may be anything and may be null
     * @return whether it folds something away
     */
    public static boolean isFoldControl(Component control) {

        return control instanceof JComponent component
            && Boolean.TRUE.equals(component.getClientProperty(FOLD_MARKER));
    }

    /**
     * Redraws one to match the state it now stands for.
     *
     * <p>Apart from the click that caused it, because a fold can also be restored on build or
     * changed by a fold above it - and a control relabelled only where it was pressed goes on
     * showing the old mark in exactly those cases.
     *
     * @param fold       the control
     * @param isUnfolded whether what it folds is now showing
     */
    public static void showFoldState(JButton fold, boolean isUnfolded) {
        fold.setText(isUnfolded ? UNFOLDED_LABEL : FOLDED_LABEL);
    }
}
