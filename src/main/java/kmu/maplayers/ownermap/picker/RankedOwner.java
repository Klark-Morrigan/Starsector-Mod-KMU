package kmu.maplayers.ownermap.picker;

import kmlib.starsector.ui.widgets.lists.SelectableListItem;

/**
 * One row of an owner-map filter picker: an owner the player can spotlight, paired with the metrics
 * the picker ranks and labels it by. The pairing is what keeps a layer's numbers off every other
 * layer's option - a view instantiates this over its own stats type, so a view painted by weighing
 * economies ranks owners by economic metrics and a view painted by another mechanic ranks them by that
 * mechanic's metrics, without either stats record growing a field for the other's benefit.
 *
 * <p>This is the owner map's declaration of the picker's {@link SelectableListItem} seam, and the
 * split is where the seam's three values end: the ID, label, and crest are the {@link SelectableOwner}
 * the row draws and a pick reports, while {@code stats} is the part only the calling view's own
 * {@link kmlib.starsector.ui.widgets.lists.ListSortMode} comparators and trailing values open. The
 * picker itself never sees {@code S} at all.
 *
 * <p>The identity-plus-payload pairing is deliberately a two-field record here rather than a shared
 * generic type: a layer whose rows carry no separable identity implements the seam directly, so there
 * is nothing yet for a common form to serve.
 *
 * <p>The payload is bounded by {@link OwnerMetrics} rather than left open, so a row's second half is
 * some layer's metrics for the owner and not any value at all. The bound asks nothing further of it:
 * the one seam value the identity cannot answer - whether the row reads back - is a fact about the
 * numbers, so it comes from the payload wherever that opted into {@link PaintingOwnerMetrics}, rather
 * than from a flag every layer's option would then have to carry. The other capabilities a payload
 * may state are read by that layer's sort vocabulary rather than by anything here.
 *
 * @param <S>      the ranking metrics this view's picker sorts and labels by - one stats record per
 *                 sort vocabulary, never shared between vocabularies
 * @param identity the owner this row is about: the ID the filter stores plus the label and crest the
 *                 row draws
 * @param stats    the owner's metrics under this view, read only by that view's sort vocabulary
 */
public record RankedOwner<S extends OwnerMetrics>(
    SelectableOwner identity,
    S stats)
        implements SelectableListItem {

    /**
     * The seam's neutral name for the owner's ID, so the picker reports an ID without learning it
     * names an owner. The layer side keeps reading {@link SelectableOwner#ownerId()} through
     * {@link #identity()} - the resolvers and the heal key presence off an owner ID, not off "an item".
     *
     * @return the owner's save-stable ID
     */
    @Override
    public String itemId() {
        return identity.ownerId();
    }

    /**
     * @return the owner's label for its picker row, or null when no name resolved
     */
    @Override
    public String displayName() {
        return identity.displayName();
    }

    /**
     * @return the crest sprite path drawn beside the label, or null when the owner has no crest
     */
    @Override
    public String crestSpritePath() {
        return identity.crestSpritePath();
    }

    /**
     * The one seam value answered by the payload rather than the identity: two owners can share a
     * name and a crest and still differ on this, because what puts a row in that state is what its
     * numbers say.
     *
     * <p>Asked only of a payload that opted into {@link PaintingOwnerMetrics}; any other payload's
     * rows draw at full strength, for the reason stated there.
     *
     * @return true when this owner's row draws receded, as its own metrics judge it
     */
    @Override
    public boolean isDimmed() {

        return stats instanceof PaintingOwnerMetrics paintingMetrics
            && paintingMetrics.isPaintingNothing();
    }
}
