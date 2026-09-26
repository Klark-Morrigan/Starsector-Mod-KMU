package kmu.settings;

import kmlib.settings.LabeledChoice;

/**
 * How a hidden market's size counts toward dominance - the player's choice behind
 * the "Hidden market scaling" Radio setting.
 *
 * <p>A hidden market (a vanilla secret colony) marks its system on the political
 * map, but a player may want it to weigh in by its real colony size or to fold in
 * at a flat token weight so a large secret base never outranks the open colonies
 * around it. {@link #NORMAL} counts the market by its own size like any colony;
 * {@link #FIXED} counts it at the player-set fixed weight regardless of size. This
 * names the options, and the settings reader maps LunaLib's stored Radio label back to
 * one, so the weighting reads the mode without matching raw strings. Its labels are the Radio
 * row's options, spelt identically - see {@link LabeledChoice} for why they never change.
 */
public enum HiddenMarketScalingChoice implements LabeledChoice {

    NORMAL("Normal"),
    FIXED("Fixed");

    private final String label;

    HiddenMarketScalingChoice(String label) {
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
