package kmu.maplayers.politicalmap.base;

import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.lists.ListSortMode;
import kmlib.starsector.ui.widgets.lists.SortDirection;

import kmu.util.KmuStrings;

import java.awt.Color;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * The whole-sector colony size ranking, declared once for every political-map sort vocabulary whose
 * metrics carry that number. It is the same reading under all of them - the same sum, folded off the
 * same per-system habitation projection - so a declaration per vocabulary stated one fact twice, and
 * gave the key, the label, the direction and the drawn value two places apiece to drift apart in.
 *
 * <p>A class rather than a member of either vocabulary's enum, because an enum constant belongs to
 * the enum that declares it and there is no enum the vocabularies share. What a vocabulary still
 * supplies is the one part that is genuinely its own: the chain ties break down behind the promoted
 * key, which is that layer's numbers in that layer's order. So the mode is constructed per
 * vocabulary while being declared once.
 *
 * <p>{@link SizedBlocMetrics} is what keeps that from widening anyone: a vocabulary whose metrics
 * record does not carry a whole-sector size cannot be handed this mode at all, rather than being
 * offered a ranking over a number its fold never summed.
 *
 * @param <S> the vocabulary's metrics record, which must carry a whole-sector colony size
 */
public final class BlocMarketSizeSortMode<S extends SizedBlocMetrics>
        implements ListSortMode<RankedBloc<S>> {

    // Frozen once shipped: renaming it silently resets every save that stored this mode back to the
    // storing vocabulary's default. Both vocabularies already persisted this spelling while each
    // declared the mode itself, so folding the declaration together resets no save.
    private static final String PERSISTENCE_KEY = "market_size";

    private final List<ToIntFunction<S>> canonicalMetricChain;

    // The one accessor the ordering, the natural direction and the drawn value all read through, so
    // none of the three can end up about a different number from the other two.
    private final ToIntFunction<S> marketSizeMetric = SizedBlocMetrics::marketSize;

    /**
     * @param canonicalMetricChain the declaring vocabulary's numbers in the order ties break down
     *                             them - this mode's own slot included, which
     *                             {@link BlocSortModeComposer} states the reason for
     */
    public BlocMarketSizeSortMode(List<ToIntFunction<S>> canonicalMetricChain) {
        this.canonicalMetricChain = List.copyOf(canonicalMetricChain);
    }

    /**
     * @return the save-stable key this mode persists under, one spelling across every vocabulary
     *         that offers it
     */
    @Override
    public String persistenceKey() {
        return PERSISTENCE_KEY;
    }

    /**
     * The text this mode's selector row draws. The seam hands drawn text over rather than a string
     * key, since it cannot look a key up against this mod's own strings category, so the lookup
     * happens here.
     *
     * @return the drawn label for this mode's selector row
     */
    @Override
    public String resolveLabelText() {
        return KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_SORT_MARKET_SIZE);
    }

    /**
     * The trailing value the picker draws on a bloc's row under this mode. A colony size carries no
     * colour of its own, so it is the plain kind {@link BlocSortModeComposer} draws in the row's own
     * tone.
     *
     * @param bloc          the listed bloc
     * @param defaultColour the tone the rest of the row draws in, which a plain number matches
     * @return the bloc's whole-sector colony size as a single run
     */
    @Override
    public List<TextSpan> resolveTrailingRuns(RankedBloc<S> bloc, Color defaultColour) {
        return BlocSortModeComposer.resolveMetricRuns(marketSizeMetric, bloc, defaultColour);
    }

    /**
     * The direction this mode ranks in until the player flips it. A fresh save and a mode the player
     * has just switched to both start here.
     *
     * @return this mode's natural sort direction
     */
    @Override
    public SortDirection defaultDirection() {
        return BlocSortModeComposer.resolveDefaultDirection(marketSizeMetric);
    }

    /**
     * The comparator that orders the picker's blocs under this mode in {@code direction} - the size
     * promoted to the primary key, then the declaring vocabulary's chain laid out in the shape
     * {@link BlocSortModeComposer} assembles.
     *
     * @param direction the way the primary key runs - this mode's default, or the flipped opposite
     * @return the bloc comparator for this mode in the requested direction
     */
    @Override
    public Comparator<RankedBloc<S>> comparator(SortDirection direction) {
        return BlocSortModeComposer.assembleComparator(
            marketSizeMetric,
            canonicalMetricChain,
            direction);
    }
}
