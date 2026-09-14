package kmu.desktop.ui.swing;

import kmu.desktop.ui.SavedValues;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

/**
 * The viewer's smaller knobs, and the furniture every knob in the panel shares.
 *
 * <p>Every control in the panel is the same three things - read the saved value on build, write
 * it on change, restore the default on demand - and writing that out per knob is how a panel of
 * twenty ends up with two that quietly do not persist. So where the value is KEPT, what a reset
 * button looks like, and how a labelled row is laid out live here, and the controls that are
 * substantial enough to have their own arithmetic or their own look live beside this in
 * {@link SliderRows} and {@link ColourRows}.
 *
 * <p>Persistence is deliberate rather than a convenience. A geometric knob is only meaningful
 * at a particular zoom on a particular sector, and re-establishing a dozen of them before
 * every session is enough friction to stop someone checking a shape they would otherwise have
 * checked. The reset button is the other half: a knob that cannot be put back is a knob
 * people stop turning.
 */
public final class ControlRows {

    private static final int ROW_PADDING = 4;

    // Wider than a row's own padding, so the rule reads as a break between subjects
    // rather than as one more gap in an evenly spaced column.
    private static final int DIVIDER_PADDING = 8;

    private static final int SINGLE_ROW = 1;

    private static final int RESET_BUTTON_WIDTH = 22;

    private static final int RESET_BUTTON_HEIGHT = 18;

    // ASCII only: the viewer runs wherever the JDK's default font does, and a glyph that
    // renders as a box on one machine makes the control unreadable rather than merely plain.
    private static final String RESET_LABEL = "R";
    private static final String RESET_TOOLTIP = "Reset to default";

