package kmu.settings;

import kmlib.settings.LabeledChoice;

/**
 * What a presence band treats a cluster name as occupying - the player's choice behind the
 * "What a band keeps clear of" Radio setting.
 *
 * <p>A name is placed by fitting a box against a chord the search swept, and that chord is accepted
 * as soon as it is at least as long as the widest wrapped line - so the fitted box overhangs the
 * drawn words at both ends by however much the chord beat the text. {@link #FITTED_BOX} keeps clear
 * of that box, which is the room the placement reserved; {@link #WORDS} keeps clear of each drawn
 * line's own measured extent, which is the room the reader can actually see a name taking. The
 * difference is ring a band either spends or keeps, and it is largest exactly where it hurts most -
 * a short name on a roomy cluster.
 *
 * <p>This names the options, and the settings reader maps LunaLib's stored Radio label back to one,
 * so the band pass reads the mode without matching raw strings. The labels here must match the
 * {@code secondaryValue} options in data/config/LunaSettings.csv exactly, and both are frozen once
 * shipped - see {@link LabeledChoice} for what a reworded label costs.
 */
public enum RibbonNameClearanceChoice implements LabeledChoice {

    FITTED_BOX("The name's fitted box"),
    WORDS("The words themselves");

    private final String label;

    RibbonNameClearanceChoice(String label) {
        this.label = label;
    }

    /**
     * @return the LunaLib Radio option label for this choice, used both as a read fallback and as
     *         the CSV default value
     */
    @Override
    public String getLabel() {
        return label;
    }
}
