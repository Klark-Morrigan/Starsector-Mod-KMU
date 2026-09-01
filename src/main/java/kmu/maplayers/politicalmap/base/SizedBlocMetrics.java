package kmu.maplayers.politicalmap.base;

/**
 * What a bloc's metrics answer on a layer whose fold measured how much of the sector the bloc lives
 * on: its summed raw colony size, across every colony anywhere, whether or not the economy lists it.
 * One number with one meaning, so the size ranking a player picks under one view is the same reading
 * of "size" under every other.
 *
 * <p>Its own interface rather than a field on {@link BlocMetrics}, for the reason
 * {@link PaintingBlocMetrics} is one too: not every fold computes a whole-sector size, and a default
 * would hand a payload that never summed one an inherited answer of nought - which ranks as the
 * smallest bloc in the sector rather than as a number nobody measured. Opting in is what leaves a
 * vocabulary whose record does not carry the size unable to be offered a ranking over it at all.
 *
 * <p>Because the number is shared, so is the mode that ranks by it: {@link SharedBlocSortModes}
 * declares it once over this capability rather than once per vocabulary. What stays per-vocabulary is
 * the chain ties break down behind it, which is that layer's own numbers in that layer's own order.
 */
public interface SizedBlocMetrics extends BlocMetrics {

    /**
     * The bloc's summed raw colony size across every colony it lives on anywhere in the sector -
     * whether or not the economy lists it, and not only in the systems its layer paints it for.
     *
     * @return the bloc's whole-sector colony size
     */
    int marketSize();
}
