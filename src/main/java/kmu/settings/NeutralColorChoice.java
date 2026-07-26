package kmu.settings;

import kmlib.settings.LabeledChoice;

/**
 * Whether a factionless political-map category (decivilised or uninhabited
 * systems) draws its outline in the neutral color or not at all - the player's
 * choice behind the "Border color" Radio for those sections.
 *
 * <p>These systems have no owning faction, so there is no palette to pick from:
 * the only choice is the shared neutral color or {@link #NONE} to hide them. This
 * names the two options, and the settings reader maps LunaLib's stored Radio label
 * back to one, so the render layer decides whether to draw the outline without
 * matching raw strings. The labels here must match the {@code secondaryValue} options in
 * data/config/LunaSettings.csv exactly.
 */
public enum NeutralColorChoice implements LabeledChoice {
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
    @Override
    public String getLabel() {
        return label;
    }

    /**
     * @return true when the outline draws (any choice but {@link #NONE})
     */
    public boolean isDrawn() {
        return this != NONE;
    }

}
