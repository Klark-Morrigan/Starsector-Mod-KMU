package kmu.maplayers.ownermap.picker;

import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.lists.ListSortComparators;
import kmlib.starsector.ui.widgets.lists.ListSortMode;
import kmlib.starsector.ui.widgets.lists.SortDirection;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * What every owner-map sort vocabulary answers the {@link ListSortMode} seam identically once its
 * own metric is named: the direction that metric naturally runs in, the comparator that lays an owner
 * list out under it, and the value its rows draw. A vocabulary declares which numbers it ranks owners
 * by and the canonical order it breaks ties down them - that is the part one layer's numbers make
 * different from another's, so it stays with the layer.
 *
 * <p>The comparator shape itself - the mode's key, the chain, then the name and the owner ID - is
 * KMLib's {@link ListSortComparators}, which ranks over a metric read off the list item. A vocabulary
 * here names its metrics on its stats record instead, so this reads each one off
 * {@link RankedOwner#stats()} and hands the result over. That keeps a vocabulary's declarations
 * free of the row they sit on, and every owner-map mode on the same tail as any other list.
 *
 * <p>A mode that ranks by name rather than by a number names no metric, which is the one distinction
 * the shape turns on: it leads with the label, lets the whole numeric chain follow, and draws no
 * value at all.
 */
public final class OwnerSortModeComposer {

    private OwnerSortModeComposer() {
    }

    /**
     * The comparator that orders a picker's owners under a mode in {@code direction}. Only the primary
     * key follows the direction; the tie-break chain behind it stays canonical, so two owners level on
     * the primary break the same way whichever direction shows.
     *
     * @param <S>                  the vocabulary's metrics record
     * @param primaryMetric        the number this mode promotes to the primary key, or null for a
     *                             mode that ranks by name
     * @param canonicalMetricChain the vocabulary's numbers in the order ties break down them
     * @param direction            the way the primary key runs - the mode's default, or the flipped
     *                             opposite
     * @return the owner comparator for that mode in the requested direction
     */
    public static <S extends OwnerMetrics> Comparator<RankedOwner<S>> assembleComparator(
            ToIntFunction<S> primaryMetric,
            List<ToIntFunction<S>> canonicalMetricChain,
            SortDirection direction) {

        var ownerMetricChain = new ArrayList<ToIntFunction<RankedOwner<S>>>(canonicalMetricChain.size());

        for (var metric : canonicalMetricChain) {
            ownerMetricChain.add(readMetricOffStats(metric));
        }

        return ListSortComparators.assembleComparator(
            readMetricOffStats(primaryMetric),
            ownerMetricChain,
            direction);
    }

    /**
     * The tail every owner-map ranking ends in, appended to an order the caller assembled itself:
     * the owner's name, then its ID.
     *
     * <p>The entry point exists for a mode whose primary key is not a number off the metrics, which
     * {@link #assembleComparator} has no way to accept. Such a mode still has to break a level pair
     * the way every other mode does, so it reaches the same tail rather than rolling one by hand.
     *
     * @param <S>   the vocabulary's metrics record
     * @param order the caller's own keys, in the direction the player asked for - the tail behind them
     *              stays canonical, so a level pair breaks the same way whichever direction shows
     * @return that order with the shared tail behind it
     */
    public static <S extends OwnerMetrics> Comparator<RankedOwner<S>> appendSharedTail(
            Comparator<RankedOwner<S>> order) {

        return ListSortComparators.appendNameAndIdTail(order);
    }

    /**
     * The direction a mode ranks in until the player flips it, which follows from what it ranks by
     * rather than from which vocabulary declares it: a number runs high-to-low so the bigger owner
     * leads, and a name runs A-to-Z.
     *
     * @param <S>    the vocabulary's metrics record
     * @param metric the number the mode ranks by, or null for a mode that ranks by name
     * @return that mode's natural sort direction
     */
    public static <S extends OwnerMetrics> SortDirection resolveDefaultDirection(
            ToIntFunction<S> metric) {

        return ListSortComparators.resolveDefaultDirection(readMetricOffStats(metric));
    }

    /**
     * The trailing value a plain numeric mode draws on an owner's row: its metric read off the owner's
     * whole-sector metrics, as one run in the tone the rest of the row takes. A number carries no
     * colour of its own, so it matches the name beside it rather than overriding the row.
     *
     * @param <S>           the vocabulary's metrics record
     * @param metric        the number the mode ranks by, or null for a mode that ranks by name and so
     *                      has nothing to show
     * @param owner         the listed owner
     * @param defaultColour the tone the rest of the row draws in, which a plain number matches
     * @return the metric's value as a single run, or no runs for a mode that ranks by name
     */
    public static <S extends OwnerMetrics> List<TextSpan> resolveMetricRuns(
            ToIntFunction<S> metric,
            RankedOwner<S> owner,
            Color defaultColour) {

        return metric == null
            ? List.of()
            : List.of(new TextSpan(String.valueOf(metric.applyAsInt(owner.stats())), defaultColour));
    }

    // One stats metric as a metric on the owner row, which is the shape the library ranks by. Null
    // stays null, since a null metric is how a by-name mode is told apart.
    private static <S extends OwnerMetrics> ToIntFunction<RankedOwner<S>> readMetricOffStats(
            ToIntFunction<S> metric) {

        return metric == null
            ? null
            : owner -> metric.applyAsInt(owner.stats());
    }
}
