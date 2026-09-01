package kmu.maplayers.politicalmap.base;

import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.lists.ListSortMode;
import kmlib.starsector.ui.widgets.lists.ListSortModes;
import kmlib.starsector.ui.widgets.lists.SortDirection;

import kmu.maplayers.politicalmap.base.politics.DominanceStats;
import kmu.util.KmuStrings;

import java.awt.Color;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * The dominance-painted views' declaration of the framework's {@link ListSortMode} seam: the metrics
 * their filter picker ranks its selectable blocs by, one per row of the sort selector. Four of the
 * five promote one of {@link DominanceStats}'s numbers to the primary sort key; the fifth sorts by
 * name. It ranks {@link RankedBloc} over {@link DominanceStats} alone, so a view painted by some
 * other mechanic cannot be offered a vocabulary that reads numbers its blocs do not carry.
 * Each mode owns what the seam asks of it and nothing else: the save-stable key its choice
 * persists under, the label its selector row draws, its natural direction, the comparator that
 * lays the bloc list out under it, and the trailing value a row shows.
 *
 * <p>What this vocabulary states of itself is its numbers and the order ties break down them:
 * domination, then presence, then score, then market size. Laying that declaration out into a
 * ranking - the mode's own metric promoted to the primary key, the rest of the chain behind it, the
 * name and then the bloc id at the tail, and only the primary key following the player's chosen
 * direction - is {@link BlocSortModeComposer}'s, so every mode of every vocabulary breaks a tie the
 * same way.
 *
 * <p>{@link #DEFAULT} is domination, the metric a fresh save and any unrecognised stored key fall back
 * to, so the picker always has a live ordering even before the player picks one.
 */
public enum DominanceSortMode implements ListSortMode<RankedBloc<DominanceStats>> {

    // Listed in the order the sort selector stacks its rows top to bottom: name first, then the four
    // numeric metrics. This is the display order, distinct from the tie-break chain below.
    NAME(
        "name",
        KmuStrings.POLITICAL_MAP_CTL_SORT_NAME,
        null),

    DOMINATION(
        "domination",
        KmuStrings.POLITICAL_MAP_CTL_SORT_DOMINATION,
        DominanceStats::domination),

    PRESENCE(
        "presence",
        KmuStrings.POLITICAL_MAP_CTL_SORT_PRESENCE,
        DominanceStats::presence),

    SCORE(
        "score",
        KmuStrings.POLITICAL_MAP_CTL_SORT_SCORE,
        DominanceStats::score),

    MARKET_SIZE(
        "market_size",
        KmuStrings.POLITICAL_MAP_CTL_SORT_MARKET_SIZE,
        DominanceStats::marketSize);

    // The numeric metrics in their canonical tie-break order - the chain every mode breaks ties down.
    // A numeric mode moves its own metric to the front of this chain; the name mode appends the whole
    // chain after the name. Held as the modes' own accessors rather than as fresh method references,
    // so a mode's slot in the chain is the very accessor it ranks by and the assembly recognises it.
    private static final List<ToIntFunction<DominanceStats>> CANONICAL_METRIC_CHAIN =
        List.of(DOMINATION.metric, PRESENCE.metric, SCORE.metric, MARKET_SIZE.metric);

    /** The metric a fresh save and any unrecognised stored key fall back to, so an ordering always exists. */
    public static final DominanceSortMode DEFAULT = DOMINATION;

    /**
     * The dominance-painted views' whole sort vocabulary - every mode in selector display order
     * with {@link #DEFAULT} as the fallback. It is what a view bundles with its bloc list, so the
     * list and the modes that can rank it travel as one value and the stored-sort resolution reads
     * the same pair the selector draws.
     */
    public static final ListSortModes<RankedBloc<DominanceStats>> MODES =
        new ListSortModes<>(List.of(values()), DEFAULT);

    private final String persistenceKey;
    private final String labelKey;

    // The numeric this mode reads off a bloc's stats, or null for the name mode, which sorts on the
    // bloc's label rather than any stat. Kept as the accessor so the comparator and the trailing value
    // read the same number.
    private final ToIntFunction<DominanceStats> metric;

    DominanceSortMode(String persistenceKey, String labelKey, ToIntFunction<DominanceStats> metric) {
        this.persistenceKey = persistenceKey;
        this.labelKey = labelKey;
        this.metric = metric;
    }

    /**
     * @return the save-stable key this mode persists under; frozen once shipped, since renaming it
     *         silently resets every save that stored this mode back to {@link #DEFAULT}
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
     * The trailing value the picker draws on a bloc's row under this mode: the mode's metric read
     * off the bloc's whole-sector stats, as one run in the tone the rest of the row takes, or the
     * seam's no-runs default under the name mode, which ranks on the label and has no number to show.
     * None of these metrics carries a colour of its own, so none overrides the row's.
     *
     * @param bloc          the listed bloc
     * @param defaultColour the tone the rest of the row draws in, which a plain number matches
     * @return the metric's value as a single run, or no runs when this mode sorts by name
     */
    @Override
    public List<TextSpan> resolveTrailingRuns(
            RankedBloc<DominanceStats> bloc,
            Color defaultColour) {

        return BlocSortModeComposer.resolveMetricRuns(metric, bloc, defaultColour);
    }

    /**
     * The direction this mode ranks in until the player flips it: descending for the numeric metrics,
     * so the bigger bloc leads, and ascending for the name, so the list reads A-to-Z. A fresh save and
     * a mode the player has just switched to both start here.
     *
     * @return this mode's natural sort direction
     */
    @Override
    public SortDirection defaultDirection() {
        return BlocSortModeComposer.resolveDefaultDirection(metric);
    }

    /**
     * The comparator that orders the picker's blocs under this mode in {@code direction}: the mode's
     * own key first (run the requested way), then the shared canonical tie-break chain, then a by-id
     * key for a total order. Only the primary key follows {@code direction}; the tie-break chain stays
     * canonical, so two blocs level on the primary always break the same way whichever direction shows.
     *
     * @param direction the way the primary key runs - this mode's default, or the flipped opposite
     * @return the bloc comparator for this mode in the requested direction
     */
    @Override
    public Comparator<RankedBloc<DominanceStats>> comparator(SortDirection direction) {
        return BlocSortModeComposer.assembleComparator(metric, CANONICAL_METRIC_CHAIN, direction);
    }
}
