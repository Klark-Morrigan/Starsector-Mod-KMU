package kmu.settings;

import kmlib.settings.LabeledChoice;
import kmlib.settings.LabeledChoices;

/**
 * Which colour the overlay sidebar's collapse-handle chevron draws in - the player's choice behind
 * the "Collapse notch chevron color" Radio.
 *
 * <p>The handle is the one control that sits outside the panel frame, over the map, so how loudly its
 * direction cue reads is a taste call: {@link #GOLD} pitches it against the frame in the vanilla
 * highlight, while {@link #PANEL_ACCENT} keeps it in the panel's own player-faction accents, brightening
 * under the pointer, so the handle reads as part of the chrome. This names the two options and maps
 * LunaLib's stored Radio label back to a choice, so the render layer resolves shades without matching
 * raw strings. The labels here must match the {@code secondaryValue} options in
 * data/config/LunaSettings.csv exactly.
 */
public enum NotchChevronColorChoice implements LabeledChoice {
    GOLD("Gold"),
    PANEL_ACCENT("Panel accent");

    private final String label;

    NotchChevronColorChoice(String label) {
        this.label = label;
    }

    /**
     * @return the LunaLib Radio option label for this choice, used both as a
     *         read fallback and as the CSV default value
     */
    @Override
    public String getLabel() {
        return label;
    }

    /**
     * Maps a stored Radio label back to its choice.
     *
     * @param label    the label LunaLib returned for the field
     * @param fallback the choice to use when {@code label} matches no option
     *                 (unset, unreadable, or a stale label from an old config)
     * @return the matching choice, or {@code fallback} when none matches
     */
    public static NotchChevronColorChoice fromLabel(String label, NotchChevronColorChoice fallback) {
        return LabeledChoices.fromLabel(values(), label, fallback);
    }
}
