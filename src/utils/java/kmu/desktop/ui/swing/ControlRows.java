package kmu.desktop.ui.swing;

import kmu.desktop.ui.SavedValues;

import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JRadioButton;

/**
 * The viewer's smaller knobs: the ones whose whole job is to remember what was chosen.
 *
 * <p>Every control in the panel is the same three things - read the saved value on build, write
 * it on change, restore the default on demand - and writing that out per knob is how a panel of
 * twenty ends up with two that quietly do not persist. So where a value is KEPT lives here,
 * what a row is made of and how it is laid out lives in {@link RowFurniture}, and the controls
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

    private ControlRows() {
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

        return RowFurniture.layOutControlRow(
            toggle,
            () -> {

                toggle.setSelected(fallback);
                apply.accept(fallback);

                SavedValues.findSavedValues().putBoolean(key, fallback);

                onChange.run();
            });
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

        var boxes = new JPanel(new GridLayout(RowFurniture.SINGLE_ROW, toggles.length));
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

        return RowFurniture.layOutControlRow(
            boxes,
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
            });
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

        return RowFurniture.layOutNamedControlRow(
            title, choice, () -> choice.setSelectedItem(fallback));
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
        var buttons = new JPanel(new GridLayout(RowFurniture.SINGLE_ROW, picks.size()));
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

        return RowFurniture.layOutNamedControlRow(
            title,
            buttons,
            () -> {

                everyButton.get(0).setSelected(true);

                recordPick(key, fallback, apply, onChange);
            });
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
