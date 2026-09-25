package kmu.maplayers.politicalmap.dominance;

import kmu.maplayers.ownermap.picker.PaintingBlocMetrics;
import kmu.maplayers.ownermap.picker.SizedBlocMetrics;
import kmu.maplayers.politicalmap.dominance.weighting.MarketFootprint;

/**
 * The four whole-sector numbers the filter picker sorts and displays a bloc by on the layers
 * domination paints, computed once per grouped dominance pass and carried beside the bloc's identity
 * on {@link kmu.maplayers.ownermap.RankedBloc}. It exists so the picker can rank and label a
 * bloc without re-reading the economy: a single pass folds every system the bloc holds into these
 * totals, and the sidebar then reads the totals rather than walking the sector again per frame.
 *
 * <p>The four are deliberately distinct measures a player might sort by. Domination and presence are
 * system counts - how many systems the bloc wins outright versus how many it merely lives in - so a
 * bloc concentrated in a few contested systems reads differently from one spread thin.
 * Score is the summed dominance weight (the size-times-stability worth {@link MarketFootprint}
 * carries), and market size is the summed raw {@code MarketAPI.getSize()}, so a heavily-weighted bloc
 * and a merely-large one are told apart. Market size is kept here rather than in {@link
 * MarketFootprint}, which stays scoped to the dominance-weight quantities the rule compares.
 *
 * <p>The two halves are scoped differently on purpose, and that is what lets a bloc be listed at
 * nought. Domination and score answer what the contest made of the bloc, so they count only the
 * colonies it weighed; presence and market size answer how much of the sector the bloc lives in, so
 * they count every colony the player may be shown it living on - a station the economy never
 * registered included.
 *
 * <p>Presence in these stats is therefore not the same as painting something. Dominance weight is
 * what these layers paint by, and it is economy-fed - a colony the economy does not list has no
 * industries, no conditions and no computed stability - so a bloc present through such colonies
 * alone folds in at a score of nought and paints nowhere. That is what {@link #isPaintingNothing}
 * answers.
 *
 * <p>Plain data with no Starsector types, so the aggregation runs without a live economy.
 *
 * @param domination the number of systems the bloc is the dominant holder of, under the active
 *                   grouping
 * @param presence   the number of systems the bloc lives in; a bloc in these stats at all has
 *                   presence of at least one
 * @param score      the bloc's combined dominance weight summed across every system it is present in
 * @param marketSize the bloc's summed raw colony size across every colony it lives on, whether or
 *                   not the economy lists it
 */
public record DominanceStats(
    int domination,
    int presence,
    int score,
    int marketSize) implements PaintingBlocMetrics, SizedBlocMetrics {

    /** A bloc present in no system; the identity a per-system accumulation folds into. */
    public static final DominanceStats EMPTY = new DominanceStats(0, 0, 0, 0);

    /**
     * These layers paint by dominance weight, so a summed score of nought is precisely "paints
     * nothing here" - the bloc holds something the player can see, which is why it is listed at all,
     * but nothing the contest weighed, so no cell is coloured for it anywhere.
     *
     * <p>The test is the weight alone, not the domination count: a bloc that competes everywhere and
     * wins nowhere still took part in the contest the fills are the outcome of, and its weight is
     * what decided some of them. A nought-weight bloc was never in it.
     *
     * @return true when the bloc's summed dominance weight is nought
     */
    @Override
    public boolean isPaintingNothing() {
        return score == 0;
    }

    /**
     * Folds one system the bloc lives in into these stats: a present-system entry always, a
     * domination count only when the bloc wins that system.
     *
     * @param isDominant       whether the bloc is this system's dominant holder, which adds one to
     *                         domination; a present-but-not-dominant bloc adds to presence alone
     * @param systemScore      the bloc's combined dominance weight in this system, added to score;
     *                         nought for a bloc the contest weighed nothing of here
     * @param systemMarketSize the summed raw colony size of the bloc's colonies in this system,
     *                         added to market size
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
