package kmu.maplayers.base.geometry.ui.settings;

import kmu.desktop.ui.RememberedValue;
import kmu.desktop.ui.SavedValueFormat;
import kmu.maplayers.base.geometry.settings.ViewerSettings;

import java.awt.Color;
import java.util.function.BiConsumer;

/**
 * A fill colour and its outline colour, edited side by side and starting at the same colour.
 *
 * <p>Each half is remembered under the stem with its own suffix. Derived here, in one place,
 * because those are the keys saved windows already hold.
 *
 * @param keyStem   what both halves' keys are built from
 * @param title     what the pair is for
 * @param fallback  the colour both start at, and what a reset puts back
 * @param applyFill writes the fill colour into the settings
 * @param applyEdge writes the outline colour into the settings
 */
record ColourPairKnob(
    String keyStem,
    String title,
    Color fallback,
    BiConsumer<ViewerSettings, Color> applyFill,
    BiConsumer<ViewerSettings, Color> applyEdge)
        implements ViewerKnob {

    private static final String FILL_KEY_SUFFIX = " fill";
    private static final String EDGE_KEY_SUFFIX = " outline";

    @Override
    public void applySavedTo(ViewerSettings settings) {

        bindFill(settings).applyRemembered();
        bindEdge(settings).applyRemembered();
    }

    /**
     * @param settings what the colour is written into
     * @return the fill half, remembered and told to those settings
     */
    RememberedValue<Color> bindFill(ViewerSettings settings) {

        return new RememberedValue<>(
            keyStem + FILL_KEY_SUFFIX,
            fallback,
            SavedValueFormat.COLOUR,
            colour -> applyFill.accept(settings, colour));
    }

    /**
     * @param settings what the colour is written into
     * @return the outline half, remembered and told to those settings
     */
    RememberedValue<Color> bindEdge(ViewerSettings settings) {

        return new RememberedValue<>(
            keyStem + EDGE_KEY_SUFFIX,
            fallback,
            SavedValueFormat.COLOUR,
            colour -> applyEdge.accept(settings, colour));
    }
}
