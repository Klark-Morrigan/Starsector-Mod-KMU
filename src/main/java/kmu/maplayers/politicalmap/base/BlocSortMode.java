package kmu.maplayers.politicalmap.base;

import kmu.maplayers.base.sidebar.ListSort;
import kmu.maplayers.base.sidebar.ListSortMode;
import kmu.maplayers.base.sidebar.ListSortModes;
import kmu.maplayers.base.sidebar.SortDirection;
import kmu.maplayers.politicalmap.base.politics.BlocStats;
import kmu.util.KmuStrings;

import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * The political map's declaration of the framework's {@link ListSortMode} seam: the metrics the
 * filter picker ranks its selectable blocs by, one per row of the sort selector. Four of the five
 * promote one of {@link BlocStats}'s numbers to the primary sort key; the fifth sorts by name.
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
public enum BlocSortMode implements ListSortMode<SelectableBloc> {

    // Listed in the order the sort selector stacks its rows top to bottom: name first, then the four
    // numeric metrics. This is the display order, distinct from the tie-break chain below.
    NAME(
        "name",
        KmuStrings.POLITICAL_MAP_CTL_SORT_NAME,
        null),

    DOMINATION(
        "domination",
        KmuStrings.POLITICAL_MAP_CTL_SORT_DOMINATION,
        BlocStats::domination),

    PRESENCE(
        "presence",
        KmuStrings.POLITICAL_MAP_CTL_SORT_PRESENCE,
        BlocStats::presence),

    SCORE(
        "score",
        KmuStrings.POLITICAL_MAP_CTL_SORT_SCORE,
        BlocStats::score),

    MARKET_SIZE(
        "market_size",
        KmuStrings.POLITICAL_MAP_CTL_SORT_MARKET_SIZE,
        BlocStats::marketSize);

    // The numeric metrics in their canonical tie-break order - the chain every mode breaks ties down.
    // A numeric mode moves its own metric to the front of this chain; the name mode appends the whole
    // chain after the name.
    private static final List<BlocSortMode> CANONICAL_NUMERIC_ORDER =
        List.of(DOMINATION, PRESENCE, SCORE, MARKET_SIZE);

    /** The metric a fresh save and any unrecognised stored key fall back to, so an ordering always exists. */
    public static final BlocSortMode DEFAULT = DOMINATION;

    /**
     * The political map's whole sort vocabulary - every mode in selector display order with
     * {@link #DEFAULT} as the fallback - declared once so the stored-sort resolution and the sort
     * selector read the same pair.
     */
    public static final ListSortModes<SelectableBloc> MODES =
        new ListSortModes<>(List.of(values()), DEFAULT);

    private final String persistenceKey;
    private final String labelKey;

    // The numeric this mode reads off a bloc's stats, or null for the name mode, which sorts on the
    // bloc's label rather than any stat. Kept as the accessor so the comparator and the trailing value
    // read the same number.
    private final ToIntFunction<BlocStats> metric;

    BlocSortMode(String persistenceKey, String labelKey, ToIntFunction<BlocStats> metric) {
        this.persistenceKey = persistenceKey;
        this.labelKey = labelKey;
        this.metric = metric;
    }

    /**
     * The player's stored sort over these modes, read live: the framework resolution run against
     * every mode with {@link #DEFAULT} as the fallback, so a fresh save and an unrecognised stored
     * key both read as domination in its natural order, and a save with no stored direction reads
     * the stored mode's own default. The one place the political map resolves the stored pair,
     * shared by the picker that ranks under it and the sort selector that acts on it.
     *
     * @return the stored sort
     */
    public static ListSort<SelectableBloc> resolveStoredSort() {
        return ListSort.resolveStored(MODES);
    }

    /**
     * @return the save-stable key this mode persists under; frozen once shipped, since renaming it
     *         silently resets every save that stored this mode back to {@link #DEFAULT}
     */
    @Override
    public String persistenceKey() {
        return persistenceKey;
    }

    /** @return the string key of this mode's selector-row label */
    @Override
    public String labelKey() {
        return labelKey;
    }

    /**
     * The trailing value the picker draws on a bloc's row under this mode: the mode's metric read
     * off the bloc's whole-sector stats, or the seam's blank default under the name mode, which
     * ranks on the label and has no number to show.
     *
     * @param bloc the listed bloc
     * @return the metric's value as text, or "" when this mode sorts by name
     */
    @Override
    public String resolveTrailingValue(SelectableBloc bloc) {
        return metric == null ? "" : String.valueOf(metric.applyAsInt(bloc.stats()));
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
    public Comparator<SelectableBloc> comparator(SortDirection direction) {
        Comparator<SelectableBloc> order = primaryComparator(direction);
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
        return order.thenComparing(SelectableBloc::blocId);
    }

    // This mode's primary key in the requested direction: the default-direction primary (numerics
    // high-to-low, name A-to-Z), reversed when the requested direction is the opposite of the mode's
    // default. Only the primary flips - the tie-break chain the caller appends stays canonical.
    private Comparator<SelectableBloc> primaryComparator(SortDirection direction) {

        Comparator<SelectableBloc> defaultOrder = metric == null
            ? byNameAscending()
            : byMetricDescending();

        return direction == defaultDirection()
            ? defaultOrder
            : defaultOrder.reversed();
    }

    // This mode's metric as a high-to-low bloc comparator, so the bigger bloc ranks first. Only ever
    // built for a numeric mode, where the metric accessor is non-null.
    private Comparator<SelectableBloc> byMetricDescending() {
        return Comparator
            .comparingInt((SelectableBloc bloc) -> metric.applyAsInt(bloc.stats()))
            .reversed();
    }

    // Blocs by label, A-to-Z, case-insensitively, treating a null name as empty so an unlabelled bloc
    // sorts with the blanks rather than throwing.
    private static Comparator<SelectableBloc> byNameAscending() {
        return Comparator.comparing(
            BlocSortMode::displayNameOrEmpty,
            String.CASE_INSENSITIVE_ORDER);
    }

    private static String displayNameOrEmpty(SelectableBloc bloc) {
        return bloc.displayName() == null ? "" : bloc.displayName();
    }
}
