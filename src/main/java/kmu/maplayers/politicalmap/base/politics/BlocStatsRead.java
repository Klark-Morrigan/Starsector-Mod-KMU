package kmu.maplayers.politicalmap.base.politics;

import kmu.maplayers.politicalmap.base.BlocMetrics;

import java.util.Map;

/**
 * What any whole-sector bloc walk yields: each bloc's totals, and the systems behind them.
 *
 * <p>The pair travels together because it is one reading of the sector under one set of knobs.
 * Answered through two entry points it would walk the economy twice and let a surface hold totals
 * from one reading beside a system set from another - two answers to "where is this bloc" for a
 * picker row and the cells lit for it. That is the reason each implementing walk carries both, and
 * the reason no caller is offered a way to ask for one alone.
 *
 * <p>Declared once here so the assembly above takes a single argument rather than a map and an index
 * it would have to trust came from the same walk. What the pair <em>means</em> stays each walk's own
 * - living somewhere under the dominance walk, claiming it under the claims one - which is why this
 * names neither.
 *
 * @param <S> the metrics that walk totals, ranked by the vocabulary the calling view pairs with them
 */
public interface BlocStatsRead<S extends BlocMetrics> {

    /**
     * The systems each bloc the walk surfaced was found in, over no wider a key set than the totals.
     *
     * @return the presence behind the totals; empty for a walk that surfaced nobody
     */
    BlocPresenceIndex presenceIndex();

    /**
     * @return each surfaced bloc's whole-sector totals, keyed by bloc ID in walk order
     */
    Map<String, S> statsByBlocId();
}
