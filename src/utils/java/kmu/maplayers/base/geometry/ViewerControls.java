package kmu.maplayers.base.geometry;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.prefs.Preferences;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;

/**
 * The viewer's knobs: a slider, a toggle and a colour, each remembering where it was left and
 * each able to go back to its default.
 *
 * <p>Every control here is the same three things - read the saved value on build, write it on
 * change, restore the default on demand - and writing that out per knob is how a panel of
 * twenty ends up with two that quietly do not persist. One factory per kind keeps the three
 * inseparable.
 *
 * <p>Persistence is deliberate rather than a convenience. A geometric knob is only meaningful
 * at a particular zoom on a particular sector, and re-establishing a dozen of them before
 * every session is enough friction to stop someone checking a shape they would otherwise have
 * checked. The reset button is the other half: a knob that cannot be put back is a knob
 * people stop turning.
 */
final class ViewerControls {

    private static final int SLIDER_STEPS = 1000;
    private static final int ROW_PADDING = 4;
    private static final int LABELLED_ROWS = 2;
    private static final int SINGLE_COLUMN = 1;
    private static final int SINGLE_ROW = 1;
    private static final int VALUE_BOX_COLUMNS = 6;
    private static final int VALUE_BOX_WIDTH = 70;
    private static final int VALUE_BOX_HEIGHT = 20;
    private static final int RESET_BUTTON_WIDTH = 22;
    private static final int RESET_BUTTON_HEIGHT = 18;
    private static final int SWATCH_WIDTH = 40;

    // Half the height a labelled row takes. A swatch carries a colour and nothing else, so
    // the height only has to be enough to see the colour and hit the button - and there are
    // four of these rows competing with the sliders for the panel.
    private static final int SWATCH_HEIGHT = 11;
    private static final int SWATCH_ROW_PADDING = 1;

    // ASCII only: the viewer runs wherever the JDK's default font does, and a glyph that
    // renders as a box on one machine makes the control unreadable rather than merely plain.
    private static final String RESET_LABEL = "R";
    private static final String RESET_TOOLTIP = "Reset to default";

    private ViewerControls() {
    }

