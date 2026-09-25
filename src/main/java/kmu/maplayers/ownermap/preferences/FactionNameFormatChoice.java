package kmu.maplayers.ownermap.preferences;

import kmlib.persistence.PersistedChoice;

/**
 * How a cluster's label spells out its holder's name - its full name, its short name, or not at all -
 * the player's choice behind the sidebar's Full/Short/No name radio.
 *
 * <p>A faction supplies two authored names: a long form (its full title) and a short form (an
 * abbreviation). The layer lets the player pick which one each cluster label carries, since
 * the short form fits a tighter cluster at a larger font, or turn the names off entirely and read
 * the map by colour alone. Whether names draw is part of this one choice rather than a separate
 * gate, so the single control the player sees maps to a single stored state. This names the options
 * and owns the save-stable key each persists under, so the stored choice resolves back without
 * matching a display string that is free to change.
 */
public enum FactionNameFormatChoice implements PersistedChoice {

    FULL("full", true),
    SHORT("short", true),
    NONE("none", false);

    private final String persistenceKey;
    private final boolean areNamesDrawn;

    FactionNameFormatChoice(String persistenceKey, boolean areNamesDrawn) {
        this.persistenceKey = persistenceKey;
        this.areNamesDrawn = areNamesDrawn;
    }

    /**
     * @return the save-stable key this choice persists under; frozen once shipped, since renaming it
     *         silently resets every save that stored this choice to the default format
     */
    @Override
    public String persistenceKey() {
        return persistenceKey;
    }

    /**
     * The gate the label passes read before they build anything: a choice that draws no name skips
     * the whole label pipeline (the anchor fit as well as the strings), so no work is done for text
     * that would not be drawn.
     *
     * @return whether a cluster label draws a name under this choice - false only for
     *         {@link #NONE}, the two named forms both draw
     */
    public boolean areNamesDrawn() {
        return areNamesDrawn;
    }
}
