package kmu.maplayers.base.geometry.viewer;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

/**
 * The viewer's smaller knobs, and the furniture every knob in the panel shares.
 *
 * <p>Every control in the panel is the same three things - read the saved value on build, write
 * it on change, restore the default on demand - and writing that out per knob is how a panel of
 * twenty ends up with two that quietly do not persist. So where the value is KEPT, what a reset
 * button looks like, and how a labelled row is laid out live here, and the controls that are
 * substantial enough to have their own arithmetic or their own look live beside this in
 * {@link ViewerSliders} and {@link ViewerSwatches}.
 *
 * <p>Persistence is deliberate rather than a convenience. A geometric knob is only meaningful
 * at a particular zoom on a particular sector, and re-establishing a dozen of them before
 * every session is enough friction to stop someone checking a shape they would otherwise have
 * checked. The reset button is the other half: a knob that cannot be put back is a knob
 * people stop turning.
 */
final class ViewerControls {

    private static final int ROW_PADDING = 4;

    // Wider than a row's own padding, so the rule reads as a break between subjects
    // rather than as one more gap in an evenly spaced column.
    private static final int DIVIDER_PADDING = 8;

    private static final int LABELLED_ROWS = 2;

    private static final int SINGLE_COLUMN = 1;

    private static final int SINGLE_ROW = 1;

    private static final int RESET_BUTTON_WIDTH = 22;

    private static final int RESET_BUTTON_HEIGHT = 18;

    // ASCII only: the viewer runs wherever the JDK's default font does, and a glyph that
    // renders as a box on one machine makes the control unreadable rather than merely plain.
    private static final String RESET_LABEL = "R";
    private static final String RESET_TOOLTIP = "Reset to default";

    private ViewerControls() {
    }

