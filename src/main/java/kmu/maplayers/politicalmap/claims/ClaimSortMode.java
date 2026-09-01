package kmu.maplayers.politicalmap.claims;

import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.lists.ListSortMode;
import kmlib.starsector.ui.widgets.lists.ListSortModes;
import kmlib.starsector.ui.widgets.lists.SortDirection;

import kmu.maplayers.politicalmap.base.BlocMarketSizeSortMode;
import kmu.maplayers.politicalmap.base.BlocSortModeComposer;
import kmu.maplayers.politicalmap.base.RankedBloc;
import kmu.maplayers.politicalmap.base.SizedBlocMetrics;
import kmu.maplayers.politicalmap.base.politics.ClaimStats;
import kmu.util.KmuStrings;

import java.awt.Color;
import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * The claims view's declaration of the framework's {@link ListSortMode} seam: the metrics its filter
 * picker ranks its blocs by, one per row of the sort selector. It ranks {@link RankedBloc} over
 * {@link ClaimStats} alone, so the domination metrics the held layers rank by - which describe a
 * market contest this layer never paints - cannot be offered here, and neither vocabulary can be
 * widened by the other's numbers.
 *
 * <p>It lives beside the view that declares it rather than in the shared base package because it is
 * one layer's vocabulary rather than shared machinery: a view painted by another mechanic declares
 * its own three-or-so modes instead of extending this one.
 *
 * <p>What this vocabulary states of itself is its numbers and the order ties break down them: claims,
 * then market size. Laying that declaration out into a ranking - the mode's own metric promoted to
 * the primary key, the rest of the chain behind it, the name and then the bloc id at the tail, and
 * only the primary key following the player's chosen direction - is {@link BlocSortModeComposer}'s,
 * so every mode of every vocabulary breaks a tie the same way.
 *
 * <p>The whole vocabulary is {@link #MODES}, which is these plus {@link #MARKET_SIZE} - a mode
 * declared outside this enum, because the number it ranks by is measured the same way under every
 * mechanic and so is stated once rather than per vocabulary. The enum keeps the claim count, which is
 * this mechanic's own.
 *
 * <p>{@link #DEFAULT} is claims - the metric the layer is actually painted by, so a fresh save and
 * any unrecognised stored key open on the ranking that matches what the map shows. The list holds
 * blocs that hold colonies while claiming nothing, and this is what places them: under the default
 * they sink to a tail below every claimant, so the list still opens on what the layer paints, while
 * the name mode interleaves them alphabetically.
 */
public enum ClaimSortMode implements ListSortMode<RankedBloc<ClaimStats>> {

    // Listed in the order the sort selector stacks its rows top to bottom, ahead of the shared market
    // size mode appended below: name first, then this mechanic's own numeric metric. This is the
    // display order, distinct from the tie-break chain below.
    NAME(
        "name",
        KmuStrings.POLITICAL_MAP_CTL_SORT_NAME,
        null),

    CLAIMS(
        "claims",
        KmuStrings.POLITICAL_MAP_CTL_SORT_CLAIMS,
        ClaimStats::claims);

    // This vocabulary's numbers in the order ties break down them. Each is read off the accessor its
    // own mode names - the enum constant above for the claim count, the shared capability for the
    // size - so the chain cannot drift from what the modes rank by.
    private static final List<ToIntFunction<ClaimStats>> CANONICAL_METRIC_CHAIN =
        List.of(CLAIMS.metric, SizedBlocMetrics::marketSize);

    /**
     * The whole-sector colony size ranking, bound to this vocabulary's tie-break chain. Held here so
     * a caller names it the way it names every other mode of this vocabulary, and so the one instance
     * {@link #MODES} offers is the one a stored key resolves back to.
     */
    public static final ListSortMode<RankedBloc<ClaimStats>> MARKET_SIZE =
        new BlocMarketSizeSortMode<>(CANONICAL_METRIC_CHAIN);

    /** The metric a fresh save and any unrecognised stored key fall back to, so an ordering always exists. */
    public static final ClaimSortMode DEFAULT = CLAIMS;

    /**
     * The claims view's whole sort vocabulary - every mode in selector display order with
     * {@link #DEFAULT} as the fallback. It is what the view bundles with its bloc list, so the list
     * and the modes that can rank it travel as one value and the stored-sort resolution reads the
     * same pair the selector draws.
     *
     * <p>Listed rather than read off {@code values()}, since the vocabulary is wider than the enum:
     * the shared size mode is a member of it without being a member of this type.
     */
    public static final ListSortModes<RankedBloc<ClaimStats>> MODES =
        new ListSortModes<>(
            List.<ListSortMode<RankedBloc<ClaimStats>>>of(NAME, CLAIMS, MARKET_SIZE),
            DEFAULT);

    private final String persistenceKey;
    private final String labelKey;

    // The numeric this mode reads off a bloc's stats, or null for the name mode, which sorts on the
    // bloc's label rather than any stat. Kept as the accessor so the comparator and the trailing value
    // read the same number.
    private final ToIntFunction<ClaimStats> metric;

    ClaimSortMode(String persistenceKey, String labelKey, ToIntFunction<ClaimStats> metric) {
        this.persistenceKey = persistenceKey;
        this.labelKey = labelKey;
        this.metric = metric;
    }

    /**
     * The name key deliberately spells the same as the dominance vocabulary's. Per-scope sort storage
     * keys each view's stored mode separately, so the two vocabularies never resolve against the same
     * slot and a shared spelling stays a readability win rather than a collision.
     *
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
     * The trailing value the picker draws on a bloc's row under this mode. The claim count carries no
     * colour of its own, so a value here is the plain kind {@link BlocSortModeComposer} draws in the
     * row's own tone.
     *
     * @param bloc          the listed bloc
     * @param defaultColour the tone the rest of the row draws in, which a plain number matches
     * @return the metric's value as a single run, or no runs when this mode sorts by name
     */
    @Override
    public List<TextSpan> resolveTrailingRuns(RankedBloc<ClaimStats> bloc, Color defaultColour) {
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
     * vocabulary's metric and chain laid out in the shape {@link BlocSortModeComposer} assembles.
     *
     * <p>Where the claimless blocs land under each mode is the visible consequence, and it is stated
     * on the type above rather than repeated per mode.
     *
     * @param direction the way the primary key runs - this mode's default, or the flipped opposite
     * @return the bloc comparator for this mode in the requested direction
     */
    @Override
    public Comparator<RankedBloc<ClaimStats>> comparator(SortDirection direction) {
        return BlocSortModeComposer.assembleComparator(metric, CANONICAL_METRIC_CHAIN, direction);
    }
}
