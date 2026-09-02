package kmu.maplayers.politicalmap.base;

import kmlib.starsector.ui.widgets.lists.ListSortMode;
import kmlib.starsector.ui.widgets.lists.ListSortModes;

import kmu.maplayers.politicalmap.base.politics.DominanceStats;
import kmu.util.KmuStrings;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * The dominance-painted views' sort vocabulary: the metrics their filter picker ranks its selectable
 * blocs by, one per row of the sort selector. Every mode here ranks {@link RankedBloc} over
 * {@link DominanceStats} alone, so a view painted by some other mechanic cannot be offered a
 * vocabulary that reads numbers its blocs do not carry.
 *
 * <p>What a vocabulary states of itself is only its numbers, the key and label each is offered under,
 * and the order ties break down them: domination, then presence, then score, then market size. What a
 * mode then <em>is</em> is {@link BlocMetricSortMode}'s, and the ranking it lays out is
 * {@link BlocSortModeComposer}'s, so every mode of every vocabulary answers the picker identically and
 * breaks a tie the same way.
 *
 * <p>{@link #MARKET_SIZE} is asked for rather than declared: its number is measured the same way under
 * every mechanic, so {@link SharedBlocSortModes} owns its key and label and this vocabulary supplies
 * only the chain. The rest are this contest's own and are named nowhere else.
 *
 * <p>{@link #DEFAULT} is domination, the metric a fresh save and any unrecognised stored key fall back
 * to, so the picker always has a live ordering even before the player picks one.
 */
public final class DominanceSortModes {

    // The accessors this vocabulary ranks by, named once each so the mode that promotes a number and
    // the chain that falls through to it cannot end up reading different ones.
    private static final ToIntFunction<DominanceStats> DOMINATION_METRIC = DominanceStats::domination;
    private static final ToIntFunction<DominanceStats> MARKET_SIZE_METRIC = SizedBlocMetrics::marketSize;
    private static final ToIntFunction<DominanceStats> PRESENCE_METRIC = DominanceStats::presence;
    private static final ToIntFunction<DominanceStats> SCORE_METRIC = DominanceStats::score;

    // This vocabulary's numbers in the order ties break down them, which is distinct from the display
    // order the modes below are listed in.
    private static final List<ToIntFunction<DominanceStats>> CANONICAL_METRIC_CHAIN =
        List.of(
            DOMINATION_METRIC,
            PRESENCE_METRIC,
            SCORE_METRIC,
            MARKET_SIZE_METRIC);

    // Declared in the order the sort selector stacks its rows top to bottom: name first, then the
    // numeric metrics. Each key is frozen once shipped, since renaming one silently resets every save
    // that stored that mode back to DEFAULT.
    public static final ListSortMode<RankedBloc<DominanceStats>> NAME =
        new BlocMetricSortMode<>(
            "name",
            KmuStrings.POLITICAL_MAP_CTL_SORT_NAME,
            null,
            CANONICAL_METRIC_CHAIN);

    public static final ListSortMode<RankedBloc<DominanceStats>> DOMINATION =
        new BlocMetricSortMode<>(
            "domination",
            KmuStrings.POLITICAL_MAP_CTL_SORT_DOMINATION,
            DOMINATION_METRIC,
            CANONICAL_METRIC_CHAIN);

    public static final ListSortMode<RankedBloc<DominanceStats>> PRESENCE =
        new BlocMetricSortMode<>(
            "presence",
            KmuStrings.POLITICAL_MAP_CTL_SORT_PRESENCE,
            PRESENCE_METRIC,
            CANONICAL_METRIC_CHAIN);

    public static final ListSortMode<RankedBloc<DominanceStats>> SCORE =
        new BlocMetricSortMode<>(
            "score",
            KmuStrings.POLITICAL_MAP_CTL_SORT_SCORE,
            SCORE_METRIC,
            CANONICAL_METRIC_CHAIN);

    public static final ListSortMode<RankedBloc<DominanceStats>> MARKET_SIZE =
        SharedBlocSortModes.declareMarketSizeMode(CANONICAL_METRIC_CHAIN);

    /** The mode a fresh save and any unrecognised stored key fall back to, so an ordering always exists. */
    public static final ListSortMode<RankedBloc<DominanceStats>> DEFAULT = DOMINATION;

    /**
     * The dominance-painted views' whole sort vocabulary - every mode in selector display order with
     * {@link #DEFAULT} as the fallback. It is what a view bundles with its bloc list, so the list and
     * the modes that can rank it travel as one value and the stored-sort resolution reads the same
     * pair the selector draws.
     */
    public static final ListSortModes<RankedBloc<DominanceStats>> MODES =
        new ListSortModes<>(
            List.of(
                NAME,
                DOMINATION,
                PRESENCE,
                SCORE,
                MARKET_SIZE),
            DEFAULT);

    private DominanceSortModes() {
    }
}
