package kmu.maplayers.base.geometry.ui.settings;

import kmu.desktop.ui.RememberedValue;
import kmu.desktop.ui.SavedValueFormat;
import kmu.desktop.ui.swing.ToggleTree;
import kmu.maplayers.base.geometry.settings.ViewerSettings;

import java.util.function.BiConsumer;

/**
 * An on-or-off setting, edited with a checkbox - on a row of its own, in a toggle tree, or as a
 * section's master switch.
 *
 * @param key      what it is remembered under
 * @param title    what the checkbox is called
 * @param fallback what it starts as, and what a reset puts back
 * @param apply    writes the state into the settings
 */
record SwitchKnob(
    String key,
    String title,
    boolean fallback,
    BiConsumer<ViewerSettings, Boolean> apply)
        implements ViewerKnob {

    @Override
    public void applySavedTo(ViewerSettings settings) {
        bindTo(settings).applyRemembered();
    }

    /**
     * @param settings what the state is written into
     * @return the value, remembered under this knob's key and told to those settings
     */
    RememberedValue<Boolean> bindTo(ViewerSettings settings) {

        return new RememberedValue<>(
            key,
            fallback,
            SavedValueFormat.FLAG,
            isOn -> apply.accept(settings, isOn));
    }

    /**
     * @param settings what the state is written into
     * @return the switch a toggle tree row is built from
     */
    ToggleTree.Switch bindSwitch(ViewerSettings settings) {
        return new ToggleTree.Switch(title, bindTo(settings));
    }
}
