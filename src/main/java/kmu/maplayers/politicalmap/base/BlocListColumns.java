package kmu.maplayers.politicalmap.base;

import kmu.util.KmuStrings;

/**
 * How many columns the filter picker's bloc list wraps its rows across - one tall stack or two side by
 * side. Like {@link BlocSortMode} each choice owns the save-stable key its pick persists under, the
 * label its selector segment draws, and the column count it feeds the list widget's row geometry, so
 * the selector, the persistence, and the layout all read one source rather than re-deriving the count.
 *
 * <p>{@link #DEFAULT} is the single column, the layout a fresh save and any unrecognised stored key
 * fall back to, so the list always has a live column count even before the player picks one. The
 * values are declared in the order the selector lays its segments out left to right, so the segment a
 * click reports maps straight back to a choice by position.
 */
public enum BlocListColumns {
    ONE("1", KmuStrings.POLITICAL_MAP_CTL_COLUMNS_ONE, 1),
    TWO("2", KmuStrings.POLITICAL_MAP_CTL_COLUMNS_TWO, 2);

    /** The layout a fresh save and any unrecognised stored key fall back to, so a count always exists. */
    public static final BlocListColumns DEFAULT = ONE;

    private final String persistenceKey;
    private final String labelKey;
    private final int columnCount;

    BlocListColumns(String persistenceKey, String labelKey, int columnCount) {
        this.persistenceKey = persistenceKey;
        this.labelKey = labelKey;
        this.columnCount = columnCount;
    }

    /**
     * Resolves a stored column-count key back to its choice, falling back to {@link #DEFAULT} when the
     * key is absent (a save that never picked a count) or names a choice this build no longer offers (a
     * key left by an older or a modded build), so the list always resolves to a live count rather than
     * failing on an unknown key.
     *
     * @param key the persisted column-count key, or null when nothing is stored
     * @return the matching choice, or {@link #DEFAULT} when the key is null or unrecognised
     */
    public static BlocListColumns fromKeyOrDefault(String key) {
        for (var choice : values()) {
            if (choice.persistenceKey.equals(key)) {
                return choice;
            }
        }
        return DEFAULT;
    }

    /**
     * @return the save-stable key this choice persists under; frozen once shipped, since renaming it
     *         silently resets every save that stored this count back to {@link #DEFAULT}
     */
    public String persistenceKey() {
        return persistenceKey;
    }

    /** @return the string key of this choice's selector-segment label */
    public String labelKey() {
        return labelKey;
    }

    /** @return how many columns the bloc list wraps its rows across under this choice */
    public int columnCount() {
        return columnCount;
    }
}
