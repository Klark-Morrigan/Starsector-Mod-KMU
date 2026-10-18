package kmu.maplayers.ownermap.picker;

/**
 * What an owner's metrics answer on a layer that paints by them: whether this owner comes to nought on
 * the very metric its layer paints by. One rule with a per-layer metric - weight on a layer that
 * weighs economies, whatever another mechanic counts on its own layer - so an owner listed because it is
 * present and greyed because it paints nothing says the same thing on every layer.
 *
 * <p>Answered by the metrics rather than by the view or by {@link RankedOwner}, because the answer is
 * the numbers and the fold has already computed them. A ranked owner holds its payload opaquely, so it
 * has nothing to judge by, and a view answering instead would re-derive the total the metrics carry.
 *
 * <p>What opting into it rather than inheriting it buys here: not every picker lists painters. One
 * choosing what the map is measured <em>against</em> has no owner that paints nothing, so it declares
 * nothing and cannot be asked a question it has no answer to.
 *
 * <p>Stating it does not decide what a row then looks like: the metrics say the owner paints nothing,
 * {@link RankedOwner} turns that into the picker's "reads back", and the picker owns how far back that
 * reads. A greyed owner stays pickable throughout - spotlighting one is the honest answer to "show me
 * what this owner holds" when the answer is nowhere.
 */
public interface PaintingOwnerMetrics
        extends OwnerMetrics {

    /**
     * Whether the owner these metrics describe comes to nought on the metric its layer paints by -
     * present enough to be listed, with nothing on the map to show for it.
     *
     * @return true when the owner paints nothing here
     */
    boolean isPaintingNothing();
}