    private ControlRows() {
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
    public static JPanel buildDivider() {

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
     * A checkbox that remembers itself, without a row round it.
     *
     * <p>The whole of what makes a switch remembered: it opens on what was last chosen, its
     * owner is told that before anything is drawn, and each click records the new state before
     * anything acts on it. Shared because more than one thing wants that behaviour without
     * wanting this class's row - a section heading is a switch with a fold button beside it
     * rather than a label and a reset.
     *
     * <p><b>The owner is told the remembered state before the control is returned.</b> Without
     * it a switch left off last time comes back drawn as off but with whatever it governs
     * still on, and the first click appears to turn the thing OFF while turning it on. That
     * ordering is the reason this is one method rather than a pattern each caller repeats.
     *
     * @param key      what to remember it under
     * @param title    what the switch is called
     * @param fallback the state to start in when nothing is remembered
     * @param apply    records the new state
     * @param onChange what to run once it changes
     * @return the checkbox
     */
    public static JCheckBox buildRememberedCheckBox(
            String key,
            String title,
            boolean fallback,
            Consumer<Boolean> apply,
            Runnable onChange) {

        var saved = SavedValues.findSavedValues().getBoolean(key, fallback);
        var toggle = new JCheckBox(title, saved);

        toggle.addActionListener(event -> {

            apply.accept(toggle.isSelected());

            SavedValues.findSavedValues().putBoolean(key, toggle.isSelected());

            onChange.run();
        });

        apply.accept(saved);

        return toggle;
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
    public static JPanel buildToggle(
            String key,
            String title,
            boolean fallback,
            Consumer<Boolean> apply,
            Runnable onChange) {

        var toggle = buildRememberedCheckBox(key, title, fallback, apply, onChange);
        var row = new JPanel(new BorderLayout());

        row.add(toggle, BorderLayout.CENTER);
        row.add(buildResetButton(
            () -> {

                toggle.setSelected(fallback);
                apply.accept(fallback);

                SavedValues.findSavedValues().putBoolean(key, fallback);

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
    public record Toggle(
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
    public static JPanel buildToggleRow(Runnable onChange, Toggle... toggles) {

        var boxes = new JPanel(new GridLayout(SINGLE_ROW, toggles.length));
        var saved = SavedValues.findSavedValues();
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
    public static JPanel buildChoice(
            String key,
            String title,
            List<String> options,
            Consumer<String> apply,
            Runnable onChange) {

        var fallback = options.get(0);

        // A remembered pick can name something that is no longer there - a fixture renamed or
        // removed between sessions - and a dropdown set to a value not in its own list shows
        // blank and cannot be put back except by picking something else.
        var saved = SavedValues.findSavedValues().get(key, fallback);

        var picked = options.contains(saved) ? saved : fallback;
        var choice = new JComboBox<>(options.toArray(new String[0]));

        choice.setSelectedItem(picked);

        choice.addActionListener(event -> {

            var selected = (String) choice.getSelectedItem();

            apply.accept(selected);

            SavedValues.findSavedValues().put(key, selected);

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

    /**
     * One option in a radio row.
     *
     * @param label what it is called on screen
     * @param value what picking it means
     * @param <T>   what the row picks between
     */
    public record Pick<T>(
        String label,
        T value) {
    }

    /**
     * A row of radio buttons that remembers which is picked, with a reset button.
     *
     * <p>A radio rather than the dropdown next door where the options are few and the point is
     * comparing them: every answer stays on screen, so a reader can see what else the map could
     * be showing without opening anything to find out.
     *
     * <p>Remembered under the option's own value rather than its label, so rewording a label
     * does not silently put everyone back to the default.
     *
     * <p><b>The owner is told the remembered pick before the row is returned</b>, for the
     * reason {@link #buildRememberedCheckBox} is: a row drawing one answer while its owner
     * holds another is a control whose first use appears to do the wrong thing.
     *
     * @param key      what to remember it under
     * @param title    what the choice is called
     * @param picks    what can be picked, the first of which is the default
     * @param apply    records the new pick
     * @param onChange what to run once it changes
     * @param <T>      what the row picks between
     * @return the row
     */
    public static <T> JPanel buildRadio(
            String key,
            String title,
            List<Pick<T>> picks,
            Consumer<T> apply,
            Runnable onChange) {

        var fallback = picks.get(0);

        // A remembered pick can name an option that is no longer offered, the way the dropdown's
        // can, and a radio group with nothing selected shows every button empty.
        var pickedIndex = findIndexOfPickNamed(
            picks,
            SavedValues.findSavedValues().get(key, resolveNameOf(fallback)));

        var group = new ButtonGroup();
        var buttons = new JPanel(new GridLayout(SINGLE_ROW, picks.size()));
        var everyButton = new ArrayList<JRadioButton>(picks.size());

        for (var index = 0; index < picks.size(); index++) {

            var pick = picks.get(index);
            var button = new JRadioButton(pick.label(), index == pickedIndex);

            button.addActionListener(event -> recordPick(key, pick, apply, onChange));

            group.add(button);
            buttons.add(button);
            everyButton.add(button);
        }

        apply.accept(picks.get(pickedIndex).value());

        var row = new JPanel(new BorderLayout());

        row.add(new JLabel(title), BorderLayout.NORTH);
        row.add(buttons, BorderLayout.CENTER);
        row.add(buildResetButton(
            () -> {

                everyButton.get(0).setSelected(true);

                recordPick(key, fallback, apply, onChange);

            }),
            BorderLayout.EAST);

        row.setBorder(BorderFactory.createEmptyBorder(ROW_PADDING, 0, ROW_PADDING, 0));

        return row;
    }

    /**
     * The small square button that puts one control back to its default.
     *
     * <p>Every remembered control carries one, in the same place and at the same size, so a
     * reader who has moved something and wants it back does not have to remember what it was.
     * That is the whole reason the defaults are worth naming: a knob nobody can undo is a knob
     * nobody experiments with.
     *
     * @param reset what to do when it is pressed, which is the control's own business
     * @return the button
     */
    public static JButton buildResetButton(Runnable reset) {

        var button = new JButton(RESET_LABEL);

        button.setToolTipText(RESET_TOOLTIP);
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setPreferredSize(new Dimension(RESET_BUTTON_WIDTH, RESET_BUTTON_HEIGHT));
        button.addActionListener(event -> reset.run());

        return button;
    }

    /**
     * One control's row: its name on the left, its value and reset on the right, and whatever
     * wide widget it has below.
     *
     * <p>Shared so that every row in a panel lines up. A row laid out where it is declared is
     * a row whose padding and column widths are its own, and a column of those reads as a list
     * of unrelated controls rather than as one panel.
     *
     * <p>The name WRAPS rather than being cut off. A label is the only part of a row that has
     * anything to say, and the column is narrow enough that several of these are already too
     * long for one line of it - so the name takes the lines it needs and the row grows, which
     * is the one arrangement where nothing has to be shortened to fit and nothing is lost to
     * the edge.
     *
     * @param title    what the control is called
     * @param valueBox the box showing its value, beside the reset
     * @param reset    what to do when the reset is pressed
     * @param slider   the track spanning the full width beneath, which every row of this
     *                 shape has
     * @return the row
     */
    public static JPanel layOutLabelledRow(
            String title,
            JTextField valueBox,
            Runnable reset,
            JSlider slider) {

        var heading = new JPanel(new BorderLayout());

        heading.add(buildWrappingLabel(title), BorderLayout.CENTER);

        var trailing = new JPanel(new BorderLayout());

        trailing.add(valueBox, BorderLayout.CENTER);
        trailing.add(buildResetButton(reset), BorderLayout.EAST);

        // Held to the top of the side rather than filling it, so the value and the reset stay
        // level with the name's FIRST line instead of drifting to the middle of a name that has
        // grown to three.
        var side = new JPanel(new BorderLayout());

        side.add(trailing, BorderLayout.NORTH);
        heading.add(side, BorderLayout.EAST);

        // A border rather than a grid of two equal rows. A grid gives the heading whatever
        // height the track takes, which is the arrangement that cut a wrapped name off at one
        // line; under this the heading keeps the height its name needs and the track keeps its
        // own.
        var panel = new JPanel(new BorderLayout());

        panel.add(heading, BorderLayout.NORTH);
        panel.add(slider, BorderLayout.CENTER);
        panel.setBorder(BorderFactory.createEmptyBorder(ROW_PADDING, 0, ROW_PADDING, 0));

        return panel;
    }

    /**
     * A label that wraps instead of running off the end of its column.
     *
     * <p>A {@link JLabel} truncates, which loses the end of the name - and it is the end that
     * carries the units, so a cut label reads as a different knob rather than as a shortened
     * one. A text area laid out to look like a label wraps to whatever width it is given, which
     * is what lets a narrow column hold a long name without anything being reworded to fit.
     *
     * <p>Not editable, not focusable and not painted: everything that makes a text area a text
     * area is turned off, and what is left is a label that wraps.
     *
     * @param text what it says
     * @return the label
     */
    public static JTextArea buildWrappingLabel(String text) {

        var label = new JTextArea(text);

        label.setLineWrap(true);
        label.setWrapStyleWord(true);
        label.setEditable(false);
        label.setFocusable(false);
        label.setOpaque(false);
        label.setBorder(null);

        // Taken from the look a real label has rather than left at a text area's, which is a
        // different font and a different colour - the point is that this reads as a label.
        label.setFont(UIManager.getFont("Label.font"));
        label.setForeground(UIManager.getColor("Label.foreground"));

        return label;
    }

    // The one order a pick is acted on in, shared by the buttons and the reset so the two
    // cannot come to disagree: the owner is told, the choice is written down, and only then
    // does anything redraw off it.
    private static <T> void recordPick(
            String key,
            Pick<T> pick,
            Consumer<T> apply,
            Runnable onChange) {

        apply.accept(pick.value());

        SavedValues.findSavedValues().put(key, resolveNameOf(pick));

        onChange.run();
    }

    // Which option a remembered name stands for, or the first where it stands for none.
    private static <T> int findIndexOfPickNamed(List<Pick<T>> picks, String name) {

        for (var index = 0; index < picks.size(); index++) {
            if (resolveNameOf(picks.get(index)).equals(name)) {
                return index;
            }
        }
        return 0;
    }

    // What a pick is written down as. The value's own name rather than the label beside it: a
    // label is copy and gets reworded, and a saved file keyed by copy forgets what was picked
    // the first time someone improves the wording.
    private static String resolveNameOf(Pick<?> pick) {
        return String.valueOf(pick.value());
    }
}
