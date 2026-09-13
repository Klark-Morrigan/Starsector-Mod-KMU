package kmu.maplayers.politicalmap.base.politics;

import java.util.Map;

/**
 * What one whole-sector dominance walk yields: the totals the filter picker sorts and labels each
 * present bloc by, and the systems each of those blocs was found living in. Why the two travel
 * together is {@link BlocStatsRead}'s.
 *
 * <p>The index is carried beside {@link DominanceStats} rather than folded into it. Those stats are
 * four plain numbers a picker row sorts and displays on, and a collection among them would be
 * sorted by nothing and summed by nothing.
 *
 * @param statsByBlocId each present bloc's whole-sector totals, keyed by bloc ID in walk order
 * @param presenceIndex the systems each of those blocs lives in, over the same key set
 */
public record DominanceStatsRead(
    Map<String, DominanceStats> statsByBlocId,
    BlocPresenceIndex presenceIndex) implements BlocStatsRead<DominanceStats> {

    /** A walk that found nobody living anywhere the player can see. */
    public static final DominanceStatsRead EMPTY
        = new DominanceStatsRead(Map.of(), BlocPresenceIndex.EMPTY);
}
