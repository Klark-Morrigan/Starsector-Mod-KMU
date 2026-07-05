package kmu.settings;

import kmlib.settings.LabeledChoice;
import kmlib.settings.LabeledChoices;

/**
 * Whether a faction cluster's label spells out the owner's full name or its short
 * name - the player's choice behind the "Faction name format" Radio setting.
 *
 * <p>A faction supplies two authored names: a long form (its full title) and a
 * short form (an abbreviation). The political map lets the player pick which one
 * each cluster label carries, since the short form fits a tighter cluster at a
 * larger font. This names the options and maps LunaLib's stored Radio label back
 * to a choice, so the label builder reads the matching name off the faction
 * without matching raw strings. The labels here must match the
 * {@code secondaryValue} options in data/config/LunaSettings.csv exactly.
 */
public enum FactionNameFormatChoice implements LabeledChoice {
    FULL("Full names"),
    SHORT("Short names");

    private final String label;

    FactionNameFormatChoice(String label) {
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
    public static FactionNameFormatChoice fromLabel(String label,
            FactionNameFormatChoice fallback) {
        return LabeledChoices.fromLabel(values(), label, fallback);
    }
}
