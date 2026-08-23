package kmu.ui;

import java.awt.Dimension;
import java.util.Locale;
import java.util.function.DoubleConsumer;

import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;

/**
 * The viewer's one numeric knob: a slider with a typed value box beside it.
 *
 * <p>Its own class because it is the only control here with arithmetic in it. A slider carries
 * an integer step count and the knob it drives carries a real value, so every reading crosses
 * between the two - and the conversion, the clamping, the formatting and the parsing are four
 * places one rounding rule has to hold. Kept together, they are one rule; scattered among the
 * colour swatches and the checkboxes, they were four.
 *
 * <p>The two directions are guarded against each other. Writing the box from the slider is
 * harmless; moving the slider from a typed value would rewrite the box mid-edit and fight the
 * caret, so a typed value sets a flag the slider's own listener reads.
 */
public final class SliderRows {

    private static final int SLIDER_STEPS = 1000;

    private static final int VALUE_BOX_COLUMNS = 6;

    private static final int VALUE_BOX_WIDTH = 70;

    private static final int VALUE_BOX_HEIGHT = 20;

    private SliderRows() {
    }

    /**
     * Where a slider may go, and where it starts.
     *
     * <p>One value because a fallback outside its own range is not a knob anyone can reset, and
     * three loose numbers let exactly that compile. Passed apart, they were also three of eight
     * parameters with nothing but their order saying which was which.
     *
     * @param minimum  the value at the far left, and the floor a typed value clamps to
     * @param maximum  the value at the far right, and the ceiling
     * @param fallback the value to start at and to reset to
     */
    public record SliderRange(
        double minimum,
        double maximum,
        double fallback) {

        public SliderRange {
            if (fallback < minimum || fallback > maximum) {
                throw new IllegalArgumentException(
                    "a slider cannot start at " + fallback
                        + ", which is outside " + minimum + ".." + maximum);
            }
        }
    }

    /**
     * What a slider does as it moves.
     *
     * <p>Split in two because the cost is: a colour wants repainting on every pixel of travel,
     * while a geometry knob wants the sector rebuilt once, when the handle is let go. A single
     * callback would have to be the expensive one, and dragging a slider would rebuild the map
     * a hundred times.
     *
     * @param apply     records the new value; runs on every movement
     * @param onChanged cheap work to redo per movement, such as a repaint
     * @param onSettled expensive work to redo once the handle is released or a value typed
     */
    public record SliderWork(
        DoubleConsumer apply,
        Runnable onChanged,
        Runnable onSettled) {
    }

    /**
     * A slider with a typed value box and a reset button.
     *
     * @param key   what to remember it under
     * @param title what the knob is called
     * @param range where it may go and where it starts
     * @param work  what to do as it moves
     * @return the row
     */
    public static JPanel buildSlider(
            String key,
            String title,
            SliderRange range,
            SliderWork work) {

        var saved = readDouble(key, range.fallback());

        var parts = new SliderParts(
            new JSlider(0, SLIDER_STEPS, convertToStep(saved, range)),
            new JTextField(VALUE_BOX_COLUMNS),
            new boolean[1]);

        parts.valueBox().setMaximumSize(new Dimension(VALUE_BOX_WIDTH, VALUE_BOX_HEIGHT));

        bindHandleMovement(parts, key, range, work);
        bindTypedValue(parts, key, range, work);

        parts.valueBox().setText(formatValue(saved));
        work.apply().accept(saved);

        Runnable reset = () -> applyValue(parts, key, range, work, range.fallback());

        return ControlRows.layOutLabelledRow(
            title, parts.valueBox(), reset, parts.slider());
    }

    /**
     * The three widgets one slider is made of.
     *
     * <p>Together because the handlers below all need all three, and because the flag is only
     * meaningful beside the two it guards: it says the box is being written from a typed value,
     * so the slider's own listener must not write it back mid-edit and fight the caret.
     *
     * @param slider    the handle
     * @param valueBox  the typed value beside it
     * @param isSyncing whether the box is currently being driven rather than read
     */
    private record SliderParts(
        JSlider slider,
        JTextField valueBox,
        boolean[] isSyncing) {
    }

    // Dragging the handle: the box follows, the knob takes the value, and the expensive work
    // waits until the handle is let go.
    private static void bindHandleMovement(
            SliderParts parts,
            String key,
            SliderRange range,
            SliderWork work) {

        parts.slider().addChangeListener(event -> {

            var value = convertToValue(parts.slider().getValue(), range);

            if (!parts.isSyncing()[0]) {
                parts.valueBox().setText(formatValue(value));
            }

            work.apply().accept(value);

            writeDouble(key, value);

            work.onChanged().run();

            if (!parts.slider().getValueIsAdjusting()) {
                work.onSettled().run();
            }
        });
    }

    // Typing a value: anything unreadable puts the box back to where the handle stands, so a
    // half-typed number cannot leave the two showing different things.
    private static void bindTypedValue(
            SliderParts parts,
            String key,
            SliderRange range,
            SliderWork work) {

        parts.valueBox().addActionListener(event -> {

            var typed = parseValue(parts.valueBox().getText(), range);

            if (Double.isNaN(typed)) {

                parts.valueBox().setText(
                    formatValue(convertToValue(parts.slider().getValue(), range)));
                return;
            }
            applyValue(parts, key, range, work, typed);
        });
    }

    // One value put through everything: the widgets, the knob, the saved value and both halves
    // of the work. Shared by the typed box and the reset button, which are the same act.
    private static void applyValue(
            SliderParts parts,
            String key,
            SliderRange range,
            SliderWork work,
            double value) {

        applyTypedValue(parts, range, value);
        work.apply().accept(value);

        writeDouble(key, value);

        work.onChanged().run();
        work.onSettled().run();
    }

    // Set explicitly rather than left to the slider's own listener: the step a value
    // quantises to reads back as a slightly different number, and a box that silently
    // rewrote what was just typed would look broken.
    private static void applyTypedValue(SliderParts parts, SliderRange range, double value) {

        parts.isSyncing()[0] = true;
        parts.slider().setValue(convertToStep(value, range));
        parts.isSyncing()[0] = false;
        parts.valueBox().setText(formatValue(value));
    }

    private static double readDouble(String key, double fallback) {
        return SavedValues.findSavedValues().getDouble(key, fallback);
    }

    private static void writeDouble(String key, double value) {
        SavedValues.findSavedValues().putDouble(key, value);
    }

    // Out-of-range clamps rather than being rejected, so typing a round number past the end
    // of a range lands on the end; unparseable text is rejected, since there is no sensible
    // value to guess from it.
    private static double parseValue(String text, SliderRange range) {
        try {
            return Math.max(
                range.minimum(),
                Math.min(range.maximum(), Double.parseDouble(text.trim())));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static int convertToStep(double value, SliderRange range) {
        return (int) Math.round(
            (value - range.minimum()) / (range.maximum() - range.minimum()) * SLIDER_STEPS);
    }

    private static double convertToValue(int step, SliderRange range) {
        return range.minimum()
            + (range.maximum() - range.minimum()) * step / (double) SLIDER_STEPS;
    }

    private static String formatValue(double value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }
}
