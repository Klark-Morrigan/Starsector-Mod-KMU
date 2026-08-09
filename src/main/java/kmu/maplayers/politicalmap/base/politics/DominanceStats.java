package kmu.maplayers.politicalmap.base.politics;

import kmu.maplayers.politicalmap.base.BlocMetrics;
import kmu.maplayers.politicalmap.base.dominance.MarketFootprint;

/**
 * The four whole-sector numbers the filter picker sorts and displays a bloc by on the layers
 * domination paints, computed once per grouped dominance pass and carried beside the bloc's identity
 * on {@link kmu.maplayers.politicalmap.base.RankedBloc}. It exists so the picker can rank and label a
 * bloc without re-reading the economy: a single pass folds every system the bloc holds into these
 * totals, and the sidebar then reads the totals rather than walking the sector again per frame.
 *
 * <p>The four are deliberately distinct measures a player might sort by. Domination and presence are
 * system counts - how many systems the bloc wins outright versus how many it merely holds a market
 * in - so a bloc concentrated in a few contested systems reads differently from one spread thin.
 * Score is the summed dominance weight (the size-times-stability worth {@link MarketFootprint}
 * carries), and market size is the summed raw {@code MarketAPI.getSize()}, so a heavily-weighted bloc
 * and a merely-large one are told apart. Market size is kept here rather than in {@link
 * MarketFootprint}, which stays scoped to the dominance-weight quantities the rule compares.
 *
 * <p>Every bloc in these stats holds a market somewhere, so every row on these layers has something
 * to show under the metrics they are painted by and none of them reads back - which is why the
 * {@link BlocMetrics} default stands here unoverridden.
 *
 * <p>Plain data with no Starsector types, so the aggregation is exercised on hand-built inputs.
 *
 * @param domination the number of systems the bloc is the dominant holder of, under the active
 *                   grouping
 * @param presence   the number of systems the bloc holds a counted market in; a bloc in these stats
 *                   at all has presence of at least one
 * @param score      the bloc's combined dominance weight summed across every system it is present in
 * @param marketSize the bloc's summed raw colony size across every market it owns
 */
public record DominanceStats(
    int domination,
    int presence,
    int score,
    int marketSize) implements BlocMetrics {

    /** A bloc present in no system; the identity a per-system accumulation folds into. */
    public static final DominanceStats EMPTY = new DominanceStats(0, 0, 0, 0);

    /**
     * Folds one system the bloc holds a market in into these stats: a present-system entry always,
     * a domination count only when the bloc wins that system.
     *
     * @param isDominant       whether the bloc is this system's dominant holder, which adds one to
     *                         domination; a present-but-not-dominant bloc adds to presence alone
     * @param systemScore      the bloc's combined dominance weight in this system, added to score
     * @param systemMarketSize the summed raw colony size of the bloc's markets in this system, added
     *                         to market size
     * @return a new stats value including this system
     */
    public DominanceStats addSystem(boolean isDominant, int systemScore, int systemMarketSize) {
        return new DominanceStats(
            domination + (isDominant ? 1 : 0),
            presence + 1,
            score + systemScore,
            marketSize + systemMarketSize);
    }
}
