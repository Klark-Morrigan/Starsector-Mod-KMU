package kmu.maplayers.ownermap.picker;

import java.util.Map;

/**
 * What any whole-sector owner walk yields: each owner's totals, and the systems behind them.
 *
 * <p>The pair travels together because it is one reading of the sector under one set of knobs.
 * Answered through two entry points it would walk the economy twice and let a surface hold totals
 * from one reading beside a system set from another - two answers to "where is this owner" for a
 * picker row and the cells lit for it. That is the reason each walk yields both, and the reason no
 * caller is offered a way to ask for one alone.
 *
 * <p>One record for every walk, the metrics being the only part that differs. What the pair
 * <em>means</em> stays each walk's own, which is why this names no layer. A walk's own metrics record
 * keeps the vocabularies apart: a number one layer has no use for cannot reach its read through
 * another layer's answers.
 *
 * <p>The totals are carried beside the index rather than folded into it. The totals are plain
 * numbers a picker row sorts and displays on, and a collection among them would be sorted by nothing
 * and summed by nothing.
 *
 * @param statsByOwnerId each surfaced owner's whole-sector totals, keyed by owner ID in walk order
 * @param presenceIndex  the systems each owner the walk surfaced was found in, over no wider a key set
 *                       than the totals; empty for a walk that surfaced nobody
 * @param <S>            the metrics that walk totals, ranked by the vocabulary the calling view pairs
 *                       with them
 */
public record OwnerStatsRead<S extends OwnerMetrics>(
    Map<String, S> statsByOwnerId,
    OwnerPresenceIndex presenceIndex) {

    /**
     * A walk that surfaced nobody.
     *
     * @param <S> the metrics the walk would have totalled
     * @return the empty read
     */
    public static <S extends OwnerMetrics> OwnerStatsRead<S> createEmptyRead() {
        return new OwnerStatsRead<>(Map.of(), OwnerPresenceIndex.EMPTY);
    }
}
