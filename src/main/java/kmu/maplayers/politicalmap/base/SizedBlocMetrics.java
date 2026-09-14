package kmu.maplayers.politicalmap.base;

/**
 * What a bloc's metrics answer on a layer whose fold measured how much of the sector the bloc lives
 * on: its summed raw colony size, across every colony anywhere, whether or not the economy lists it.
 * One number with one meaning, so the size ranking a player picks under one view is the same reading
 * of "size" under every other.
 *
 * <p>What opting into it rather than inheriting it buys here: a vocabulary whose fold never summed a
 * size cannot be offered a ranking over one at all, where an inherited nought would rank every one of
 * its blocs as the smallest in the sector.
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
