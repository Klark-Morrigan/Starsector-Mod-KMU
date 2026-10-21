package kmu.maplayers.base.geometry.ui.settings;

import kmu.desktop.ui.RememberedValue;
import kmu.desktop.ui.SavedValueFormat;
import kmu.desktop.ui.swing.SliderRows;
import kmu.maplayers.base.geometry.settings.ViewerSettings;

import java.util.function.BiConsumer;

/**
 * A numeric setting, edited with a slider.
 *
 * <p>The remembered value is the slider's own reading. Where a setting is held in other units -
 * a share the slider shows as a percentage, a whole count, a reach stepped in hundredths - the
 * conversion is in {@code apply}, so the saved file and the slider never disagree about a number.
 *
 * @param key      what it is remembered under
 * @param title    what the slider is called
 * @param range    where the slider may go
 * @param fallback where it starts, and what a reset puts back
 * @param apply    writes a slider reading into the settings
 */
record SliderKnob(
    String key,
    String title,
    SliderRows.SliderRange range,
    double fallback,
    BiConsumer<ViewerSettings, Double> apply)
        implements ViewerKnob {

    @Override
    public void applySavedTo(ViewerSettings settings) {
        bindTo(settings).applyRemembered();
    }

    /**
     * @param settings what the value is written into
     * @return the value, remembered under this knob's key and told to those settings
     */
    RememberedValue<Double> bindTo(ViewerSettings settings) {

        return new RememberedValue<>(
            key,
            fallback,
            SavedValueFormat.MEASUREMENT,
            value -> apply.accept(settings, value));
    }
}
