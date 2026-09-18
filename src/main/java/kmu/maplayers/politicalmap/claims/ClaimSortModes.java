package kmu.maplayers.politicalmap.claims;

import kmlib.starsector.ui.widgets.lists.ListSortMode;
import kmlib.starsector.ui.widgets.lists.ListSortModes;

import kmu.maplayers.politicalmap.base.BlocMetricSortMode;
import kmu.maplayers.politicalmap.base.BlocSortModeComposer;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.RankedBloc;
import kmu.maplayers.politicalmap.base.SharedBlocSortModes;
import kmu.maplayers.politicalmap.base.SizedBlocMetrics;
import kmu.maplayers.politicalmap.base.politics.ClaimStats;
import kmu.util.KmuStringKeys;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * The claims view's sort vocabulary: the metrics its filter picker ranks its blocs by, one per row of
 * the sort selector. Every mode here ranks {@link RankedBloc} over {@link ClaimStats} alone, so the
 * domination metrics the held layers rank by - which describe a market contest this layer never
 * paints - cannot be offered here, and neither vocabulary can be widened by the other's numbers.
 *
 * <p>It lives beside the view that declares it rather than in the shared base package because it is
 * one layer's vocabulary rather than shared machinery: a view painted by another mechanic declares its
 * own two-or-so modes instead of extending this one.
 *
 * <p>What a vocabulary states of itself is only its numbers, the key and label each is offered under,
 * and the order ties break down them: claims, then market size. What a mode then <em>is</em> is
 * {@link BlocMetricSortMode}'s, and the ranking it lays out is {@link BlocSortModeComposer}'s, so every
 * mode of every vocabulary answers the picker identically and breaks a tie the same way.
 *
 * <p>{@link #MARKET_SIZE} is asked for rather than declared: its number is measured the same way under
 * every mechanic, so {@link SharedBlocSortModes} owns its key and label and this vocabulary supplies
 * only the chain. The claim count is this mechanic's own and is named nowhere else.
 *
 * <p>{@link #DEFAULT} is claims - the metric the layer is actually painted by, so a fresh save and any
 * unrecognised stored key open on the ranking that matches what the map shows. The list holds blocs
 * that hold colonies while claiming nothing, and this is what places them: under the default they sink
 * to a tail below every claimant, so the list still opens on what the layer paints, while the name mode
 * interleaves them alphabetically.
 */
public final class ClaimSortModes {

    // The accessors this vocabulary ranks by, named once each so the mode that promotes a number and
    // the chain that falls through to it cannot end up reading different ones.
    private static final ToIntFunction<ClaimStats> CLAIMS_METRIC = ClaimStats::claims;
    private static final ToIntFunction<ClaimStats> MARKET_SIZE_METRIC = SizedBlocMetrics::marketSize;

    // This vocabulary's numbers in the order ties break down them, which is distinct from the display
    // order the modes below are listed in.
    private static final List<ToIntFunction<ClaimStats>> CANONICAL_METRIC_CHAIN =
        List.of(CLAIMS_METRIC, MARKET_SIZE_METRIC);

    // Declared in the order the sort selector stacks its rows top to bottom: name first, then the
    // numeric metrics. Each key is frozen once shipped, since renaming one silently resets every save
    // that stored that mode back to DEFAULT. The name key deliberately spells the same as the dominance
    // vocabulary's: per-scope sort storage keys each view's stored mode separately, so the two never
    // resolve against the same slot and a shared spelling stays a readability win rather than a
    // collision.
    public static final ListSortMode<RankedBloc<ClaimStats>> NAME =
        new BlocMetricSortMode<>(
            "name",
            KmuStringKeys.POLITICAL_MAP_CTL_SORT_NAME,
            null,
            CANONICAL_METRIC_CHAIN);

    public static final ListSortMode<RankedBloc<ClaimStats>> CLAIMS =
        new BlocMetricSortMode<>(
            "claims",
            KmuStringKeys.POLITICAL_MAP_CTL_SORT_CLAIMS,
            CLAIMS_METRIC,
            CANONICAL_METRIC_CHAIN);

    public static final ListSortMode<RankedBloc<ClaimStats>> MARKET_SIZE =
        SharedBlocSortModes.declareMarketSizeMode(CANONICAL_METRIC_CHAIN);

    /** The mode a fresh save and any unrecognised stored key fall back to, so an ordering always exists. */
    public static final ListSortMode<RankedBloc<ClaimStats>> DEFAULT = CLAIMS;

    /**
     * This mechanic's half of the claims view's sort vocabulary - every mode declared here in selector
     * display order, with {@link #DEFAULT} as the fallback. The view hands it to
     * {@link PoliticalMapView#buildBlocPickerRead}, which offers it with the standing ranking behind
     * it, so the selector draws these numbers plus the one criterion no vocabulary declares.
     *
     * <p>It travels with the bloc list from there on, which is what keeps the rows and the modes that
     * can rank them one value and has the stored-sort resolution read the same pair the selector
     * draws.
     */
    public static final ListSortModes<RankedBloc<ClaimStats>> MODES =
        new ListSortModes<>(
            List.of(NAME, CLAIMS, MARKET_SIZE),
            DEFAULT);

    private ClaimSortModes() {
    }
}
