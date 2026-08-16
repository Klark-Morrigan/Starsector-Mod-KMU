package kmu.settings;

import kmlib.settings.LabeledChoice;

/**
 * Which side of the map's own nebulae one of the political overlay's sub-layers paints on - the
 * player's choice behind the "Nebula depth" Radio settings.
 *
 * <p>The map draws its nebulae between two of its terrain passes, so an overlay sub-layer is
 * either painted before them or after them, and there is no third answer to give. {@link #BELOW}
 * paints first and is dimmed by whatever the map lays over it - under the Starscape look that is
 * one large blended sprite across the whole sector - while {@link #ABOVE} paints last and reads
 * at full strength. Which of the two a sub-layer looks better on turns on the player's own
 * palette and how loudly they run the overlay, so it is a taste call the map cannot make for
 * them; under the schematic look, where the nebulae draw as ordinary terrain, the same choice
 * only reorders the overlay against itself.
 *
 * <p>This names the two options, and the settings reader maps LunaLib's stored Radio label back
 * to one, so the render layer resolves a depth without matching raw strings. The labels here must
 * match the {@code secondaryValue} options in data/config/LunaSettings.csv exactly, and both are
 * frozen once shipped - see {@link LabeledChoice} for what a reworded label costs.
 */
public enum NebulaDepthChoice implements LabeledChoice {

    BELOW("Below"),
    ABOVE("Above");

    private final String label;

    NebulaDepthChoice(String label) {
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