    /**
     * A rule across the panel, breaking one run of knobs off from the next.
     *
     * <p>The panel is one long column and every row in it looks like every other, so a reader
     * looking for a knob has only its label to go on. A rule says where one subject ends, which
     * turns the search into "the void half, near the top" rather than a scan of forty rows.
     *
     * @return the rule, as a row
     */
    static JPanel buildDivider() {

        var row = new JPanel(new BorderLayout());

        row.add(new JSeparator(SwingConstants.HORIZONTAL), BorderLayout.CENTER);
        row.setBorder(BorderFactory.createEmptyBorder(DIVIDER_PADDING, 0, DIVIDER_PADDING, 0));

        // A separator will take whatever height it is offered, and the panel's layout offers
        // it everything left over - so without a cap the rule becomes a gap the height of the
        // window and pushes the knobs under it off the bottom.
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));

        return row;
    }

    /**
     * A checkbox that remembers itself, with a reset button.
     *
     * @param key      what to remember it under
     * @param title    what the toggle is called
     * @param fallback the state to start in and to reset to
     * @param apply    records the new state
     * @param onChange what to run once it changes
     * @return the row
     */
    static JPanel buildToggle(
            String key,
            String title,
            boolean fallback,
            Consumer<Boolean> apply,
            Runnable onChange) {

        var saved = findSavedValues().getBoolean(key, fallback);

        var toggle = new JCheckBox(title, saved);

        toggle.addActionListener(event -> {

            apply.accept(toggle.isSelected());

            findSavedValues().putBoolean(key, toggle.isSelected());

            onChange.run();
        });

        apply.accept(saved);

        var row = new JPanel(new BorderLayout());

        row.add(toggle, BorderLayout.CENTER);
        row.add(buildResetButton(
            () -> {

                toggle.setSelected(fallback);
                apply.accept(fallback);

                findSavedValues().putBoolean(key, fallback);

                onChange.run();

            }),
            BorderLayout.EAST);

        row.setBorder(BorderFactory.createEmptyBorder(ROW_PADDING, 0, ROW_PADDING, 0));

        return row;
    }

    /**
     * One checkbox in a row of them.
     *
     * @param key      what to remember it under
     * @param title    what the toggle is called
     * @param fallback the state to start in and to reset to
     * @param apply    records the new state
     */
    record Toggle(
        String key,
        String title,
        boolean fallback,
        Consumer<Boolean> apply) {
    }

    /**
     * Several checkboxes side by side on one line, with a single reset for the row.
     *
     * <p>A toggle carries one bit and needs one line's worth of height to say so. Giving each
     * a row of its own, as tall as a colour picker or a slider, pushes the knobs that do need
     * the space off the bottom of the panel - so related toggles share a line.
     *
     * @param onChange what to run once any of them changes
     * @param toggles  the checkboxes, left to right
     * @return the row
     */
    static JPanel buildToggleRow(Runnable onChange, Toggle... toggles) {

        var boxes = new JPanel(new GridLayout(SINGLE_ROW, toggles.length));
        var saved = findSavedValues();
        var checks = new ArrayList<JCheckBox>(toggles.length);

        for (var toggle : toggles) {

            var check = new JCheckBox(
                toggle.title(),
                saved.getBoolean(toggle.key(),
                toggle.fallback()));

            toggle.apply().accept(check.isSelected());

            check.addActionListener(event -> {
                toggle.apply().accept(check.isSelected());
                saved.putBoolean(toggle.key(), check.isSelected());
                onChange.run();
            });

            checks.add(check);
            boxes.add(check);
        }

        var row = new JPanel(new BorderLayout());

        row.add(boxes, BorderLayout.CENTER);
        row.add(buildResetButton(
            () -> {

                for (var index = 0; index < toggles.length; index++) {
                    
                    checks
                        .get(index)
                        .setSelected(toggles[index].fallback());

                    toggles[index]
                        .apply()
                        .accept(toggles[index].fallback());

                    saved.putBoolean(
                        toggles[index].key(),
                        toggles[index].fallback());
                }

                onChange.run();

            }),
            BorderLayout.EAST);

        row.setBorder(BorderFactory.createEmptyBorder(ROW_PADDING, 0, ROW_PADDING, 0));

        return row;
    }

    /**
     * A dropdown that remembers what was picked, with a reset button.
     *
     * @param key      what to remember it under
     * @param title    what the choice is called
     * @param options  what can be picked, the first of which is the default
     * @param apply    records the new pick
     * @param onChange what to run once it changes
     * @return the row
     */
    static JPanel buildChoice(
            String key,
            String title,
            List<String> options,
            Consumer<String> apply,
            Runnable onChange) {

        var fallback = options.get(0);

        // A remembered pick can name something that is no longer there - a fixture renamed or
        // removed between sessions - and a dropdown set to a value not in its own list shows
        // blank and cannot be put back except by picking something else.
        var saved = findSavedValues().get(key, fallback);

        var picked = options.contains(saved) ? saved : fallback;
        var choice = new JComboBox<>(options.toArray(new String[0]));

        choice.setSelectedItem(picked);

        choice.addActionListener(event -> {

            var selected = (String) choice.getSelectedItem();

            apply.accept(selected);

            findSavedValues().put(key, selected);

            onChange.run();
        });

        apply.accept(picked);

        var row = new JPanel(new BorderLayout());

        row.add(new JLabel(title), BorderLayout.NORTH);
        row.add(choice, BorderLayout.CENTER);
        row.add(buildResetButton(() -> choice.setSelectedItem(fallback)), BorderLayout.EAST);
        row.setBorder(BorderFactory.createEmptyBorder(ROW_PADDING, 0, ROW_PADDING, 0));

        return row;
    }

    // Where every knob's value is kept, in one place. Each factory reads on build and writes
    // on change, so the node was named at eleven separate call sites; one of them naming a
    // different class would have split the panel's memory in two without failing anything.
    //
    // Shared with the toggle block next door for exactly that reason: its switches are knobs
    // like any other and have to be remembered in the same node, not in one of their own.
    static Preferences findSavedValues() {
        return Preferences.userNodeForPackage(ViewerControls.class);
    }

    static JButton buildResetButton(Runnable reset) {

        var button = new JButton(RESET_LABEL);

        button.setToolTipText(RESET_TOOLTIP);
        button.setMargin(new java.awt.Insets(0, 0, 0, 0));
        button.setPreferredSize(new Dimension(RESET_BUTTON_WIDTH, RESET_BUTTON_HEIGHT));
        button.addActionListener(event -> reset.run());

        return button;
    }

    static JPanel layOutLabelledRow(
            String title,
            JTextField valueBox,
            Runnable reset,
            JSlider slider) {

        var heading = new JPanel(new BorderLayout());

        heading.add(new JLabel(title), BorderLayout.CENTER);

        var trailing = new JPanel(new BorderLayout());

        trailing.add(valueBox, BorderLayout.CENTER);
        trailing.add(buildResetButton(reset), BorderLayout.EAST);

        heading.add(trailing, BorderLayout.EAST);

        var panel = new JPanel(new GridLayout(LABELLED_ROWS, SINGLE_COLUMN));

        panel.add(heading);
        panel.add(slider);
        panel.setBorder(BorderFactory.createEmptyBorder(ROW_PADDING, 0, ROW_PADDING, 0));

        return panel;
    }
}
