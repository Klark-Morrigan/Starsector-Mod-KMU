package kmu.maplayers.ownermap.picker;

import kmlib.starsector.ui.widgets.lists.ListSortMode;
import kmlib.starsector.ui.widgets.lists.ListSortModes;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * A sort vocabulary as a tier case states one: a couple of numbers, a fallback, and the order the
 * selector stacks them in.
 *
 * <p>What a layer ranks its owners by is the layer's, so a tier case about how a vocabulary is
 * offered, stored and fallen back to wants any vocabulary rather than a mechanic's own.
 *
 * <p>Final class with a private constructor: fixture of static wiring, no instances.
 */
public final class OwnerSortModesFake {

    // The keys the two modes are labelled under. No case here draws a selector, so neither is ever
    // looked up - they are this vocabulary's own rather than borrowed from a layer's.
    private static final String NAME_LABEL_KEY = "test_sort_name";
    private static final String SCORE_LABEL_KEY = "test_sort_score";

    // The chain a tie falls through, spelled once so every mode below breaks a tie the same way.
    private static final List<ToIntFunction<OwnerMetricsFake>> CHAIN =
        List.of(OwnerMetricsFake::score, OwnerMetricsFake::size);

    /** Ranked by nothing, which is how a vocabulary offers a name-ordered row. */
    public static final ListSortMode<RankedOwner<OwnerMetricsFake>> NAME =
        new OwnerMetricSortMode<>("name", NAME_LABEL_KEY, null, CHAIN);

    /** Ranked by the one number this vocabulary leads with. */
    public static final ListSortMode<RankedOwner<OwnerMetricsFake>> SCORE =
        new OwnerMetricSortMode<>(
            "score",
            SCORE_LABEL_KEY,
            OwnerMetricsFake::score,
            CHAIN);

    /** What a fresh save and any unrecognised stored key fall back to. */
    public static final ListSortMode<RankedOwner<OwnerMetricsFake>> DEFAULT = SCORE;

    /** Every mode this vocabulary offers, in the order a selector stacks them. */
    public static final ListSortModes<RankedOwner<OwnerMetricsFake>> MODES =
        new ListSortModes<>(List.of(NAME, SCORE), DEFAULT);

    private OwnerSortModesFake() {
        // fixture of static wiring, no instances.
    }
}
