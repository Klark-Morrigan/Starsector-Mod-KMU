package kmu.maplayers.base.sidebar;

import java.util.Comparator;

/**
 * The sort a sidebar picker ranks its list by: the metric and the direction it runs in, paired
 * because the two are always chosen, stored, and read together. Bundling them keeps a picker's
 * builder from carrying the mode and the direction as two loose parameters, and gives the "read
 * the stored sort" resolution one home rather than a copy at each reader. Which modes exist is
 * the calling layer's declaration ({@link ListSortMode}); this record only pairs and resolves.
 *
 * @param <T>       the list item type the sort's comparator ranks
 * @param mode      the metric the list is ranked by
 * @param direction the direction that metric runs in
 */
public record ListSort<T>(
    ListSortMode<T> mode,
    SortDirection direction) {

    /**
     * The player's stored sort, read live against the caller's own vocabulary: the stored mode key
     * matched among the vocabulary's modes (or its default when nothing is stored or the key names
     * a mode the caller no longer offers), then the stored direction resolved against that mode's
     * own default, so a save with no stored direction reads the mode's natural order.
     *
     * @param <T>       the list item type the modes rank
     * @param sortModes the calling layer's sort vocabulary - the set a stored key resolves
     *                  against, and the mode it falls back to
     * @return the stored sort
     */
    public static <T> ListSort<T> resolveStored(ListSortModes<T> sortModes) {
        var mode = resolveModeOrDefault(sortModes);
        var direction = SortDirection.fromKeyOrDefault(
            SortSelection.getSortDirectionKey(),
            mode.defaultDirection());

        return new ListSort<>(mode, direction);
    }

    /**
     * The comparator that ranks the list under this sort - the mode's comparator run in this
     * direction.
     *
     * @return the item comparator for this sort
     */
    public Comparator<T> comparator() {
        return mode.comparator(direction);
    }

    // The stored mode key matched back to one of the caller's modes, falling back to the caller's
    // default when nothing is stored (a fresh save) or the key names a mode the caller no longer
    // offers (a key left by an older or a modded build), so a picker always resolves to a live
    // mode rather than failing on an unknown key.
    private static <T> ListSortMode<T> resolveModeOrDefault(ListSortModes<T> sortModes) {
        var key = SortSelection.getSortModeKey();
        for (var candidate : sortModes.modes()) {
            if (candidate.persistenceKey().equals(key)) {
                return candidate;
            }
        }
        return sortModes.defaultMode();
    }
}
