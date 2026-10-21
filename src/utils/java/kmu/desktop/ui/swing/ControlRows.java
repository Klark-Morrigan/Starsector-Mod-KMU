package kmu.desktop.ui.swing;

import kmu.desktop.ui.RememberedValue;
import kmu.desktop.ui.SavedValueFormat;

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
 * The smaller remembered controls: the ones whose whole job is to keep what was chosen.
 *
 * <p>Where a value is KEPT is {@link RememberedValue}'s, what a row is made of and how it is laid
 * out is {@link RowFurniture}'s, and the controls substantial enough to have their own arithmetic
 * or their own look live beside this in {@link SliderRows} and {@link ColourRows}.
 *
 * <p>Every control here carries a reset beside it. A control that cannot be put back is one
 * people stop turning, so the reset is as much a part of the row as the control is.
 */
public final class ControlRows {

    private ControlRows() {
    }

    /**
     * A checkbox that remembers itself, without a row round it.
     *
     * <p>Shared because more than one thing wants a remembered switch without wanting this
     * class's row - a section heading is a switch with a fold button beside it rather than a
     * label and a reset.
     *
     * <p><b>The owner is told the remembered state before the control is returned.</b> Without
     * it a switch left off last time comes back drawn as off with whatever it governs still on,
     * and the first click appears to turn the thing OFF while turning it on.
     *
     * @param title    what the switch is called
     * @param value    what it remembers and tells
     * @param onChange what to run once a click has changed it
     * @return the checkbox
     */
    public static JCheckBox buildRememberedCheckBox(
            String title,
            RememberedValue<Boolean> value,
            Runnable onChange) {

        var toggle = new JCheckBox(title, value.applyRemembered());

        toggle.addActionListener(event -> {

            value.recordValue(toggle.isSelected());
            onChange.run();
        });

        return toggle;
    }

    /**
     * A checkbox that remembers itself, with a reset button.
     *
     * @param title    what the toggle is called
     * @param value    what it remembers and tells
     * @param onChange what to run once it changes
     * @return the row
     */
    public static JPanel buildToggle(
            String title,
            RememberedValue<Boolean> value,
            Runnable onChange) {

        var toggle = buildRememberedCheckBox(title, value, onChange);

        return RowFurniture.layOutControlRow(
            toggle,
            () -> {

                toggle.setSelected(value.fallback());
                value.recordValue(value.fallback());

                onChange.run();
            });
    }

    /**
     * One checkbox in a row of them.
     *
     * @param title what the toggle is called
     * @param value what it remembers and tells
     */
    public record Toggle(
        String title,
        RememberedValue<Boolean> value) {
    }

    /**
     * Several checkboxes side by side on one line, with a single reset for the row.
     *
     * <p>A toggle carries one bit and needs one line's worth of height to say so. Giving each a
     * row of its own, as tall as a slider, pushes the controls that do need the space off the
     * bottom of the column - so related toggles share a line.
     *
     * @param onChange what to run once any of them changes
     * @param toggles  the checkboxes, left to right
     * @return the row
     */
    public static JPanel buildToggleRow(Runnable onChange, Toggle... toggles) {

        var boxes = new JPanel(new GridLayout(RowFurniture.SINGLE_ROW, toggles.length));
        var checks = new ArrayList<JCheckBox>(toggles.length);

        for (var toggle : toggles) {

            var check = new JCheckBox(toggle.title(), toggle.value().applyRemembered());

            check.addActionListener(event -> {

                toggle.value().recordValue(check.isSelected());
                onChange.run();
            });

            checks.add(check);
            boxes.add(check);
        }

        return RowFurniture.layOutControlRow(
            boxes,
            () -> {

                for (var index = 0; index < toggles.length; index++) {

                    var value = toggles[index].value();

                    checks
                        .get(index)
                        .setSelected(value.fallback());
                    value
                        .recordValue(value.fallback());
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
     * @param onChange what to run once a pick has changed it
     * @return the row
     */
    public static JPanel buildChoice(
            String key,
            String title,
            List<String> options,
            Consumer<String> apply,
            Runnable onChange) {

        var value = new RememberedValue<>(
            key,
            options.get(0),
            SavedValueFormat.createNamedFormat(options),
            apply);

        var choice = new JComboBox<>(options.toArray(new String[0]));

        choice.setSelectedItem(value.applyRemembered());
        choice.addActionListener(event -> {

            value.recordValue((String) choice.getSelectedItem());
            onChange.run();
        });

        return RowFurniture.layOutNamedControlRow(
            title,
            choice,
            () -> choice.setSelectedItem(value.fallback()));
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
     * The remembered value a radio row over these picks keeps.
     *
     * <p>Built here rather than by each caller, because the radio and anything reading its value
     * without one have to agree on how a pick is written down: under the option's own value
     * rather than its label, so rewording a label does not silently put everyone back to the
     * fallback.
     *
     * @param key      what to remember it under
     * @param picks    what can be picked
     * @param fallback what it is when nothing is remembered, and what the reset puts back
     * @param apply    records the new pick
     * @param <T>      what the row picks between
     * @return the value
     */
    public static <T> RememberedValue<T> createRememberedPick(
            String key,
            List<Pick<T>> picks,
            T fallback,
            Consumer<T> apply) {

        var offered = picks
            .stream()
            .map(Pick::value)
            .toList();

        return new RememberedValue<>(
            key,
            fallback,
            SavedValueFormat.createNamedFormat(offered),
            apply);
    }

    /**
     * A row of radio buttons that remembers which is picked, with a reset button.
     *
     * <p>A radio rather than the dropdown next door where the options are few and the point is
     * comparing them: every answer stays on screen, so a reader can see what else could be
     * picked without opening anything to find out.
     *
     * <p><b>The owner is told the remembered pick before the row is returned</b>, for the reason
     * {@link #buildRememberedCheckBox} is: a row drawing one answer while its owner holds another
     * is a control whose first use appears to do the wrong thing.
     *
     * @param title    what the choice is called
     * @param picks    what can be picked
     * @param value    what it remembers and tells, from {@link #createRememberedPick}
     * @param onChange what to run once a pick has changed it
     * @param <T>      what the row picks between
     * @return the row
     */
    public static <T> JPanel buildRadio(
            String title,
            List<Pick<T>> picks,
            RememberedValue<T> value,
            Runnable onChange) {

        var picked = value.applyRemembered();
        var group = new ButtonGroup();
        var buttons = new JPanel(new GridLayout(RowFurniture.SINGLE_ROW, picks.size()));
        var everyButton = new ArrayList<JRadioButton>(picks.size());

        for (var pick : picks) {

            var button = new JRadioButton(pick.label(), pick.value().equals(picked));

            button.addActionListener(event -> {

                value.recordValue(pick.value());
                onChange.run();
            });

            group.add(button);
            buttons.add(button);
            everyButton.add(button);
        }

        return RowFurniture.layOutNamedControlRow(
            title,
            buttons,
            () -> {

                everyButton
                    .get(findIndexOfValue(picks, value.fallback()))
                    .setSelected(true);

                value.recordValue(value.fallback());
                onChange.run();
            });
    }

    // Which button stands for a value. Always found for the fallback, which a caller picks from
    // the same options it offers.
    private static <T> int findIndexOfValue(List<Pick<T>> picks, T value) {

        for (var index = 0; index < picks.size(); index++) {

            if (picks.get(index).value().equals(value)) {
                return index;
            }
        }

        throw new IllegalArgumentException(value + " is not one of the options offered");
    }
}
