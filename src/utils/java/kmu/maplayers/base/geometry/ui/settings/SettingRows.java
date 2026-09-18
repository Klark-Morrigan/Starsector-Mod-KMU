package kmu.maplayers.base.geometry.ui.settings;

import kmu.desktop.ui.swing.ControlRows;
import kmu.desktop.ui.swing.SliderRows;
import kmu.maplayers.base.geometry.settings.ViewerSettings;

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

import javax.swing.BoxLayout;
import javax.swing.JPanel;

/**
 * The row shapes every section here builds, bound once to the redraws they cost.
 *
 * <p>Shared because the binding is the point. A geometry knob rebuilds and a colour repaints,
 * and a knob added straight onto a row builder can be given either - so the sections do not
 * get to choose: a slider from here rebuilds, and that is what makes it a geometry knob rather
 * than something that merely looks like one.
 */
final class SettingRows {

    // Alpha runs the full byte, so a fill can be turned off entirely or made solid without
    // touching the colour it was chosen as.
    private static final double OPACITY_MINIMUM = 0;

    private static final double OPACITY_MAXIMUM = 255;

    private final ViewerRefreshes refreshes;

    SettingRows(ViewerRefreshes refreshes) {
        this.refreshes = refreshes;
    }

    // A column for one section to hold, filled by whichever run of rows it is the section for.
    // The rows add themselves to whatever they are handed, so a section body is only the
    // container plus the layout the panel itself uses.
    static JPanel buildSectionBody(Consumer<JPanel> addRows) {

        var body = new JPanel();

        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        addRows.accept(body);

        return body;
    }

    // Every geometry knob rebuilds; that is what makes it a geometry knob rather than a
    // colour. Bound once here so a new one cannot be added that quietly only repaints.
    //
    // Remembered under a key of its own rather than under its label, as every control here is:
    // a label is copy, and a knob keyed by one loses whatever was set the moment it is reworded.
    JPanel buildSlider(
            String key,
            String title,
            double minimum,
            double maximum,
            double initial,
            DoubleConsumer apply) {

        return SliderRows.buildSlider(
            buildSliderSpec(key, title, minimum, maximum, initial, apply));
    }

    // One slider's four parts, under the same refresh every geometry knob takes. Named rather
    // than a twelve-argument pairing method: two of a pair's arguments are multi-line lambdas,
    // and in a positional list of twelve nothing would catch a left and a right transposed.
    SliderRows.SliderSpec buildSliderSpec(
            String key,
            String title,
            double minimum,
            double maximum,
            double initial,
            DoubleConsumer apply) {

        return new SliderRows.SliderSpec(
            key,
            title,
            new SliderRows.SliderRange(minimum, maximum, initial),
            new SliderRows.SliderWork(apply, refreshes::rebuildGeometry, () -> { }));
    }

    JPanel buildOpacitySlider(String key, String title, DoubleConsumer apply) {
        return buildSlider(
            key,
            title,
            OPACITY_MINIMUM,
            OPACITY_MAXIMUM,
            ViewerSettings.OWNER_FILL_ALPHA,
            apply);
    }

    JPanel buildToggle(
            String key, String title, boolean initial, Consumer<Boolean> apply) {

        return ControlRows.buildToggle(
            key, title, initial, apply, refreshes::rebuildGeometry);
    }
}
