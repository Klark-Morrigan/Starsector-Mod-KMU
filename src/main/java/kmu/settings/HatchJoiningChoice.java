package kmu.settings;

import kmlib.settings.LabeledChoice;

/**
 * How the contested-cluster hatch cuts one line's crossings into drawn segments - the player's
 * choice behind the "Hatch joining" Radio setting.
 *
 * <p>A diagnostic axis rather than a taste one: {@link #PER_TRIANGLE} is the emission the hatch
 * shipped with, one segment per triangle a line crosses, and {@link #COALESCED} merges a line's
 * abutting crossings so a stroke is one primitive. The two hatch identical ground, so the choice
 * is only visible in how the strokes rasterise, which is what makes it worth switching between on
 * a live map. This names the options, and the settings reader maps LunaLib's stored Radio label
 * back to one, so the theme resolves a joining without matching raw strings. The labels here must
 * match the {@code secondaryValue} options in data/config/LunaSettings.csv exactly, and both are
 * frozen once shipped - see {@link LabeledChoice} for what a reworded label costs.
 */
public enum HatchJoiningChoice implements LabeledChoice {

    PER_TRIANGLE("Per triangle"),
    COALESCED("Coalesced");

    private final String label;

    HatchJoiningChoice(String label) {
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
}
