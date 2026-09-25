package kmu.maplayers.ownermap.picker;

import kmlib.starsector.ui.widgets.lists.ListPicker;
import kmlib.starsector.ui.widgets.lists.SelectableListItem;

/**
 * What one resolve of a view's spotlight picker yields: the rows the sidebar draws, and the systems
 * behind them. The drawn half of
 * {@link kmu.maplayers.ownermap.owners.BlocStatsRead}, which is where the reason the pair
 * travels together is stated.
 *
 * <p>It carries the index rather than the picker doing so, because a picker is a general widget
 * model that ranks and draws items; where a bloc lives is this map's own knowledge and no part of
 * what a row is drawn from.
 *
 * <p>The index is the whole walk's, not the listed rows'. A view's gate decides which present blocs
 * it offers, and trimming the index to match would cost a filtering pass to remove entries no lookup
 * can ask for - only a listed bloc is ever hovered.
 *
 * @param <T>           the calling view's own row type, ranked by the vocabulary its picker carries
 * @param picker        the blocs this view offers and the vocabulary that ranks them
 * @param presenceIndex the systems each bloc the walk surfaced was found in
 */
public record BlocPickerRead<T extends SelectableListItem>(
    ListPicker<T> picker,
    BlocPresenceIndex presenceIndex) {

    /**
     * The offers-nothing case as a value rather than a null, mirroring {@link ListPicker#empty()}: a
     * view with no spotlight answers with a read like any other, and no caller above it tests for
     * absence before reaching either half.
     *
     * @param <T> the row type the caller's empty read stands in for; unconstrained, since an empty
     *            read holds nothing of that type
     * @return a read offering no rows and no presence
     */
    public static <T extends SelectableListItem> BlocPickerRead<T> empty() {
        return new BlocPickerRead<>(ListPicker.empty(), BlocPresenceIndex.EMPTY);
    }
}
