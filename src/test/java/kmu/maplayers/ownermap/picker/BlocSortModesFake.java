package kmu.maplayers.ownermap.picker;

import kmlib.starsector.ui.widgets.lists.ListSortMode;
import kmlib.starsector.ui.widgets.lists.ListSortModes;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * A sort vocabulary as a tier case states one: a couple of numbers, a fallback, and the order the
 * selector stacks them in.
 *
 * <p>What a layer ranks its blocs by is the layer's, so a tier case about how a vocabulary is
 * offered, stored and fallen back to wants any vocabulary rather than a mechanic's own.
 *
 * <p>Final class with a private constructor: fixture of static wiring, no instances.
 */
public final class BlocSortModesFake {

    // The keys the two modes are labelled under. No case here draws a selector, so neither is ever
    // looked up - they are this vocabulary's own rather than borrowed from a layer's.
    private static final String NAME_LABEL_KEY = "test_sort_name";
    private static final String SCORE_LABEL_KEY = "test_sort_score";

    // The chain a tie falls through, spelled once so every mode below breaks a tie the same way.
    private static final List<ToIntFunction<BlocMetricsFake>> CHAIN =
        List.of(BlocMetricsFake::score, SizedBlocMetrics::marketSize);

    /** Ranked by nothing, which is how a vocabulary offers a name-ordered row. */
    public static final ListSortMode<RankedBloc<BlocMetricsFake>> NAME =
        new BlocMetricSortMode<>("name", NAME_LABEL_KEY, null, CHAIN);

    /** Ranked by the one number this vocabulary leads with. */
    public static final ListSortMode<RankedBloc<BlocMetricsFake>> SCORE =
        new BlocMetricSortMode<>(
            "score",
            SCORE_LABEL_KEY,
            BlocMetricsFake::score,
            CHAIN);

    /** The size every vocabulary carries, bound through the shared declaration. */
    public static final ListSortMode<RankedBloc<BlocMetricsFake>> MARKET_SIZE =
        SharedBlocSortModes.declareMarketSizeMode(CHAIN);

    /** What a fresh save and any unrecognised stored key fall back to. */
    public static final ListSortMode<RankedBloc<BlocMetricsFake>> DEFAULT = SCORE;

    /** Every mode this vocabulary offers, in the order a selector stacks them. */
    public static final ListSortModes<RankedBloc<BlocMetricsFake>> MODES =
        new ListSortModes<>(List.of(NAME, SCORE, MARKET_SIZE), DEFAULT);

    private BlocSortModesFake() {
        // fixture of static wiring, no instances.
    }
}
