package kmu.maplayers.politicalmap.base;

import kmlib.starsector.ui.widgets.lists.SelectableListItem;

/**
 * One row of a political-map filter picker: a bloc the player can spotlight, paired with the metrics
 * the picker ranks and labels it by. The pairing is what keeps a layer's numbers off every other
 * layer's option - a view instantiates this over its own stats type, so the held views rank blocs by
 * domination metrics and a claim-painted view ranks them by claim metrics without either stats record
 * growing a field for the other's benefit.
 *
 * <p>This is the political map's declaration of the picker's {@link SelectableListItem} seam, and the
 * split is where the seam's three values end: the id, label, and crest are the {@link SelectableBloc}
 * the row draws and a pick reports, while {@code stats} is the part only the calling view's own
 * {@link kmlib.starsector.ui.widgets.lists.ListSortMode} comparators and trailing values open. The
 * picker itself never sees {@code S} at all.
 *
 * <p>The identity-plus-payload pairing is deliberately a two-field record here rather than a shared
 * generic type: a layer whose rows carry no separable identity implements the seam directly, so there
 * is nothing yet for a common form to serve.
 *
 * @param <S>      the ranking metrics this view's picker sorts and labels by - one stats record per
 *                 sort vocabulary, never shared between vocabularies
 * @param identity the bloc this row is about: the id the filter stores plus the label and crest the
 *                 row draws
 * @param stats    the bloc's metrics under this view, read only by that view's sort vocabulary
 */
public record RankedBloc<S>(
    SelectableBloc identity,
    S stats) implements SelectableListItem {

    /**
     * The seam's neutral name for the bloc's id, so the picker reports an id without learning it
     * names a bloc. The political side keeps reading {@link SelectableBloc#blocId()} through
     * {@link #identity()} - the resolvers and the heal key presence off a bloc id, not off "an item".
     *
     * @return the bloc's save-stable id
     */
    @Override
    public String itemId() {
        return identity.blocId();
    }

    /**
     * @return the bloc's label for its picker row, or null when no name resolved
     */
    @Override
    public String displayName() {
        return identity.displayName();
    }

    /**
     * @return the crest sprite path drawn beside the label, or null when the bloc's crest faction has
     *         no authored crest
     */
    @Override
    public String crestSpritePath() {
        return identity.crestSpritePath();
    }
}
