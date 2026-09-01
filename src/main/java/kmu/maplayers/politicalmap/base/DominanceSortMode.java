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
 * <p>The comparators share one fixed tie-break chain so two blocs level on the chosen metric always
 * break the same way: domination, then presence, then score, then market size, then name. A numeric
 * mode promotes its own metric to the front of that chain and lets the rest follow in the canonical
 * order; the name mode leads with the name and lets the whole numeric chain follow. Numeric keys sort
 * high-to-low (the bigger bloc ranks first), the name sorts A-to-Z, and a final by-id key gives a
 * total order so a fully-level pair never reshuffles between frames. This is each mode's {@link
 * #defaultDirection()} ordering; {@link #comparator(SortDirection)} flips only the primary key when
 * the player picks the opposite direction, leaving the canonical tie-break chain fixed either way.
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
    // chain after the name.
    private static final List<DominanceSortMode> CANONICAL_NUMERIC_ORDER =
        List.of(DOMINATION, PRESENCE, SCORE, MARKET_SIZE);

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

        return metric == null
            ? List.of()
            : List.of(new TextSpan(String.valueOf(metric.applyAsInt(bloc.stats())), defaultColour));
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
        return metric == null ? SortDirection.ASCENDING : SortDirection.DESCENDING;
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
        Comparator<RankedBloc<DominanceStats>> order = primaryComparator(direction);
        if (metric == null) {
            // Name mode leads with the label, then breaks ties down the whole numeric chain.
            for (var mode : CANONICAL_NUMERIC_ORDER) {
                order = order.thenComparing(mode.byMetricDescending());
            }
        } else {
            // A numeric mode leads with its own metric, then follows the canonical chain skipping that
            // metric's own slot, and finally breaks a numeric-level pair by name.
            for (var mode : CANONICAL_NUMERIC_ORDER) {
                if (mode != this) {
                    order = order.thenComparing(mode.byMetricDescending());
                }
            }
            order = order.thenComparing(byNameAscending());
        }
        // A final by-id key gives a total order, so two blocs level on every visible key keep a fixed
        // position rather than reshuffling as the per-frame sort re-runs.
        return order.thenComparing(RankedBloc::itemId);
    }

    // This mode's primary key in the requested direction: the default-direction primary (numerics
    // high-to-low, name A-to-Z), reversed when the requested direction is the opposite of the mode's
    // default. Only the primary flips - the tie-break chain the caller appends stays canonical.
    private Comparator<RankedBloc<DominanceStats>> primaryComparator(SortDirection direction) {

        Comparator<RankedBloc<DominanceStats>> defaultOrder = metric == null
            ? byNameAscending()
            : byMetricDescending();

        return direction == defaultDirection()
            ? defaultOrder
            : defaultOrder.reversed();
    }

    // This mode's metric as a high-to-low bloc comparator, so the bigger bloc ranks first. Only ever
    // built for a numeric mode, where the metric accessor is non-null.
    private Comparator<RankedBloc<DominanceStats>> byMetricDescending() {
        return Comparator
            .comparingInt((RankedBloc<DominanceStats> bloc) -> metric.applyAsInt(bloc.stats()))
            .reversed();
    }

    // Blocs by label, A-to-Z, case-insensitively, treating a null name as empty so an unlabelled bloc
    // sorts with the blanks rather than throwing.
    private static Comparator<RankedBloc<DominanceStats>> byNameAscending() {
        return Comparator.comparing(
            DominanceSortMode::displayNameOrEmpty,
            String.CASE_INSENSITIVE_ORDER);
    }

    private static String displayNameOrEmpty(RankedBloc<DominanceStats> bloc) {
        return bloc.displayName() == null ? "" : bloc.displayName();
    }
}
