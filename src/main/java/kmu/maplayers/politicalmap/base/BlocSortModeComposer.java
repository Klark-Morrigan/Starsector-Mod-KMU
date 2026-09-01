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
 * tie-break chain minus the mode's own slot, then the name, then the bloc id. Sharing the tail is the
 * point - two blocs a player cannot tell apart break the same way under every mode of every
 * vocabulary, and no mode can be written that forgets the by-id key which keeps a fully-level pair
 * from reshuffling between frames.
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
     * @param canonicalMetricChain the vocabulary's numbers in the order ties break down them, held as
     *                             the same accessors its modes name, so the mode's own slot is
     *                             recognised in the chain and left out of it
     * @param direction            the way the primary key runs - the mode's default, or the flipped
     *                             opposite
     * @return the bloc comparator for that mode in the requested direction
     */
    public static <S extends BlocMetrics> Comparator<RankedBloc<S>> assembleComparator(
            ToIntFunction<S> primaryMetric,
            List<ToIntFunction<S>> canonicalMetricChain,
            SortDirection direction) {

        Comparator<RankedBloc<S>> order = resolvePrimaryOrder(primaryMetric, direction);
        for (var metric : canonicalMetricChain) {
            // The primary's own slot is skipped by identity: a chain still holding it would compare a
            // key the primary has already found level, which decides nothing and reads as though it
            // might.
            if (metric != primaryMetric) {
                order = order.thenComparing(compareByMetricDescending(metric));
            }
        }
        if (primaryMetric != null) {
            // A numeric mode breaks a chain-level pair by name; the name mode has already led with it.
            order = order.thenComparing(compareByNameAscending());
        }
        // A final by-id key gives a total order, so two blocs level on every visible key keep a fixed
        // position rather than reshuffling as the per-frame sort re-runs.
        return order.thenComparing(RankedBloc::itemId);
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
