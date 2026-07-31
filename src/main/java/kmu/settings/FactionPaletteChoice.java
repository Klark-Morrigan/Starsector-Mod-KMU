package kmu.settings;

import kmlib.settings.LabeledChoice;

import kmu.maplayers.base.theme.ElementPaint;

/**
 * Which of a faction's two palette colors a political-map element draws in, or
 * whether it draws at all - the player's choice behind the "Faction ... color"
 * and "Independent ... color" Radio settings.
 *
 * <p>A faction supplies two authored UI shades; the political map lets the player
 * point each element (fill, outer border, inner seam) at either, or turn it off
 * with {@link #NONE}. This names the options, and the settings reader maps LunaLib's
 * stored Radio label back to one, so the render layer picks {@code owner.primaryColor()},
 * {@code owner.secondaryColor()}, or no draw without matching raw strings. The
 * labels here must match the {@code secondaryValue} options in
 * data/config/LunaSettings.csv exactly.
 */
public enum FactionPaletteChoice implements LabeledChoice, ElementPaint {
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

    /**
     * This choice as the theme tier carries it, with {@link #NONE} flattened to null.
     *
     * <p>The tier reads a missing selection as "this element paints nothing", which is the one
     * thing it decides for itself; expressing the off state as absence rather than as a named
     * constant is what keeps it from having to know this enum has three options rather than two
     * or five. Every style built from a player-authored choice goes through here, so the off
     * state cannot reach the tier still wearing a value that reads as drawable.
     *
     * @return this choice, or null when it is {@link #NONE}
     */
    public ElementPaint resolveElementPaint() {
        return this == NONE ? null : this;
    }
}
