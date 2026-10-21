package kmu.maplayers.ownermap.owners.holders.base.picker;

import kmlib.starsector.ui.widgets.lists.ListSortMode;

import kmu.maplayers.ownermap.picker.OwnerMetricSortMode;
import kmu.maplayers.ownermap.picker.OwnerMetrics;
import kmu.maplayers.ownermap.picker.RankedOwner;
import kmu.util.KmuOwnerMapStringKeys;

import java.util.List;
import java.util.function.ToIntFunction;

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
 * <p>Because the number is shared, so is the mode that ranks by it, declared once here over this
 * capability rather than once per vocabulary. What stays per-vocabulary is the chain ties break down
 * behind it, which is that layer's own numbers in that layer's own order.
 */
public interface SizedBlocMetrics
        extends OwnerMetrics {

    /**
     * The key the size ranking is saved under. Frozen: renaming it resets every save that stored the
     * size ranking. One spelling for every vocabulary carrying the mode, so the choice survives a
     * view switch the way the standing ranking's does.
     */
    String MARKET_SIZE_PERSISTENCE_KEY = "market_size";

    /**
     * The whole-sector size ranking, bound to one vocabulary's tie-break chain. Asked for through a
     * call rather than held as a constant, because the chain is the one part that is per-layer.
     *
     * @param <S>                  the asking vocabulary's metrics record
     * @param canonicalMetricChain that vocabulary's numbers in the order ties break down them
     * @return the size ranking over that vocabulary's blocs
     */
    static <S extends SizedBlocMetrics> ListSortMode<RankedOwner<S>> createMarketSizeMode(
            List<ToIntFunction<S>> canonicalMetricChain) {

        return new OwnerMetricSortMode<>(
            MARKET_SIZE_PERSISTENCE_KEY,
            KmuOwnerMapStringKeys.OWNER_MAP_CTL_SORT_MARKET_SIZE,
            SizedBlocMetrics::marketSize,
            canonicalMetricChain);
    }

    /**
     * The bloc's summed raw colony size across every colony it lives on anywhere in the sector -
     * whether or not the economy lists it, and not only in the systems its layer paints it for.
     *
     * @return the bloc's whole-sector colony size
     */
    int marketSize();
}
