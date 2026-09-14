package kmu.maplayers.politicalmap.base.politics;

import java.util.Map;

/**
 * What one whole-sector claim walk yields: the totals the claims picker sorts and labels each bloc
 * by, and the systems each of those blocs claims. Why the two travel together is
 * {@link BlocStatsRead}'s.
 *
 * <p>The claims counterpart to {@link DominanceStatsRead}, and a separate record for the same reason
 * the two stats records are separate: the claims vocabulary has no presence to name and the
 * dominance one has no claim, so neither read may be widened by the other's answers.
 *
 * <p>Its presence is what this walk's claim arm recorded and nothing else. A bloc's colonies are
 * summed from wherever they are, so letting the habitation arm beside it contribute would name
 * systems this layer draws the bloc nothing in.
 *
 * @param statsByBlocId each claiming or living bloc's whole-sector totals, keyed by bloc ID in walk
 *                      order
 * @param presenceIndex the systems each bloc claims - never a wider key set than the stats beside
 *                      it, a bloc listed for its colonies alone claiming nowhere
 */
public record ClaimStatsRead(
    Map<String, ClaimStats> statsByBlocId,
    BlocPresenceIndex presenceIndex) implements BlocStatsRead<ClaimStats> {
}
