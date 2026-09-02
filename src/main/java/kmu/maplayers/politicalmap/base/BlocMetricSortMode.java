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
 * One political-map sort mode: a bloc ranking read off a number the metrics already carry, or off the
 * bloc's name. Every mode the map's vocabularies offer is one of these, and they differ only in the
 * four things each is handed - so what a mode <em>is</em> is stated here once instead of once per
 * vocabulary, where the seam's five answers had two places apiece to drift apart in.
 *
 * <p>A declared value rather than a type per mode, because none of the five answers turns on which
 * mode is asking. The key and the label are handed over, and the direction, the ordering and the drawn
 * value all follow from the single metric through {@link BlocSortModeComposer}. A ranking by something
 * the metrics do not carry answers at least one of them differently, so it implements the seam itself
 * rather than being declared here.
 *
 * <p>The metric being nullable is the one distinction the shape turns on: a mode that names no number
 * ranks by the bloc's label, lets the whole numeric chain follow, and draws no value. That is a
 * property of the ranking rather than of a vocabulary, so it is stated once here too.
 *
 * <p>Instances are compared by identity, deliberately, and that is a constraint on how a vocabulary
 * holds them: each mode must be published as a single constant and that same instance put in the set
 * the vocabulary offers. A sort checks its mode against the set it names, so a second instance of the
 * same declaration - built per read rather than held - is rejected as a mode its own vocabulary does
 * not offer. Keying equality on the persistence key would paper over that, at the cost of making any
 * two same-keyed modes equal, two different vocabularies' included.
 *
 * @param <S> the vocabulary's metrics record, whose numbers this mode ranks over
 */
public final class BlocMetricSortMode<S extends BlocMetrics> implements ListSortMode<RankedBloc<S>> {

    private final List<ToIntFunction<S>> canonicalMetricChain;
    private final String labelKey;
    private final ToIntFunction<S> metric;
    private final String persistenceKey;

    /**
     * @param persistenceKey       the save-stable key this mode's choice persists under
     * @param labelKey             this mod's string id for the text the mode's selector row draws
     * @param metric               the number this mode promotes to the primary key, kept as the
     *                             accessor so the ordering and the drawn value read the same one, or
     *                             null for a mode that ranks by name
     * @param canonicalMetricChain the declaring vocabulary's numbers in the order ties break down
     *                             them - this mode's own slot included, for the reason
     *                             {@link BlocSortModeComposer} states
     */
    public BlocMetricSortMode(
            String persistenceKey,
            String labelKey,
            ToIntFunction<S> metric,
            List<ToIntFunction<S>> canonicalMetricChain) {

        this.canonicalMetricChain = List.copyOf(canonicalMetricChain);
        this.labelKey = labelKey;
        this.metric = metric;
        this.persistenceKey = persistenceKey;
    }

    /**
     * @return the save-stable key this mode persists under; frozen once shipped, since renaming it
     *         silently resets every save that stored this mode back to its vocabulary's default
     */
    @Override
    public String persistenceKey() {
        return persistenceKey;
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
        return KmuStrings.get(labelKey);
    }

    /**
     * The trailing value the picker draws on a bloc's row under this mode. A number off the metrics
     * carries no colour of its own, so it is the plain kind {@link BlocSortModeComposer} draws in the
     * row's own tone.
     *
     * @param bloc          the listed bloc
     * @param defaultColour the tone the rest of the row draws in, which a plain number matches
     * @return the metric's value as a single run, or no runs when this mode ranks by name
     */
    @Override
    public List<TextSpan> resolveTrailingRuns(RankedBloc<S> bloc, Color defaultColour) {
        return BlocSortModeComposer.resolveMetricRuns(metric, bloc, defaultColour);
    }

    /**
     * The direction this mode ranks in until the player flips it. A fresh save and a mode the player
     * has just switched to both start here.
     *
     * @return this mode's natural sort direction
     */
    @Override
    public SortDirection defaultDirection() {
        return BlocSortModeComposer.resolveDefaultDirection(metric);
    }

    /**
     * The comparator that orders the picker's blocs under this mode in {@code direction} - this
     * mode's metric and its vocabulary's chain laid out in the shape {@link BlocSortModeComposer}
     * assembles.
     *
     * @param direction the way the primary key runs - this mode's default, or the flipped opposite
     * @return the bloc comparator for this mode in the requested direction
     */
    @Override
    public Comparator<RankedBloc<S>> comparator(SortDirection direction) {
        return BlocSortModeComposer.assembleComparator(metric, canonicalMetricChain, direction);
    }
}
