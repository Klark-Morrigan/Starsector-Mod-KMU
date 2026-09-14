package kmu.maplayers.politicalmap.base;

import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.lists.ListSortMode;
import kmlib.starsector.ui.widgets.lists.SortDirection;

import java.awt.Color;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * What every political-map sort vocabulary answers the {@link ListSortMode} seam identically once its
 * own metric is named: the direction that metric naturally runs in, the comparator that lays a bloc
 * list out under it, and the value its rows draw. A vocabulary declares which numbers it ranks blocs
 * by and the canonical order it breaks ties down them - that is the part one layer's numbers make
 * different from another's, so it stays with the layer. How those declarations become a comparator
 * does not differ, and is stated here instead of once per vocabulary.
 *
 * <p>The shape assembled is fixed: the mode's own key in the requested direction, then the canonical
 * tie-break chain, then the name, then the bloc id. Sharing the tail is the point - two blocs a
 * player cannot tell apart break the same way under every mode of every vocabulary, and no mode can
 * be written that forgets the by-id key which keeps a fully-level pair from reshuffling between
 * frames. The tail is reachable on its own ({@link #appendSharedTail}) for a mode whose primary key
 * is not one of the metrics, so that promise holds for those too.
 *
 * <p>A mode that ranks by name rather than by a number names no metric, which is the one distinction
 * the shape turns on: it leads with the label, lets the whole numeric chain follow, and draws no
 * value at all.
 */
public final class BlocSortModeComposer {

    private BlocSortModeComposer() {
    }

    /**
     * The comparator that orders a picker's blocs under a mode in {@code direction}. Only the primary
     * key follows the direction; the tie-break chain behind it stays canonical, so two blocs level on
     * the primary break the same way whichever direction shows.
     *
     * @param <S>                  the vocabulary's metrics record
     * @param primaryMetric        the number this mode promotes to the primary key, or null for a
     *                             mode that ranks by name
     * @param canonicalMetricChain the vocabulary's numbers in the order ties break down them
     * @param direction            the way the primary key runs - the mode's default, or the flipped
     *                             opposite
     * @return the bloc comparator for that mode in the requested direction
     */
    public static <S extends BlocMetrics> Comparator<RankedBloc<S>> assembleComparator(
            ToIntFunction<S> primaryMetric,
            List<ToIntFunction<S>> canonicalMetricChain,
            SortDirection direction) {

        Comparator<RankedBloc<S>> order = resolvePrimaryOrder(primaryMetric, direction);
        // The chain is appended entire, the promoted metric included: a pair that reached the chain is
        // level on the primary key, so reading that same number again lands on the same answer and the
        // next key decides. Leaving its slot in is what keeps the chain one list rather than a list
        // plus a rule about which of its entries is being skipped.
        for (var metric : canonicalMetricChain) {
            order = order.thenComparing(compareByMetricDescending(metric));
        }
        // The tail follows for a by-name mode too, on the same reasoning the chain does: a pair that
        // reached it is level on the primary key, so a name mode's leading key answers identically
        // when the tail reads it again and the by-id key decides.
        return appendSharedTail(order);
    }

    /**
     * The tail every political-map ranking ends in, appended to an order the caller assembled itself:
     * the bloc's name, then its id.
     *
     * <p>The entry point exists for a mode whose primary key is not a number off the metrics, which
     * {@link #assembleComparator} has no way to accept. Such a mode still has to break a level pair
     * the way every other mode does - two blocs a player cannot tell apart reading the same way under
     * every mode of every vocabulary is the whole point of the tail being shared - and rolling it by
     * hand at each such mode is exactly the duplication this class exists to close.
     *
     * <p>The by-id key behind the name is what gives a total order, so two blocs level on every
     * visible key keep a fixed position rather than reshuffling as the per-frame sort re-runs.
     *
     * @param <S>   the vocabulary's metrics record
     * @param order the caller's own keys, in the direction the player asked for - the tail behind them
     *              stays canonical, so a level pair breaks the same way whichever direction shows
     * @return that order with the shared tail behind it
     */
    public static <S extends BlocMetrics> Comparator<RankedBloc<S>> appendSharedTail(
            Comparator<RankedBloc<S>> order) {

        return order
            .thenComparing(compareByNameAscending())
            .thenComparing(RankedBloc::itemId);
    }

    /**
     * The direction a mode ranks in until the player flips it, which follows from what it ranks by
     * rather than from which vocabulary declares it: a number runs high-to-low so the bigger bloc
     * leads, and a name runs A-to-Z.
     *
     * @param <S>    the vocabulary's metrics record
     * @param metric the number the mode ranks by, or null for a mode that ranks by name
     * @return that mode's natural sort direction
     */
    public static <S extends BlocMetrics> SortDirection resolveDefaultDirection(
            ToIntFunction<S> metric) {

        return metric == null
            ? SortDirection.ASCENDING
            : SortDirection.DESCENDING;
    }

    /**
     * The trailing value a plain numeric mode draws on a bloc's row: its metric read off the bloc's
     * whole-sector metrics, as one run in the tone the rest of the row takes. A number carries no
     * colour of its own, so it matches the name beside it rather than overriding the row.
     *
     * @param <S>           the vocabulary's metrics record
     * @param metric        the number the mode ranks by, or null for a mode that ranks by name and so
     *                      has nothing to show
     * @param bloc          the listed bloc
     * @param defaultColour the tone the rest of the row draws in, which a plain number matches
     * @return the metric's value as a single run, or no runs for a mode that ranks by name
     */
    public static <S extends BlocMetrics> List<TextSpan> resolveMetricRuns(
            ToIntFunction<S> metric,
            RankedBloc<S> bloc,
            Color defaultColour) {

        return metric == null
            ? List.of()
            : List.of(new TextSpan(String.valueOf(metric.applyAsInt(bloc.stats())), defaultColour));
    }

    // One metric as a high-to-low bloc comparator, so the bigger bloc ranks first - the direction every
    // number in the tie-break chain is read in, whichever way the primary key was asked to run.
    private static <S extends BlocMetrics> Comparator<RankedBloc<S>> compareByMetricDescending(
            ToIntFunction<S> metric) {

        return Comparator
            .comparingInt((RankedBloc<S> bloc) -> metric.applyAsInt(bloc.stats()))
            .reversed();
    }

    // Blocs by label, A-to-Z, case-insensitively, treating a null name as empty so an unlabelled bloc
    // sorts with the blanks rather than throwing.
    private static <S extends BlocMetrics> Comparator<RankedBloc<S>> compareByNameAscending() {
        return Comparator.comparing(
            BlocSortModeComposer::resolveDisplayNameOrEmpty,
            String.CASE_INSENSITIVE_ORDER);
    }

    private static String resolveDisplayNameOrEmpty(RankedBloc<?> bloc) {
        return bloc.displayName() == null ? "" : bloc.displayName();
    }

    // The mode's own key in the requested direction: its natural order - a number high-to-low, a name
    // A-to-Z - reversed when the requested direction is the opposite of that.
    private static <S extends BlocMetrics> Comparator<RankedBloc<S>> resolvePrimaryOrder(
            ToIntFunction<S> primaryMetric,
            SortDirection direction) {

        Comparator<RankedBloc<S>> defaultOrder = primaryMetric == null
            ? compareByNameAscending()
            : compareByMetricDescending(primaryMetric);

        return direction == resolveDefaultDirection(primaryMetric)
            ? defaultOrder
            : defaultOrder.reversed();
    }
}
