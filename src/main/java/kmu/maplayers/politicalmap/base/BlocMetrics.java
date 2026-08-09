package kmu.maplayers.politicalmap.base;

/**
 * What every political-map stats record can be asked about the bloc it describes, beyond the numbers
 * only its own layer's sort vocabulary reads. One question so far: whether the bloc's picker row
 * reads back from the rest of the list.
 *
 * <p>It sits on the metrics rather than on {@link RankedBloc} because that is where the answer comes
 * from. A row reads back when the bloc has nothing to show under the metric its layer is about,
 * which is a fact about the numbers - and the numbers are exactly the half a ranked bloc holds
 * opaquely, so it cannot answer for them. Widening the pairing with a stated flag instead would put
 * a component on every layer's option for one layer's benefit, which is the pollution the split
 * between identity and metrics exists to prevent.
 *
 * <p>The default is "reads at full strength", so a layer whose blocs are all equally worth
 * spotlighting implements this and states nothing further.
 */
public interface BlocMetrics {

    /**
     * Whether this bloc's picker row draws receded - the state of a bloc worth listing that has
     * nothing to show under the metric its layer paints by. What receding looks like is the picker's
     * decision, not this one's: a stats record says only that the bloc is in that state.
     *
     * @return true when the bloc's row draws receded; false for the ordinary full-strength row
     */
    default boolean isDimmed() {
        return false;
    }
}
