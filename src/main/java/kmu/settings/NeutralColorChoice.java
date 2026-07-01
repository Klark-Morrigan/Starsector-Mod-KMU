package kmu.settings;

/**
 * Whether a factionless political-map category (decivilised or uninhabited
 * systems) draws its outline in the neutral color or not at all - the player's
 * choice behind the "Border color" Radio for those sections.
 *
 * <p>These systems have no owning faction, so there is no palette to pick from:
 * the only choice is the shared neutral color or {@link #NONE} to hide them. This
 * names the two options and maps LunaLib's stored Radio label back to a choice,
 * so the render layer decides whether to draw the outline without matching raw
 * strings. The labels here must match the {@code secondaryValue} options in
 * data/config/LunaSettings.csv exactly.
 */
public enum NeutralColorChoice {
    NEUTRAL("Neutral color"),
    NONE("No color");

    private final String label;

    NeutralColorChoice(String label) {
        this.label = label;
    }

    /**
     * @return the LunaLib Radio option label for this choice, used both as a
     *         read fallback and as the CSV default value
     */
    public String getLabel() {
        return label;
    }

    /**
     * @return true when the outline draws (any choice but {@link #NONE})
     */
    public boolean isDrawn() {
        return this != NONE;
    }

    /**
     * Maps a stored Radio label back to its choice.
     *
     * @param label    the label LunaLib returned for the field
     * @param fallback the choice to use when {@code label} matches no option
     *                 (unset, unreadable, or a stale label from an old config)
     * @return the matching choice, or {@code fallback} when none matches
     */
    public static NeutralColorChoice fromLabel(String label, NeutralColorChoice fallback) {
        for (var choice : values()) {
            if (choice.label.equals(label)) {
                return choice;
            }
        }
        return fallback;
    }
}
