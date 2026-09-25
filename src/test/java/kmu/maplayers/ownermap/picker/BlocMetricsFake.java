package kmu.maplayers.ownermap.picker;

/**
 * A bloc's numbers as a tier case states them, with no mechanic behind any of them.
 *
 * <p>Everything the tier does with metrics is generic - a picker ranks them, a row draws them, a
 * vocabulary declares which to sort by - so a case about any of that wants a metrics value and
 * nothing more. Reaching for a mechanic's own stats would make a tier suite name the very layer
 * the tier must not.
 *
 * <p>Carries both capabilities a tier case can ask for: a size, and whether the bloc paints
 * anything. A case about neither states whatever reads clearly and ignores it.
 *
 * @param lead       the number this vocabulary leads with, and what deciding it paints nothing
 *                   reads off
 * @param presence   a second reading a layer might rank by
 * @param score      a third, so a tie can fall past more than one
 * @param marketSize the bloc's summed colony size, which every vocabulary carries
 */
public record BlocMetricsFake(
    int lead,
    int presence,
    int score,
    int marketSize) implements
        PaintingBlocMetrics,
        SizedBlocMetrics {

    /** A bloc with nothing to its name, which is what an empty fold yields. */
    public static final BlocMetricsFake EMPTY_FAKE = new BlocMetricsFake(0, 0, 0, 0);

    /**
     * Metrics carrying one ranked number, every other reading following it.
     *
     * @param score the number a case ranks by
     * @return the metrics
     */
    public static BlocMetricsFake scoring(int score) {
        return new BlocMetricsFake(score, score, score, score);
    }

    @Override
    public boolean isPaintingNothing() {
        return lead == 0;
    }
}
