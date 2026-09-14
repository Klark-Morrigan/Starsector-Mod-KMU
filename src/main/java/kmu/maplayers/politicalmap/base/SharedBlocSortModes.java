package kmu.maplayers.politicalmap.base;

import kmlib.starsector.ui.widgets.lists.ListSortMode;

import kmu.util.KmuStrings;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * The sort modes every political-map vocabulary offers rather than declares - the ones whose number
 * means the same thing under any mechanic, so the key a save stores and the label a row draws belong
 * to the number rather than to the layer reading it.
 *
 * <p>Declared through a call rather than as a constant, because a mode still has to be bound to the
 * chain its vocabulary breaks ties down, and that chain is the one part which genuinely is per-layer.
 * So each vocabulary asks here for its own binding, and what it gets back is spelled the same
 * everywhere.
 *
 * <p>The third mode holder beside the two vocabularies, and named the same way for that reason: a
 * vocabulary's own numbers are declared where that vocabulary lives, as {@link BlocMetricSortMode}
 * values over its own accessors, and only a number two layers measure identically belongs here.
 * Anything else would put one mechanic's vocabulary in front of the other.
 */
public final class SharedBlocSortModes {

    // Frozen once shipped: renaming it silently resets every save that stored the size ranking back to
    // the storing vocabulary's default. Both vocabularies persisted this spelling while each declared
    // the mode separately, so folding the declaration together resets no save.
    private static final String MARKET_SIZE_PERSISTENCE_KEY = "market_size";

    private SharedBlocSortModes() {
    }

    /**
     * The whole-sector colony size ranking, bound to one vocabulary's tie-break chain. The size is the
     * same sum off the same per-system habitation projection whichever mechanic paints the map, which
     * is what makes one declaration serve every vocabulary carrying it.
     *
     * <p>{@link SizedBlocMetrics} keeps that from widening anyone: a vocabulary whose metrics record
     * does not carry a size cannot ask for this binding at all, rather than being handed a ranking
     * over a number its fold never summed.
     *
     * @param <S>                  the asking vocabulary's metrics record
     * @param canonicalMetricChain that vocabulary's numbers in the order ties break down them
     * @return the size ranking over that vocabulary's blocs
     */
    public static <S extends SizedBlocMetrics> ListSortMode<RankedBloc<S>> declareMarketSizeMode(
            List<ToIntFunction<S>> canonicalMetricChain) {

        return new BlocMetricSortMode<>(
            MARKET_SIZE_PERSISTENCE_KEY,
            KmuStrings.POLITICAL_MAP_CTL_SORT_MARKET_SIZE,
            SizedBlocMetrics::marketSize,
            canonicalMetricChain);
    }
}
