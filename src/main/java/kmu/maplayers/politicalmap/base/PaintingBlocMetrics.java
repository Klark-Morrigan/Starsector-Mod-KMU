package kmu.maplayers.politicalmap.base;

/**
 * What a bloc's metrics answer on a layer that paints by them: whether this bloc comes to nought on
 * the very metric its layer paints by. One rule with a per-layer metric - claims on the claims layer,
 * dominance weight on the layers the contest paints - so a bloc listed because it is present and
 * greyed because it paints nothing says the same thing on either.
 *
 * <p>Answered by the metrics rather than by the view or by {@link RankedBloc}, because the answer is
 * the numbers and the fold has already computed them. A ranked bloc holds its payload opaquely, so it
 * has nothing to judge by, and a view answering instead would re-derive the total the metrics carry.
 *
 * <p>Stating it does not decide what a row then looks like: the metrics say the bloc paints nothing,
 * {@link RankedBloc} turns that into the picker's "reads back", and the picker owns how far back that
 * reads. A greyed bloc stays pickable throughout - spotlighting one is the honest answer to "show me
 * what this bloc holds" when the answer is nowhere.
 */
public interface PaintingBlocMetrics extends BlocMetrics {

    /**
     * Whether the bloc these metrics describe paints nothing on this layer - present enough to be
     * listed, with nothing on the map to show for it.
     *
     * @return true when the bloc comes to nought on the metric its layer paints by
     */
    boolean isPaintingNothing();
}
