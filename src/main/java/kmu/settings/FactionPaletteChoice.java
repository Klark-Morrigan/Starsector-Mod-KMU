package kmu.settings;

import kmlib.settings.LabeledChoice;

/**
 * Which of a faction's two palette colours a political-map element draws in, or
 * whether it draws at all - the player's choice behind the "Faction ... colour"
 * and "Independent ... colour" Radio settings.
 *
 * <p>A faction supplies two authored UI shades; the political map lets the player
 * point each element (fill, outer border, inner seam) at either, or turn it off
 * with {@link #NONE}. This names the options, and the settings reader maps LunaLib's
 * stored Radio label back to one, so the render layer picks {@code owner.primaryColour()},
 * {@code owner.secondaryColour()}, or no draw without matching raw strings. Its
 * labels are the Radio row's options, spelt identically - see {@link LabeledChoice}
 * for why they never change.
 */
public enum FactionPaletteChoice implements LabeledChoice {

    PRIMARY("Primary faction color"),
    SECONDARY("Secondary faction color"),
    NONE("No color");

    private final String label;

    FactionPaletteChoice(String label) {
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
