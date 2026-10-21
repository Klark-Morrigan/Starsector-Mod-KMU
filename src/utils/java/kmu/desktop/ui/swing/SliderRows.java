package kmu.desktop.ui.swing;

import kmu.desktop.ui.RememberedValue;

import java.awt.Dimension;
import java.util.Locale;

import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;

/**
 * A remembered number: a slider with a typed value box beside it.
 *
 * <p>Its own class because it is the only control here with arithmetic in it. A slider carries
 * an integer step count and the value it drives is a real number, so every reading crosses
 * between the two - and the conversion, the clamping, the formatting and the parsing are four
 * places one rounding rule has to hold. Kept together, they are one rule.
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
     * Where a slider may go.
     *
     * @param minimum the value at the far left, and the floor a typed value clamps to
     * @param maximum the value at the far right, and the ceiling
     */
    public record SliderRange(
        double minimum,
        double maximum) {

        /**
         * @param value a value
         * @return whether the slider can stand at it
         */
        public boolean contains(double value) {
            return value >= minimum && value <= maximum;
        }
    }

    /**
     * What a slider does as it moves, beyond telling its owner.
     *
     * <p>Split in two because the cost is: cheap work wants redoing on every pixel of travel,
     * while expensive work wants doing once, when the handle is let go. A single callback would
     * have to be the expensive one, and dragging a slider would run it a hundred times.
     *
     * @param onChanged cheap work to redo per movement, such as a repaint
     * @param onSettled expensive work to redo once the handle is released or a value typed
     */
    public record SliderWork(
        Runnable onChanged,
        Runnable onSettled) {
    }

    /**
     * Everything one slider is asked for, as a single value.
     *
     * <p>A fallback outside its own range is not a value anyone can reset to, so the two are
     * checked against each other here, where they first meet.
     *
     * @param title what the slider is called
     * @param range where it may go
     * @param value what it remembers and tells; its fallback is where it starts and resets to
     * @param work  what else to do as it moves
     */
    public record SliderSpec(
        String title,
        SliderRange range,
        RememberedValue<Double> value,
        SliderWork work) {

        public SliderSpec {

            if (!range.contains(value.fallback())) {

                throw new IllegalArgumentException(
                    "a slider cannot start at " + value.fallback()
                        + ", which is outside " + range.minimum() + ".." + range.maximum());
            }
        }
    }

    /**
     * A slider with a typed value box and a reset button.
     *
     * @param spec the slider
     * @return the row
     */
    public static JPanel buildSlider(SliderSpec spec) {

        return RowFurniture.layOutLabelledRow(describeRow(spec));
    }

    /**
     * Two sliders on one line, each with its name above its own value and reset.
     *
     * <p>Stacked rather than laid out as a full-width row is, because half a column does not
     * leave a name room beside its value box: a long name wraps to four lines beside the value
     * in half a column and to two above it. So the pairing and the shape are one decision, made
     * here, and a caller cannot ask for one without the other.
     *
     * @param left  the slider on the left
     * @param right the slider on the right
     * @return the line holding both
     */
    public static JPanel buildSliderPair(SliderSpec left, SliderSpec right) {

        return RowFurniture.layOutPairedRow(layOutStacked(left), layOutStacked(right));
    }

    // Out-of-range clamps rather than being rejected, so typing a round number past the end
    // of a range lands on the end; unparseable text is rejected, since there is no sensible
    // value to guess from it.
    static double parseValue(String text, SliderRange range) {

        try {
            return Math.max(
                range.minimum(),
                Math.min(range.maximum(), Double.parseDouble(text.trim())));

        } catch (NumberFormatException numberFormatException) {
            return Double.NaN;
        }
    }

    // One slider in the shape a paired line wants.
    private static JPanel layOutStacked(SliderSpec spec) {

        return RowFurniture.layOutStackedRow(describeRow(spec));
    }

    // One slider built and named, in the shape either row takes. Shared because the two shapes
    // differ in where the name sits and in nothing else.
    private static RowFurniture.RowParts describeRow(SliderSpec spec) {

        var parts = prepareSlider(spec);

        return new RowFurniture.RowParts(
            spec.title(),
            parts.valueBox(),
            buildReset(parts, spec),
            parts.slider());
    }

    // Putting one slider back where it started, which is the same act in either row shape.
    private static Runnable buildReset(SliderParts parts, SliderSpec spec) {

        return () -> applyValue(parts, spec, spec.value().fallback());
    }

    // The widgets and the wiring, which are the same whichever shape the row is laid out in.
    // Everything that reads or writes the value happens here; only where the name sits differs.
    private static SliderParts prepareSlider(SliderSpec spec) {

        var opened = spec
            .value()
            .applyRemembered();

        var parts = new SliderParts(
            new JSlider(0, SLIDER_STEPS, convertToStep(opened, spec.range())),
            new JTextField(VALUE_BOX_COLUMNS),
            new boolean[1]);

        parts.valueBox().setMaximumSize(new Dimension(VALUE_BOX_WIDTH, VALUE_BOX_HEIGHT));
        parts.valueBox().setText(formatValue(opened));

        bindHandleMovement(parts, spec);
        bindTypedValue(parts, spec);

        return parts;
    }

    /**
     * The three widgets one slider is made of.
     *
     * <p>Together because the handlers below all need all three, and because the flag is only
     * meaningful beside the two it guards: it says the slider is being driven from a typed
     * value, so its own listener must not act on the move as though the handle had been dragged.
     *
     * @param slider    the handle
     * @param valueBox  the typed value beside it
     * @param isSyncing whether the slider is currently being driven rather than read
     */
    private record SliderParts(
        JSlider slider,
        JTextField valueBox,
        boolean[] isSyncing) {
    }

    // Dragging the handle: the box follows and the owner is told on every movement, and the
    // value is written down and the expensive work done once the handle is let go.
    //
    // A move made by a typed value or a reset is left alone here: that path does all of it
    // itself, at the exact value rather than the step it quantises to, and acting on the move
    // as well would settle the slider twice.
    private static void bindHandleMovement(SliderParts parts, SliderSpec spec) {

        parts.slider().addChangeListener(event -> {

            if (parts.isSyncing()[0]) {
                return;
            }

            var value = convertToValue(parts.slider().getValue(), spec.range());

            parts.valueBox().setText(formatValue(value));
            spec.value().apply().accept(value);
            spec.work().onChanged().run();

            if (!parts.slider().getValueIsAdjusting()) {

                spec.value().recordValue(value);
                spec.work().onSettled().run();
            }
        });
    }

    // Typing a value: anything unreadable puts the box back to where the handle stands, so a
    // half-typed number cannot leave the two showing different things.
    private static void bindTypedValue(SliderParts parts, SliderSpec spec) {

        parts.valueBox().addActionListener(event -> {

            var typed = parseValue(parts.valueBox().getText(), spec.range());
            if (Double.isNaN(typed)) {

                parts.valueBox().setText(
                    formatValue(convertToValue(parts.slider().getValue(), spec.range())));

                return;
            }
            applyValue(parts, spec, typed);
        });
    }

    // One value put through everything: the widgets, the owner, the saved value and both halves
    // of the work. Shared by the typed box and the reset button, which are the same act.
    private static void applyValue(SliderParts parts, SliderSpec spec, double value) {

        moveWidgetsTo(parts, spec.range(), value);

        spec.value().recordValue(value);
        spec.work().onChanged().run();
        spec.work().onSettled().run();
    }

    // Set explicitly rather than left to the slider's own listener: the step a value
    // quantises to reads back as a slightly different number, and a box that silently
    // rewrote what was just typed would look broken.
    private static void moveWidgetsTo(SliderParts parts, SliderRange range, double value) {

        parts.isSyncing()[0] = true;
        parts.slider().setValue(convertToStep(value, range));
        parts.isSyncing()[0] = false;
        parts.valueBox().setText(formatValue(value));
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
