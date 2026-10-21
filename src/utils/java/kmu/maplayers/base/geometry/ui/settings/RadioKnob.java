package kmu.maplayers.base.geometry.ui.settings;

import kmu.desktop.ui.RememberedValue;
import kmu.desktop.ui.swing.ControlRows;
import kmu.maplayers.base.geometry.settings.ViewerSettings;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * A setting picked from a few options, edited with a row of radio buttons.
 *
 * <p>Its fallback is named rather than taken from the first option, so two radios offering the
 * same options in the same order can still open on different ones.
 *
 * @param key      what it is remembered under
 * @param title    what the choice is called
 * @param picks    the options, in the order they are shown
 * @param fallback the option it starts on, and what a reset puts back
 * @param apply    writes the pick into the settings
 * @param <T>      what is picked between
 */
record RadioKnob<T>(
    String key,
    String title,
    List<ControlRows.Pick<T>> picks,
    T fallback,
    BiConsumer<ViewerSettings, T> apply)
        implements ViewerKnob {

    @Override
    public void applySavedTo(ViewerSettings settings) {
        bindTo(settings).applyRemembered();
    }

    /**
     * @param settings what the pick is written into
     * @return the value, remembered under this knob's key and told to those settings
     */
    RememberedValue<T> bindTo(ViewerSettings settings) {

        return ControlRows.createRememberedPick(
            key,
            picks,
            fallback,
            value -> apply.accept(settings, value));
    }
}
