package kmu.maplayers.base.geometry.ui.settings;

import kmu.desktop.ui.RememberedValue;
import kmu.desktop.ui.SavedValueFormat;
import kmu.maplayers.base.geometry.settings.ViewerSettings;

import java.awt.Color;
import java.util.function.BiConsumer;

/**
 * A colour setting, edited with one swatch.
 *
 * @param key      what it is remembered under
 * @param title    what the colour is for
 * @param fallback the colour it starts at, and what a reset puts back
 * @param apply    writes the colour into the settings
 */
record ColourKnob(
    String key,
    String title,
    Color fallback,
    BiConsumer<ViewerSettings, Color> apply)
        implements ViewerKnob {

    @Override
    public void applySavedTo(ViewerSettings settings) {
        bindTo(settings).applyRemembered();
    }

    /**
     * @param settings what the colour is written into
     * @return the value, remembered under this knob's key and told to those settings
     */
    RememberedValue<Color> bindTo(ViewerSettings settings) {

        return new RememberedValue<>(
            key,
            fallback,
            SavedValueFormat.COLOUR,
            colour -> apply.accept(settings, colour));
    }
}
