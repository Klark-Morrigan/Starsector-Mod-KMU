package kmu.maplayers.politicalmap.base.politics;

import kmu.maplayers.politicalmap.base.BlocMetrics;

import java.util.Map;

/**
 * Test fixture: a bloc walk's read over any metrics at all.
 *
 * <p>The shipped reads are each fixed to one mechanic's numbers, so neither can stand in for a case
 * about what the shared assembly does with metrics it has never heard of. This one is open on its
 * payload, which is what lets such a case be written at all.
 *
 * @param <S>           the metrics the case ranks by
 * @param statsByBlocId each bloc's totals, in the order the case wants them walked
 * @param presenceIndex the systems behind those totals
 */
public record BlocStatsReadFake<S extends BlocMetrics>(
    Map<String, S> statsByBlocId,
    BlocPresenceIndex presenceIndex) implements BlocStatsRead<S> {

    /**
     * A read carrying totals and no presence, for a case about the rows alone.
     *
     * @param <S>           the metrics the case ranks by
     * @param statsByBlocId each bloc's totals, in the order the case wants them walked
     * @return that read, its presence empty
     */
    public static <S extends BlocMetrics> BlocStatsReadFake<S> createRowsOnlyFake(
            Map<String, S> statsByBlocId) {

        return new BlocStatsReadFake<>(statsByBlocId, BlocPresenceIndex.EMPTY);
    }
}