    /**
     * A slider with a typed value box and a reset button.
     *
     * @param key       what to remember it under
     * @param title     what the knob is called
     * @param minimum   the value at the far left, and the floor a typed value clamps to
     * @param maximum   the value at the far right, and the ceiling
     * @param fallback  the value to start at and to reset to
     * @param apply     records the new value; runs on every movement
     * @param onChanged cheap work to redo per movement, such as a repaint
     * @param onSettled expensive work to redo once the handle is released or a value typed
     * @return the row
     */
    static JPanel buildSlider(
            String key,
            String title,
            double minimum,
            double maximum,
            double fallback,
            DoubleConsumer apply,
            Runnable onChanged,
            Runnable onSettled) {

        var saved = readDouble(key, fallback);
        var slider = new JSlider(0, SLIDER_STEPS, toStep(saved, minimum, maximum));
        var valueBox = new JTextField(VALUE_BOX_COLUMNS);

        valueBox.setMaximumSize(new Dimension(VALUE_BOX_WIDTH, VALUE_BOX_HEIGHT));

        // Guards the two directions against each other: writing the box from the slider is
        // harmless, but moving the slider from a typed value would otherwise rewrite the box
        // mid-edit and fight the caret.
        var isSyncing = new boolean[1];

        slider.addChangeListener(event -> {

            var value = toValue(slider.getValue(), minimum, maximum);

            if (!isSyncing[0]) {
                valueBox.setText(formatValue(value));
            }

            apply.accept(value);

            writeDouble(key, value);

            onChanged.run();

            if (!slider.getValueIsAdjusting()) {
                onSettled.run();
            }
        });
        valueBox.addActionListener(event -> {

            var typed = parseValue(valueBox.getText(), minimum, maximum);

            if (Double.isNaN(typed)) {

                valueBox.setText(formatValue(toValue(slider.getValue(), minimum, maximum)));
                return;
            }

            applyTypedValue(slider, valueBox, isSyncing, typed, minimum, maximum);
            apply.accept(typed);

            writeDouble(key, typed);

            onChanged.run();
            onSettled.run();
        });

        valueBox.setText(formatValue(saved));

        apply.accept(saved);

        Runnable reset = () -> {

            applyTypedValue(slider, valueBox, isSyncing, fallback, minimum, maximum);
            apply.accept(fallback);

            writeDouble(key, fallback);

            onChanged.run();
            onSettled.run();
        };
        return layOutLabelledRow(title, valueBox, reset, slider);
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

        var saved = Preferences
            .userNodeForPackage(ViewerControls.class)
            .getBoolean(key, fallback);

        var toggle = new JCheckBox(title, saved);

        toggle.addActionListener(event -> {

            apply.accept(toggle.isSelected());

            Preferences
                .userNodeForPackage(ViewerControls.class)
                .putBoolean(key, toggle.isSelected());

            onChange.run();
        });

        apply.accept(saved);

        var row = new JPanel(new BorderLayout());

        row.add(toggle, BorderLayout.CENTER);
        row.add(buildResetButton(
            () -> {

                toggle.setSelected(fallback);
                apply.accept(fallback);

                Preferences
                    .userNodeForPackage(ViewerControls.class)
                    .putBoolean(key, fallback);

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
        var saved = Preferences.userNodeForPackage(ViewerControls.class);
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
     * One colour on its own line, remembered, with a reset.
     *
     * <p>Separate from the pair because some things are one colour. Handing a pair to
     * something with only one, and pointing both halves at the same field, means whichever
     * swatch is touched last silently wins and the other reads as broken.
     *
     * @param key      what to remember it under
     * @param title    what the colour is for
     * @param fallback the colour to start at and to reset to
     * @param apply    records the new colour
     * @param onChange what to run once it changes
     * @return the row
     */
    static JPanel buildColour(
            String key,
            String title,
            Color fallback,
            Consumer<Color> apply,
            Runnable onChange) {

        var swatch = buildSwatch(key, title, fallback, apply, onChange);
        var trailing = new JPanel(new BorderLayout());

        trailing.add(swatch, BorderLayout.CENTER);
        trailing.add(buildResetButton(
            () -> {

                resetSwatch(swatch, key, fallback, apply);
                onChange.run();

            }),
            BorderLayout.EAST);

        var row = new JPanel(new BorderLayout());

        row.add(new JLabel(title), BorderLayout.CENTER);
        row.add(trailing, BorderLayout.EAST);
        row.setBorder(BorderFactory.createEmptyBorder(
            SWATCH_ROW_PADDING,
            0,
            SWATCH_ROW_PADDING,
            0));

        return row;
    }

    /**
     * A fill colour and an outline colour on one line, each remembering its choice, with a
     * reset for the pair.
     *
     * <p>Together rather than as two rows because they are one decision. A layer is drawn as
     * a translucent body under an opaque edge, and choosing those apart - on separate rows,
     * with the panel scrolled between them - is how a fill ends up with an outline nothing
     * like it. Side by side they are compared while being picked.
     *
     * @param key           what to remember them under; each takes its own suffix
     * @param title         what the pair is for
     * @param fillFallback  the fill to start at and reset to
     * @param edgeFallback  the outline to start at and reset to
     * @param applyFill     records the new fill
     * @param applyEdge     records the new outline
     * @param onChange      what to run once either changes
     * @return the row
     */
    static JPanel buildColourPair(
            String key,
            String title,
            Color fillFallback,
            Color edgeFallback,
            Consumer<Color> applyFill,
            Consumer<Color> applyEdge,
            Runnable onChange) {
                
        var fill = buildSwatch(
            key + " fill",
            title + " fill",
            fillFallback,
            applyFill,
            onChange);

        var edge = buildSwatch(
            key + " outline",
            title + " outline",
            edgeFallback,
            applyEdge,
            onChange);

        var swatches = new JPanel(new GridLayout(SINGLE_ROW, 2));

        swatches.add(fill);
        swatches.add(edge);

        var trailing = new JPanel(new BorderLayout());

        trailing.add(swatches, BorderLayout.CENTER);
        trailing.add(buildResetButton(
            () -> {

                resetSwatch(fill, key + " fill", fillFallback, applyFill);
                resetSwatch(edge, key + " outline", edgeFallback, applyEdge);

                onChange.run();

            }),
            BorderLayout.EAST);

        var row = new JPanel(new BorderLayout());

        row.add(new JLabel(title), BorderLayout.CENTER);
        row.add(trailing, BorderLayout.EAST);
        row.setBorder(BorderFactory.createEmptyBorder(
            SWATCH_ROW_PADDING,
            0,
            SWATCH_ROW_PADDING,
            0));

        return row;
    }

    private static JButton buildSwatch(
            String key,
            String title,
            Color fallback,
            Consumer<Color> apply,
            Runnable onChange) {

        var saved = new Color(
            Preferences.userNodeForPackage(ViewerControls.class)
                .getInt(key, fallback.getRGB()),
            true);

        var swatch = new JButton();

        swatch.setPreferredSize(new Dimension(SWATCH_WIDTH, SWATCH_HEIGHT));
        swatch.setBackground(saved);
        swatch.setOpaque(true);
        swatch.setToolTipText(title);

        apply.accept(saved);

        swatch.addActionListener(event -> {

            var chosen = JColorChooser.showDialog(swatch, title, swatch.getBackground());

            if (chosen == null) {
                return;
            }

            swatch.setBackground(chosen);
            apply.accept(chosen);

            Preferences
                .userNodeForPackage(ViewerControls.class)
                .putInt(key, chosen.getRGB());

            onChange.run();
        });
        return swatch;
    }

    private static void resetSwatch(
            JButton swatch,
            String key,
            Color fallback,
            Consumer<Color> apply) {

        swatch.setBackground(fallback);
        apply.accept(fallback);

        Preferences
            .userNodeForPackage(ViewerControls.class)
            .putInt(key, fallback.getRGB());
    }

    private static JButton buildResetButton(Runnable reset) {

        var button = new JButton(RESET_LABEL);

        button.setToolTipText(RESET_TOOLTIP);
        button.setMargin(new java.awt.Insets(0, 0, 0, 0));
        button.setPreferredSize(new Dimension(RESET_BUTTON_WIDTH, RESET_BUTTON_HEIGHT));
        button.addActionListener(event -> reset.run());

        return button;
    }

    private static JPanel layOutLabelledRow(
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

    // Set explicitly rather than left to the slider's own listener: the step a value
    // quantises to reads back as a slightly different number, and a box that silently
    // rewrote what was just typed would look broken.
    private static void applyTypedValue(
            JSlider slider,
            JTextField valueBox,
            boolean[] isSyncing,
            double value,
            double minimum,
            double maximum) {
                
        isSyncing[0] = true;
        slider.setValue(toStep(value, minimum, maximum));
        isSyncing[0] = false;
        valueBox.setText(formatValue(value));
    }

    private static double readDouble(String key, double fallback) {
        return Preferences
            .userNodeForPackage(ViewerControls.class)
            .getDouble(key, fallback);
    }

    private static void writeDouble(String key, double value) {
        Preferences
            .userNodeForPackage(ViewerControls.class)
            .putDouble(key, value);
    }

    // Out-of-range clamps rather than being rejected, so typing a round number past the end
    // of a range lands on the end; unparseable text is rejected, since there is no sensible
    // value to guess from it.
    private static double parseValue(String text, double minimum, double maximum) {
        try {
            return Math.max(minimum, Math.min(maximum, Double.parseDouble(text.trim())));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static int toStep(double value, double minimum, double maximum) {
        return (int) Math.round((value - minimum) / (maximum - minimum) * SLIDER_STEPS);
    }

    private static double toValue(int step, double minimum, double maximum) {
        return minimum + (maximum - minimum) * step / (double) SLIDER_STEPS;
    }

    private static String formatValue(double value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }
}
