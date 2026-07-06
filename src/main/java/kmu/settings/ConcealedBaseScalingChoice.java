package kmu.settings;

import kmlib.settings.LabeledChoice;
import kmlib.settings.LabeledChoices;

/**
 * How a concealed base's size counts toward dominance - the player's choice behind
 * the "Concealed base scaling" Radio setting.
 *
 * <p>A concealed base (a vanilla hidden market) marks its system on the political
 * map, but a player may want it to weigh in by its real colony size or to fold in
 * at a flat token weight so a large secret base never outranks the open colonies
 * around it. {@link #NORMAL} counts the base by its own size like any colony;
 * {@link #FIXED} counts it at the player-set fixed weight regardless of size. This
 * names the options and maps LunaLib's stored Radio label back to a choice, so the
 * weighting reads the mode without matching raw strings. The labels here must match
 * the {@code secondaryValue} options in data/config/LunaSettings.csv exactly.
 */
public enum ConcealedBaseScalingChoice implements LabeledChoice {
    NORMAL("Normal"),
    FIXED("Fixed");

    private final String label;

    ConcealedBaseScalingChoice(String label) {
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
    public static ConcealedBaseScalingChoice fromLabel(String label,
            ConcealedBaseScalingChoice fallback) {
        return LabeledChoices.fromLabel(values(), label, fallback);
    }
}
