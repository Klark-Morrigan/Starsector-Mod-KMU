package kmu.maplayers.politicalmap.base;

/**
 * Whether a cluster's label spells out its owner's full name or its short name - the player's
 * choice behind the sidebar's Short/Full name-format radio.
 *
 * <p>A faction supplies two authored names: a long form (its full title) and a short form (an
 * abbreviation). The political map lets the player pick which one each cluster label carries,
 * since the short form fits a tighter cluster at a larger font. This names the options and, like
 * {@link SortDirection}, owns the save-stable key each persists under, so the stored choice
 * resolves back without matching a display string that is free to change.
 */
public enum FactionNameFormatChoice {
    FULL("full"),
    SHORT("short");

    private final String persistenceKey;

    FactionNameFormatChoice(String persistenceKey) {
        this.persistenceKey = persistenceKey;
    }

    /**
     * Resolves a stored format key back to its choice, falling back to {@code fallback} when the key
     * is absent (a save that never picked a format) or names a format this build no longer knows.
     *
     * @param key      the persisted format key, or null when nothing is stored
     * @param fallback the choice to use when the key is null or unrecognised
     * @return the matching choice, or {@code fallback} when the key is null or unrecognised
     */
    public static FactionNameFormatChoice fromKeyOrDefault(String key,
            FactionNameFormatChoice fallback) {
        for (var choice : values()) {
            if (choice.persistenceKey.equals(key)) {
                return choice;
            }
        }
        return fallback;
    }

    /**
     * @return the save-stable key this choice persists under; frozen once shipped, since renaming it
     *         silently resets every save that stored this choice to the default format
     */
    public String persistenceKey() {
        return persistenceKey;
    }
}
