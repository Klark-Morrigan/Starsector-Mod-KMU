package kmu.maplayers.ownermap.picker;

import kmlib.starsector.ui.widgets.lists.ListPicker;
import kmlib.starsector.ui.widgets.lists.SelectableListItem;

/**
 * What one resolve of a view's spotlight picker yields: the rows the sidebar draws, and the systems
 * behind them. The drawn half of
 * {@link kmu.maplayers.ownermap.owners.OwnerStatsRead}, which is where the reason the pair
 * travels together is stated.
 *
 * <p>It carries the index rather than the picker doing so, because a picker is a general widget
 * model that ranks and draws items; where an owner lives is this map's own knowledge and no part of
 * what a row is drawn from.
 *
 * <p>The index is the whole walk's, not the listed rows'. A view's gate decides which present owners
 * it offers, and trimming the index to match would cost a filtering pass to remove entries no lookup
 * can ask for - only a listed owner is ever hovered.
 *
 * @param <T>           the calling view's own row type, ranked by the vocabulary its picker carries
 * @param picker        the owners this view offers and the vocabulary that ranks them
 * @param presenceIndex the systems each owner the walk surfaced was found in
 */
public record OwnerPickerRead<T extends SelectableListItem>(
    ListPicker<T> picker,
    OwnerPresenceIndex presenceIndex) {

    /**
     * The offers-nothing case as a value rather than a null, mirroring {@link ListPicker#empty()}: a
     * view with no spotlight answers with a read like any other, and no caller above it tests for
     * absence before reaching either half.
     *
     * @param <T> the row type the caller's empty read stands in for; unconstrained, since an empty
     *            read holds nothing of that type
     * @return a read offering no rows and no presence
     */
    public static <T extends SelectableListItem> OwnerPickerRead<T> empty() {
        return new OwnerPickerRead<>(ListPicker.empty(), OwnerPresenceIndex.EMPTY);
    }
}
