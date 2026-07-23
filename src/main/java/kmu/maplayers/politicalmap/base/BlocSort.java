package kmu.maplayers.politicalmap.base;

import kmu.maplayers.politicalmap.base.refresh.SortSelection;

import java.util.Comparator;

/**
 * The sort the filter picker ranks its bloc list by: the metric and the direction it runs in, paired
 * because the two are always chosen, stored, and read together. Bundling them keeps the picker's
 * builder from carrying the mode and the direction as two loose parameters, and gives the "read the
 * stored sort" resolution one home rather than a copy at each reader.
 *
 * @param mode      the metric the list is ranked by
 * @param direction the direction that metric runs in
 */
public record BlocSort(BlocSortMode mode, SortDirection direction) {

    /**
     * The player's stored sort, read live: the stored mode (or the default when none is stored), then
     * the stored direction resolved against that mode's own default, so a save with no stored direction
     * reads the mode's natural order. The one place the stored mode-and-direction pair is resolved,
     * shared by the picker that ranks under it and the sort selector that acts on it.
     *
     * @return the stored sort
     */
    public static BlocSort resolveStored() {
        var mode = BlocSortMode.fromKeyOrDefault(SortSelection.getSortModeKey());
        var direction = SortDirection.fromKeyOrDefault(
                SortSelection.getSortDirectionKey(), mode.defaultDirection());
        return new BlocSort(mode, direction);
    }

    /**
     * The comparator that ranks blocs under this sort - the mode's comparator run in this direction.
     *
     * @return the bloc comparator for this sort
     */
    public Comparator<SelectableBloc> comparator() {
        return mode.comparator(direction);
    }
}
