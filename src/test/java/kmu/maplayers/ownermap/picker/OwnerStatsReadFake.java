package kmu.maplayers.ownermap.picker;

import java.util.Map;

/**
 * Test fixture: an owner walk's read over any metrics at all.
 *
 * <p>The shipped reads are each fixed to one mechanic's numbers, so neither can stand in for a case
 * about what the shared assembly does with metrics it has never heard of. This one is open on its
 * payload, which is what lets such a case be written at all.
 *
 * @param <S>            the metrics the case ranks by
 * @param statsByOwnerId each owner's totals, in the order the case wants them walked
 * @param presenceIndex  the systems behind those totals
 */
public record OwnerStatsReadFake<S extends OwnerMetrics>(
    Map<String, S> statsByOwnerId,
    OwnerPresenceIndex presenceIndex)
        implements OwnerStatsRead<S> {

    /**
     * A read carrying totals and no presence, for a case about the rows alone.
     *
     * @param <S>            the metrics the case ranks by
     * @param statsByOwnerId each owner's totals, in the order the case wants them walked
     * @return that read, its presence empty
     */
    public static <S extends OwnerMetrics> OwnerStatsReadFake<S> createRowsOnlyFake(
            Map<String, S> statsByOwnerId) {

        return new OwnerStatsReadFake<>(statsByOwnerId, OwnerPresenceIndex.EMPTY);
    }
}
