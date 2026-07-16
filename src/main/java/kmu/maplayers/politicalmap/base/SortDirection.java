package kmu.maplayers.politicalmap.base;

/**
 * The direction the filter picker's active sort runs in - ascending or descending on the chosen
 * metric. It rides alongside the {@link BlocSortMode}: a mode fixes which key ranks the blocs, this
 * fixes which way that key runs. Split out from the mode because the two persist and change
 * independently - picking a new mode resets the direction to that mode's default, while re-picking
 * the lit mode flips only the direction.
 *
 * <p>Each direction owns the save-stable key it persists under; the sort selector maps it to the
 * up/down triangle it draws in a row's trailing slot, since the body font renders no up/down glyph.
 * {@link #opposite()} is the flip a re-pick applies.
 */
public enum SortDirection {
    ASCENDING("asc"),
    DESCENDING("desc");

    private final String persistenceKey;

    SortDirection(String persistenceKey) {
        this.persistenceKey = persistenceKey;
    }

    /**
     * Resolves a stored direction key back to its direction, falling back to {@code fallback} when the
     * key is absent (a save that predates the sort direction, or one that never flipped) or names a
     * direction this build no longer knows. The fallback is the active mode's default direction, so an
     * unresolved key reads as "this mode's natural order" rather than failing.
     *
     * @param key      the persisted direction key, or null when nothing is stored
     * @param fallback the direction to use when the key is null or unrecognised
     * @return the matching direction, or {@code fallback} when the key is null or unrecognised
     */
    public static SortDirection fromKeyOrDefault(String key, SortDirection fallback) {
        for (var direction : values()) {
            if (direction.persistenceKey.equals(key)) {
                return direction;
            }
        }
        return fallback;
    }

    /**
     * @return the save-stable key this direction persists under; frozen once shipped, since renaming
     *         it silently resets every save that stored this direction to its mode's default
     */
    public String persistenceKey() {
        return persistenceKey;
    }

    /** @return the other direction - the flip a re-pick of the lit sort mode applies */
    public SortDirection opposite() {
        return this == ASCENDING ? DESCENDING : ASCENDING;
    }
}
