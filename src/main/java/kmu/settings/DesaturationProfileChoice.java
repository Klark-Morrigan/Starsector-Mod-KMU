package kmu.settings;

import kmlib.settings.LabeledChoice;
import kmlib.settings.LabeledChoices;

/**
 * Which colour the alliances view recolours a non-allied faction to when its Desaturate toggle
 * is on - the player's choice behind the "Desaturation profile" Radio.
 *
 * <p>The sidebar toggle only flags <em>whether</em> a bloc desaturates; this names <em>what</em>
 * it desaturates to, so the view stays out of the colour decision and the render pipeline
 * resolves the profile once per pass. {@link #INDEPENDENT} forges the Independent faction's own
 * two shades, so a desaturated bloc reads exactly as independent-held space; {@link #NEUTRAL}
 * uses the flat neutral grey unowned space draws in. The labels here must match the
 * {@code secondaryValue} options in data/config/LunaSettings.csv exactly.
 */
public enum DesaturationProfileChoice implements LabeledChoice {
    INDEPENDENT("Independent"),
    NEUTRAL("Neutral");

    private final String label;

    DesaturationProfileChoice(String label) {
        this.label = label;
    }

    /**
     * @return the LunaLib Radio option label for this choice, used both as a read fallback and
     *         as the CSV default value
     */
    @Override
    public String getLabel() {
        return label;
    }

    /**
     * Maps a stored Radio label back to its choice.
     *
     * @param label    the label LunaLib returned for the field
     * @param fallback the choice to use when {@code label} matches no option (unset, unreadable,
     *                 or a stale label from an old config)
     * @return the matching choice, or {@code fallback} when none matches
     */
    public static DesaturationProfileChoice fromLabel(String label, DesaturationProfileChoice fallback) {
        return LabeledChoices.fromLabel(values(), label, fallback);
    }
}
